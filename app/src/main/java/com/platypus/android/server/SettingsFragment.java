package com.platypus.android.server;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

/**
 * Fragment that implements a dynamically-generated settings panel for the server.
 * <p/>
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

        // Add a click handler to synchronize logs.
        Preference button = (Preference) findPreference("pref_cloud_sync");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                LogUploadService.runSyncNow(getActivity());
                return true;
            }
        });
        
        // Create a shared preference handler to schedule and un-schedule log file auto-uploads.
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                        if ("pref_cloud_autosync_enable".equals(key))
                            LogUploadService.updateAutoSync(getActivity());
                    }
                });
    }
}
