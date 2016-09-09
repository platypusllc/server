package com.platypus.android.server;

import android.app.Notification;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

/**
 * Iterates through the logs in the log directory, and uploads any logs that are not already
 * stored in Firebase storage.
 */
public class LogUploadService extends JobService {
    protected static final int SYNC_IMMEDIATE_JOB_ID = 0x34;
    protected static final int SYNC_PERIODIC_JOB_ID = 0x35;


    // Manually construct the TAG because we need to pass it into super().
    private static final String TAG = "LogUploadService";

    /**
     * Filter that matches log files generated by VehicleLogger.
     */
    private static final FilenameFilter LOG_FILENAME_FILTER = new FilenameFilter() {
        final Pattern LOG_PATTERN = Pattern.compile("platypus_.*.txt");

        @Override
        public boolean accept(File dir, String filename) {
            return LOG_PATTERN.matcher(filename).matches();
        }
    };
    // Reference to ongoing uploading task.
    protected AsyncTask<File, Integer, Boolean> mUploadTask;

    /**
     * Compute the MD5 hash of a file as a Base64 string.
     *
     * @param file the file whose hash is computed
     * @return a base64 encoded MD5 hash for the file
     */
    public static String computeMD5Hash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            InputStream is = new FileInputStream(file);
            byte[] buffer = new byte[8192];

