package com.platypus.android.server;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Implements a debugging panel that can be used to test the vehicle server.
 *
 * This fragment implements a debugging panel to observe incoming sensor data and teleoperate the
 * vehicle directly for testing purposes.
 */
public class DebugFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_debug, container, false);
    }
}
