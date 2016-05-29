package com.platypus.android.server;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;
import com.platypus.crw.data.UtmPose;

/**
 * Created by hnx on 6/23/14.
 */
public class PlatypusApplication extends Application{
    @Override
    public void onCreate() {
        super.onCreate();

        // Configure Firebase to work offline.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