            // Read contents of the file.
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }

            // Turn MD5 sum into a standardized 32 character hash string.
            byte[] md5sum = digest.digest();
            return Base64.encodeToString(md5sum, Base64.NO_WRAP);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to compute MD5 hash: file not found.");
            return null;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to compute MD5 hash: algorithm unavailable.");
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Unable to compute MD5 hash: error accessing file.");
            return null;
        }
    }

    /**
     * Schedules an upload of logs to be executed as soon as possible.
     *
     * @param context the context from which to schedule the run
     */
    static void runSyncNow(Context context) {
        // Create a job that attempts to upload files immediately.
        final JobInfo job = new JobInfo.Builder(SYNC_IMMEDIATE_JOB_ID,
                new ComponentName(context, LogUploadService.class))
                .setOverrideDeadline(200)
                .build();

        // Schedule this job for execution.
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    /**
     * Configures the automatic upload of logs based on the current SharedPreference.
     *
     * @param context the context from which to schedule the run
     */
    static void updateAutoSync(Context context) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.getBoolean("pref_cloud_autosync_enable", false)) {
            // Create a job that attempts to upload files periodically.
            final JobInfo job = new JobInfo.Builder(SYNC_PERIODIC_JOB_ID,
                    new ComponentName(context, LogUploadService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresCharging(true)
                    .setRequiresDeviceIdle(true)
                    .setPeriodic(86400000) // Run about once every day.
                    .setPersisted(true)
                    .build();

            // Schedule this job for execution.
            jobScheduler.schedule(job);
            Log.i(TAG, "Auto-synchronization of logs enabled.");
        } else {
            // Clear this job from execution.
            jobScheduler.cancel(SYNC_PERIODIC_JOB_ID);
            Log.i(TAG, "Auto-synchronization of logs disabled.");
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        // Create a reference to the appropriate storage location for log files for this vehicle.
        final StorageReference logsRef = FirebaseStorage.getInstance()
                .getReferenceFromUrl("gs://platypus-cloud-api.appspot.com")
                .child("logs")
                .child(Build.SERIAL);

        // Log in with an authenticated user and scan and upload all log files.
        FirebaseUtils.firebaseSignin(this,
                new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        // Retrieve a list of all log files currently on the system.
                        File logDirectory = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOCUMENTS), "platypus");
                        File[] logFiles = logDirectory.listFiles(LOG_FILENAME_FILTER);

                        // If we fail to find any logfiles, just make an empty list.
                        if (logFiles == null)
                            logFiles = new File[0];

                        // Start a task to upload these files.
                        mUploadTask = new UploadLogsTask(logsRef, params).execute(logFiles);
                    }
                },
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Even though we did not complete, the authentication error will not go
                        // away on its own, so tell Android not to reschedule this job.
                        jobFinished(params, false);
                    }
                }
        );

        // Indicate that there is ongoing work that may need to be canceled.
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Cancelling returns false if unable to cancel or if the task already, which are
        // the same conditions under which the task does _not_ need to be rescheduled.
        Log.w(TAG, "Log upload job was cancelled.");
        return mUploadTask.cancel(false);
    }

    /**
     * Implements a background task that iterates through each provided file and checks that they
     * are already uploaded to the cloud, or uploads them when necessary.
     */
    class UploadLogsTask extends AsyncTask<File, Integer, Boolean> {

        static final int notificationId = 1;
        final StorageReference mLogsRef;
        final JobParameters mJobParams;

        public UploadLogsTask(StorageReference logsRef, JobParameters jobParams) {
            mLogsRef = logsRef;
            mJobParams = jobParams;
        }

        @Override
        protected void onPreExecute() {
            Log.i(TAG, "Log uploading started.");

            // Create a notification that keeps this service in the foreground.
            startForeground(notificationId,
                    new Notification.Builder(LogUploadService.this)
                            .setContentTitle("Synchronizing logs")
                            .setContentText("Scanning for changes...")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setOngoing(true)
                            .build()
            );
        }


        @Override
        protected void onPostExecute(Boolean result) {
            Log.i(TAG, "Log uploading completed.");

            // Remove this service from the foreground when the task is complete.
            stopForeground(true);

            // Indicate to the system that this job completed normally.
            jobFinished(mJobParams, false);
        }

        @Override
        protected void onCancelled(Boolean result) {
            Log.w(TAG, "Log uploading was cancelled.");

            // Remove this service from the foreground when the task is canceled.
            stopForeground(true);
        }

        @Override
        protected Boolean doInBackground(final File... logFiles) {
            // Iterate through each file, checking if the file has valid metadata in the cloud
            // and starting an upload if it does not.
            for (File f : logFiles) {
                final File logFile = f;
                final Uri logFileUri = Uri.fromFile(logFile);

                // Retrieve metadata to determine if this file already exists on the server.
                final CountDownLatch metadataDone = new CountDownLatch(1);
                final Task<StorageMetadata> metadataTask = mLogsRef.child(logFile.getName())
                        .getMetadata()
                        .addOnCompleteListener(new OnCompleteListener<StorageMetadata>() {
                            @Override
                            public void onComplete(@NonNull Task<StorageMetadata> task) {
                                metadataDone.countDown();
                            }
                        });

                // Check if the file already exists (metadata exists and file size matches).
                try {
                    metadataDone.await();
                    if (metadataTask.isSuccessful()) {
                        final String cloudHash = metadataTask.getResult().getMd5Hash();
                        final String logHash = computeMD5Hash(logFile);
                        if (cloudHash != null && cloudHash.equalsIgnoreCase(logHash)) {
                            Log.d(TAG, "Log " + logFile.getName() + " already uploaded.");
                            continue;
                        }
                    }
                } catch (InterruptedException e) {
                    return false;
                }

                // Create the file metadata
                StorageMetadata metadata = new StorageMetadata.Builder()
                        .setContentType("text/plain")
                        .build();

                // Upload any file that has a size mismatch or does not exist.
                final CountDownLatch uploadDone = new CountDownLatch(1);
                final StorageTask uploadTask = mLogsRef.child(logFileUri.getLastPathSegment())
                        .putFile(logFileUri, metadata)
                        .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                                int progress = (int) ((100 * taskSnapshot.getBytesTransferred()) /
                                        taskSnapshot.getTotalByteCount());
                                startForeground(notificationId,
                                        new Notification.Builder(LogUploadService.this)
                                                .setProgress(100, progress, false)
                                                .setContentTitle("Synchronizing to cloud.")
                                                .setContentText(logFile.getName() + " - " + progress + "%")
                                                .setSmallIcon(R.drawable.ic_notification)
                                                .setOngoing(true)
                                                .build()
                                );
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                Log.w(TAG, "Failed to upload " + logFile.getName() + " to cloud.");
                                uploadDone.countDown();
                            }
                        })
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                Log.i(TAG, "Uploaded " + logFile.getName() + " to cloud.");
                                uploadDone.countDown();

                                // If sync deletion is enabled, remove local copy of log file.
                                boolean deleteAfterSync = PreferenceManager
                                        .getDefaultSharedPreferences(LogUploadService.this)
                                        .getBoolean("pref_cloud_sync_delete", false);
                                if (deleteAfterSync) {
                                    if (logFile.delete()) {
                                        Log.i(TAG, "Removed local copy of " + logFile.getName());
                                    } else {
                                        Log.e(TAG, "Unable to remove local copy of " + logFile.getName());
                                    }
                                }
                            }
                        });

                // Wait for the file to finish uploading.
                try {
                    uploadDone.await();
                    if (!uploadTask.isSuccessful()) {
                        Log.w(TAG, "Error while uploading " + logFile.getName() + ".");
                        return false;
                    }
                } catch (InterruptedException e) {
                    uploadTask.cancel();
                    return false;
                }
            }
            return true;
        }
    }
}
