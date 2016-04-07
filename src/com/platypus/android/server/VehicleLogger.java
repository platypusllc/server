package com.platypus.android.server;

import android.util.Log;

import com.google.code.microlog4android.Level;
import com.google.code.microlog4android.LoggerFactory;
import com.google.code.microlog4android.appender.FileAppender;
import com.google.code.microlog4android.format.PatternFormatter;

import org.json.JSONObject;

import java.io.IOException;
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
     * Internal logger object used to add entries to the log.
     */
    private static final com.google.code.microlog4android.Logger mLogger =
            LoggerFactory.getLogger();

    /**
     * Internal log appender that manages the output to a log file.
     */
    private FileAppender mFileAppender;

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
     * Create a new vehicle log file.
     */
    public VehicleLogger() {
        // Set up logging format to include time, tag, and value.
        PatternFormatter formatter = new PatternFormatter();
        formatter.setPattern("%r %d %m %T");

        // Set up an output stream for the vehicle log file.
        String logFilename = defaultFilename();
        mFileAppender = new FileAppender();
        mFileAppender.setFileName(logFilename);
        mFileAppender.setAppend(true);
        mFileAppender.setFormatter(formatter);
        try {
            mFileAppender.open();
        } catch (IOException e) {
            Log.w(TAG, "Failed to open data log file: " + logFilename, e);
        }
        mLogger.addAppender(mFileAppender);
    }

    public synchronized void close() {
        // Remove the data log (a new one will be created on restart)
        if (mFileAppender != null) {
            try {
                mFileAppender.close();
                mFileAppender = null;
            } catch (IOException e) {
                Log.e(TAG, "Data log shutdown error", e);
            }
        }
    }

    /**
     * Creates a log entry from the specified JSON object.
     * @param obj
     */
    public synchronized void log(Level level, JSONObject obj) {
        mLogger.log(level, obj.toString());
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
