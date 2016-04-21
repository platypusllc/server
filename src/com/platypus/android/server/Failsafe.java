package com.platypus.android.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.platypus.crw.AsyncVehicleServer;
import com.platypus.crw.FunctionObserver;
import com.platypus.crw.VehicleServer;
import com.platypus.crw.WaypointListener;
import com.platypus.crw.data.UtmPose;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implements a failsafe behavior that does the following:
 *
 * - Every five seconds, query the failsafe address for connectivity
 * - Record the current pose if the boat is within connectivity
 * - If it has been FAILSAFE_TIMEOUT since the failsafe address was contacted,
 *   then enable autonomy and head to the last position where there was connectivity.
 * - If the last position with connectivity was reached, reset the timeout counter and wait.
 * - If it has been FAILSAFE_TIMEOUT since the failsafe address was contacted,
 *   then enable autonomy to head to the home position, if it was specified.
 */
public class Failsafe {
    private static final String TAG = VehicleService.class.getSimpleName();
    static final String ACTION_FAILSAFE_UPDATE = "com.platypus.android.server.action.FAILSAFE_UPDATE";

    /**
     * Time between periodic connectivity checks.
     */
    static int UPDATE_PERIOD_MS = 5000;

    /**
     * Maximum time until a connectivity check will be considered a failure.
     */
    static int UPDATE_TIMEOUT_MS = 1000;

    /**
     * The current status of the failsafe behavior.
     */
    enum FailsafeStatus {
        CONNECTED,
        FAILSAFE_TO_LAST_LOCATION,
        FAILSAFE_TO_HOME_LOCATION
    }

    /**
     * Failsafe server to which to test connectivity.
     * Updated from SharedPreferences.
     */
    InetAddress mFailsafeServer;

    /**
     * Maximum allowable loss of connectivity to failsafe server before engaging failsafe.
     * Updated from SharedPreferences.
     */
    int mTimeoutMs = 0;

    /**
     * Pose at which the vehicle was last connected to the failsafe server.
     */
    UtmPose mLastConnectedLocation;

    /**
     * Home location to which the vehicle will navigate if last-known location still fails.
     */
    UtmPose mHomeLocation;

    /**
     * Time at which the vehicle was last connected to the failsafe server.
     */
    long mLastConnectedTime;

    /**
     * Current state of the failsafe recovery behavior.
     */
    FailsafeStatus mStatus;

    /**
     * Vehicle on which to engage the failsafe behavior.
     */
    final VehicleServer mVehicleServer;

    /**
     * Application context within which this behavior is being run.
     */
    final Context mContext;

    /**
     * Lock used to synchronize changes in failsafe server settings with updates.
     */
    final Object mUpdateLock = new Object();

    /**
     * Future for a running update task.
     */
    ScheduledFuture mUpdateFuture;

    /**
     * Executor that periodically runs connectivity checks.
     */
    final ScheduledThreadPoolExecutor mUpdateExecutor = new ScheduledThreadPoolExecutor(1);

    public Failsafe(final Context context, final VehicleServer server) {
        mContext = context;
        mVehicleServer = server;

        // Register a shared preference listener to listen for settings updates and
        // initialize based on existing shared preferences settings.
        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(mContext);
        sharedPreferences.registerOnSharedPreferenceChangeListener(mPreferenceListener);
        updatePreferences(sharedPreferences);
    }

    /**
     * Permanently shuts down the failsafe behavior.
     * After this function is called, this instance is no longer valid.
     */
    public void shutdown() {
        // Unregister the shared preference listener.
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .unregisterOnSharedPreferenceChangeListener(mPreferenceListener);

        // Shutdown the update task if it is running.
        synchronized (mUpdateLock) {
            if (mUpdateFuture != null) {
                try {
                    mUpdateFuture.cancel(false);
                    mUpdateFuture.get();
                } catch (InterruptedException e) {
                    // Do nothing.
                } catch (CancellationException e) {
                    // Do nothing.
                } catch (ExecutionException e) {
                    // Do nothing.
                }
            }
        }
    }

    /**
     * Run an update loop which tests connectivity and takes appropriate failsafe measures.
     */
    Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            // Run a connectivity test.
            checkConnection();

            // Test if connectivity is sufficiently recent.
            boolean isRecentlyConnected;
            synchronized (mUpdateLock) {
                isRecentlyConnected = (System.currentTimeMillis() - mLastConnectedTime > mTimeoutMs);
            }
            sendFailsafeIntent(isRecentlyConnected);

