package com.platypus.android.server;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment that controls the starting and stopping of the vehicle server.
 *
 * This fragment provides a simple full-screen one-touch interface for starting and stopping the
 * vehicle server.  While this screen is up, it can also lock the display on and disables button
 * functionality, making it easier to ensure that the server stays up and running.
 */
public class LauncherFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_launcher, container, false);
    }
}
