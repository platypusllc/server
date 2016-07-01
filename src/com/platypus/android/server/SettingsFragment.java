package com.platypus.android.server;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Fragment that implements a dynamically-generated settings panel for the server.
 *
 * This panel is generated from an XML layout resource, and changes settings in the application
 * SharedPreferences.  These preferences can be dynamically queried by the server, and are
 * automatically saved and preserved across software updates.
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
}