            switch (mStatus) {
                case CONNECTED:
                    // If we were recently connected, just stay connected.
                    if (isRecentlyConnected)
                        return;

                    // If not recently connected, initiate failsafe to last known location.
                    synchronized (mUpdateLock) {
                        mStatus = FailsafeStatus.FAILSAFE_TO_HOME_LOCATION;
                        Log.w(TAG,
                                "Connectivity loss detected. " +
                                "Will failsafe to last-known location if IDLE.");
                    }
                    break;

                case FAILSAFE_TO_LAST_LOCATION:
                    // If we were recently connected, return to connected state.
                    // (Note that this will NOT stop the ongoing navigation command.  That is left up
                    //  to the user to override when connectivity is re-established.)
                    if (isRecentlyConnected) {
                        synchronized (mUpdateLock) {
                            mStatus = FailsafeStatus.CONNECTED;
                        }
                        Log.i(TAG,
                                "Failsafe to last-known location disengaged, " +
                                "connectivity re-established.");
                        return;
                    }

                    // Check if we are already navigating to the last-known location.
                    // If not, start navigating using the default controller.
                    synchronized (mUpdateLock) {
                        checkFailsafeBehavior(mLastConnectedLocation);

                        // Since we have already attempted to get to the last location,
                        // transition to using the home location the next time we need to
                        // engage a failsafe behavior.
                        mStatus = FailsafeStatus.FAILSAFE_TO_HOME_LOCATION;
                    }
                    break;

                case FAILSAFE_TO_HOME_LOCATION:
                    // If we were recently connected, return to connected state.
                    // (Note that this will NOT stop the ongoing navigation command.  That is left up
                    //  to the user to override when connectivity is re-established.)
                    if (isRecentlyConnected) {
                        synchronized (mUpdateLock) {
                            mStatus = FailsafeStatus.CONNECTED;
                        }

                        Log.i(TAG,
                                "Failsafe to home location disengaged, " +
                                "connectivity established.");
                        return;
                    }

                    // Check if we are already navigating to the home location.
                    // If not, start navigating using the default controller.
                    synchronized (mUpdateLock) {
                        checkFailsafeBehavior(mHomeLocation);
                    }
                    break;
            }
        }
    };

    /**
     * Tests connection to the failsafe server and updates the last connected location and time.
     *
     * @return the success of the connectivity test
     */
    boolean checkConnection() {
        try {
            // If the server is not reachable, do nothing.
            synchronized (mUpdateLock) {
                if (!mFailsafeServer.isReachable(UPDATE_TIMEOUT_MS))
                    return false;

                // If the server is reached, update the latest pose and time.
                mLastConnectedTime = System.currentTimeMillis();
                mLastConnectedLocation = mVehicleServer.getPose();
                return true;
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to test connectivity.", e);
            return false;
        }
    }

    /**
     * Sends an android Intent that notifies listeners that the state of the failsafe has changed.
     *
     * @param isRunning whether the failsafe is currently engaged or not.
     */
    void sendFailsafeIntent(boolean isRunning) {
        Intent intent = new Intent(ACTION_FAILSAFE_UPDATE);
        intent.putExtra("isRunning", isRunning);
        mContext.sendBroadcast(intent);
    }

    /**
     * Check if we are already navigating. If not, start navigating using the default controller.
     *
     * This will begin navigation to the failsafe, but only if the boat does not have any active
     * autonomous behavior.
     *
     * @param failsafeDestination the failsafe destination to verify navigation towards
     */
    void checkFailsafeBehavior(final UtmPose failsafeDestination) {
        // Do not engage failsafe if the vehicle is already doing something.
        switch(mVehicleServer.getWaypointStatus()) {
            case GOING:
            case PAUSED:
                return;
        }

        // Check if already navigating to the desired destination.
        UtmPose[] waypoints = mVehicleServer.getWaypoints();
        if (waypoints[0].equals(failsafeDestination))
            return;

        // Start navigating to the failsafe destination.
        mVehicleServer.startWaypoints(new UtmPose[]{failsafeDestination}, null);
        mVehicleServer.setAutonomous(true);
        Log.w(TAG, "Engaged failsafe behavior to: " + failsafeDestination);
    }

    /**
     * Updates all member variables from shared preference settings.
     */
    void updatePreferences(final SharedPreferences sharedPreferences) {
        synchronized (mUpdateLock) {

            // Update the timeout used for the failsafe server (stored as a numeric string).
            mTimeoutMs = Integer.parseInt(
                    sharedPreferences.getString("pref_failsafe_timeout",
                        mContext.getString(R.string.pref_failsafe_timeout_default)));

            // Update the hostname used for the failsafe server.
            String failsafeHostname =
                    sharedPreferences.getString("pref_failsafe_addr", "localhost");
            try {
                mFailsafeServer = InetAddress.getByName(failsafeHostname);
            } catch (UnknownHostException e) {
                Log.w(TAG, "Unable to resolve failsafe server: " +
                        failsafeHostname);
            }

            // Enable/disable update task.
            if (sharedPreferences.getBoolean("pref_failsafe_enable", false)) {
                if (mUpdateFuture == null) {
                    mUpdateFuture = mUpdateExecutor.scheduleAtFixedRate(
                            mUpdateRunnable, 0,
                            UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS);
                    Log.i(TAG, "Failsafe service enabled.");
                }
            } else {
                if (mUpdateFuture != null) {
                    try {
                        mUpdateFuture.cancel(false);
                        mUpdateFuture.get();
                    } catch (InterruptedException e) {
                        // Do nothing.
                    } catch (CancellationException e) {
                        // Do nothing.
                    } catch (ExecutionException e) {
                        // Do nothing.
                    }
                    Log.i(TAG, "Failsafe service disabled.");
                }
            }
        }
    }

    /**
     * A shared preference listener that changes settings on the failsafe server when
     * settings are changed.
     */
    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    // If any of the keys related to this service changed, update them.
                    switch (key) {
                        case "pref_failsafe_enable":
                        case "pref_failsafe_addr":
                        case "pref_failsafe_timeout":
                            updatePreferences(sharedPreferences);
                            break;
                    }
                }
            };
}
