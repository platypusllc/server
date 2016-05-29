package com.platypus.android.server;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;


/**
 * Application configuration for the Platypus Server.
 */
public class PlatypusApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Configure Firebase to work offline.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
