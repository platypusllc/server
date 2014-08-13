package com.platypus.server;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class ServerService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * Tag used for logging handler
     */
    private static final String TAG = ServerService.class.getName();
    private SharedPreferences preferences;

    public ServerService() {
    }

    @Override
    public void onCreate() {
        // Retrieve the shared preferences object for the server application.
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit()
                .putBoolean("server_service_checkbox", true)
                .commit();
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting Platypus Server.");

        // Configure this service as a foreground service.
        Intent notificationIntent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
                .setOngoing(true)
                .setContentTitle("Platypus Server")
                .setContentText("Running normally.")
                .setContentInfo("No connection.")
                .setSmallIcon(R.drawable.ic_stat_platypus_icon_transparent)
                .setContentIntent(pendingIntent).build();
        startForeground(R.string.server_service_started, notification);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // This is not supported, so we return null.
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stopping Platypus Server.");

        // Remove this service as a foreground service.
        stopForeground(true);

        // Stop listening for changes in the preferences.
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        preferences.edit()
                .putBoolean("server_service_checkbox", false)
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

    }
}
