package com.platypus.android.server;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;

/**
 * Iterates through the logs in the log directory, and uploads any logs that are not already
 * stored in Firebase storage.
 */
public class LogUploadService extends IntentService {
    // Manually construct the TAG because we need to pass it into super().
    private static final String TAG = "LogUploadService";
    public static final String ACTION_UPLOAD_LOG =
            "com.platypus.android.server.UPLOAD_LOG";
    public static final String ACTION_SCAN_AND_UPLOAD_LOGS =
            "com.platypus.android.server.SCAN_AND_UPLOAD_LOGS";
    public static final String PARAM_FILENAME = "filename";

    private static final FilenameFilter LOG_FILENAME_FILTER = new FilenameFilter() {
        final Pattern LOG_PATTERN = Pattern.compile("platypus_.*.txt");

        @Override
        public boolean accept(File dir, String filename) {
            return LOG_PATTERN.matcher(filename).matches();
        }
    };

    protected StorageReference mLogsRef;

    public LogUploadService() {
        super(TAG);

        // If the system shuts down the service, the Intent is not redelivered
        // and therefore the Service won't start again.
        setIntentRedelivery(false);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Create a reference to the appropriate storage location for log files for this vehicle.
        String instanceToken = FirebaseInstanceId.getInstance().getToken();
        if (instanceToken != null) {
            mLogsRef = FirebaseStorage.getInstance()
                    .getReferenceFromUrl("gs://platypus-cloud-api-9f679.appspot.com")
                    .child("logs")
                    .child(instanceToken);
        } else {
            Log.w(TAG, "Unable to connect to Firebase storage: missing instance ID.");
        }
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        // Exit if the storage reference was not already created.
        if (mLogsRef == null) {
            Log.w(TAG, "Unable to perform log upload: missing instance ID.");
            return;
        }

        // Log in with an anonymous user.
        FirebaseAuth.getInstance().signInAnonymously()
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        // Either upload a single file or begin scanning all files.
                        String action = intent.getAction();
                        if(action.equals(ACTION_UPLOAD_LOG)) {
                            String filename = intent.getStringExtra(PARAM_FILENAME);
                            uploadLog(filename, 1);
                        } else if(action.equals(ACTION_SCAN_AND_UPLOAD_LOGS)) {
                            Log.i(TAG, "Scanning and uploading logs to cloud.");
                            scanAndUploadLogs();
                        }
                    }
                });
    }

    /**
     * Uploads a single log file to Firebase.
     *
     * This method will <b>not</b> check if the log file already exists, if it does, Firebase may
     * report an error when the upload starts.
     *
     * @param filename the path to the log file that should be uploaded.
     */
    private void uploadLog(final String filename, final int notificationId) {
        // File or Blob
        final File file = new File(filename);
        final Uri fileUri = Uri.fromFile(file);

        // Reference to system notification service.
        String ns = Context.NOTIFICATION_SERVICE;
        final NotificationManager notificationManager =
                (NotificationManager)LogUploadService.this.getSystemService(ns);

        // Create the file metadata
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("text/plain")
                .build();

        // Upload file and metadata to the storage reference for this vehicle.
        UploadTask uploadTask = mLogsRef.child(fileUri.getLastPathSegment())
                .putFile(fileUri, metadata);

        // Listen for state changes, errors, and completion of the upload.
        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                int progress = (int)((100 * taskSnapshot.getBytesTransferred()) /
                        taskSnapshot.getTotalByteCount());
                startForeground(notificationId,
                        new Notification.Builder(LogUploadService.this)
                                .setProgress(100, progress, false)
                                .setContentTitle(fileUri.getLastPathSegment())
                                .setContentText(progress + "% uploaded...")
                                .setSmallIcon(R.drawable.ic_notification)
                                .setOngoing(true)
                                .build()
                );
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.w(TAG, "Failed to upload " + file.getName() + " to cloud.");
                notificationManager.cancel(notificationId);
                stopForeground(false);
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // If sync deletion is enabled, remove local copy of log file.
                boolean deleteAfterSync =
                        PreferenceManager.getDefaultSharedPreferences(LogUploadService.this)
                                .getBoolean("pref_cloud_sync_delete", false);
                if (deleteAfterSync) {
                    Log.i(TAG, "Removing local copy of " + file.getName());
                    file.delete();
                }

                Log.i(TAG, "Uploaded " + file.getName() + " to cloud.");
                notificationManager.cancel(notificationId);
                stopForeground(false);
            }
        });
    }

    /**
     * Scans through all files on this system, and only uploads logs that have not already been
     * uploaded.
     */
    private void scanAndUploadLogs() {
        // Retrieve a list of all log files currently on the system.
        File logDirectory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "platypus");
        File[] logFiles = logDirectory.listFiles(LOG_FILENAME_FILTER);

        // Iterate through all of the logs in the log directory, initiate uploads on any logs that
        // do not already exist in the Firebase storage container.
        for (int i = 0; i < logFiles.length; ++i) {
            final File logFile = logFiles[i];
            final int notificationId = i + 1;
            mLogsRef.child(logFile.getName()).getMetadata()
                    .addOnSuccessListener(new OnSuccessListener<StorageMetadata>() {
                        @Override
                        public void onSuccess(StorageMetadata storageMetadata) {
                            Log.i(TAG, "Found existing log: " + logFile.getName());
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            uploadLog(logFile.getAbsolutePath(), notificationId);
                        }
                    });
        }
    }
}
