package com.platypus.android.server;

import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A logging class that creates vehicle logs in JSON format.
 *
 * This allows the creation of easily parsable and log messages and handles storing the log in a
 * standard location from which it can be downloaded.
 *
 * Example:
 * <pre>
 *     try {
 *         JSONObject entry = new JSONObject()
 *             .put("gain", new JSONObject()
 *                 .put("axis", axis)
 *                 .put("values", gains));
 *         logger.info(entry);
 *     } catch (JSONException e) {
 *         Log.w(TAG, "Failed to write gain to log file.");
 *     }
 * </pre>
 */
public class VehicleLogger {
    /**
     * Tag used for Android log records.
     */
    private static final String TAG = VehicleService.class.getSimpleName();

    /**
     * Internal log appender that manages the output to a log file.
     */
    private PrintWriter mLogWriter;

    /**
     * Internal timestamp of when log was created.
     */
    private long mStartTime;

    /**
     * The default prefix for Platypus Vehicle data log files.
     */
    private static final String DEFAULT_LOG_PREFIX = "platypus_";

    /**
     * Constructs a default filename from the current date and time.
     *
     * @return the default filename for the current time.
     */
    private static String defaultFilename() {
        Date d = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_hhmmss");
        return DEFAULT_LOG_PREFIX + sdf.format(d) + ".txt";
    }

    /**
     * Logging levels supported by the vehicle logger.
     */
    public enum Level {
        /**
         * Debugging information that normal users do not need to see.
         */
        DEBUG("D"),
        /**
         * Information about normal operation.
         */
        INFO("I"),
        /**
         * Notification of an abnormal event that is recoverable.
         */
        WARN("W"),
        /**
         * Notification of an abnormal event that may put the system into an invalid state.
         */
        ERROR("E"),
        /**
         * Notification of an abnormal event that the system cannot recover from.
         */
        FATAL("F");

        private final String mCode;
        Level(final String code) { mCode = code; }

        /**
         * Returns a short string code for each log level.
         */
        public String code() { return mCode; }
    }

    /**
     * Create a new vehicle log file.
     */
    public VehicleLogger() {
        // Construct the path to the new log file.
        File logDirectory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "platypus");
        File logFile = new File(logDirectory, defaultFilename());

        // Set up a writer for the vehicle log file.
        try {
            logDirectory.mkdirs();
            logFile.createNewFile();
            mLogWriter = new PrintWriter(logFile);
            mStartTime = System.currentTimeMillis();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file: " + logFile, e);
            return;
        }

        // Whenever a log is created, add a date/time message to the log.
        try {
            log(Level.INFO, new JSONObject()
                    .put("date", new Date())
                    .put("time", System.currentTimeMillis()));
        }  catch (JSONException e) {
            Log.e(TAG, "Failed to serialize time.", e);
            return;
        }
    }

    public synchronized void close() {
        // Remove the data log (a new one will be created on restart)
        if (mLogWriter != null) {
            mLogWriter.close();
            mLogWriter = null;
        }
    }

    /**
     * Creates a log entry from the specified JSON object.
     * @param obj
     */
    public synchronized void log(Level level, JSONObject obj) {
        if (mLogWriter == null)
            return;

        StringBuffer sb = new StringBuffer();
        sb.append(System.currentTimeMillis() - mStartTime);
        sb.append("\t");
        sb.append(level.code());
        sb.append("\t");
        sb.append(obj.toString());

        mLogWriter.println(sb.toString());
    }

    public synchronized void debug(JSONObject obj) {
        log(Level.DEBUG, obj);
    }

    public synchronized void error(JSONObject obj) {
        log(Level.ERROR, obj);
    }

    public synchronized void fatal(JSONObject obj) {
        log(Level.FATAL, obj);
    }

    public synchronized void info(JSONObject obj) {
        log(Level.INFO, obj);
    }

    public synchronized void warn(JSONObject obj) {
        log(Level.WARN, obj);
    }
}
