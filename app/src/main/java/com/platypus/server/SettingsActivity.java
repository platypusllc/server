package com.platypus.server;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.List;

/**
 * Allows selection of Platypus Server configuration settings.  These settings immediately take
 * effect and can be modified at any time to change the behavior of the Platypus service.
 */
public class SettingsActivity extends PreferenceActivity {

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener
            = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || ServerPreferenceFragment.class.getName().equals(fragmentName)
                || MechanicalPreferenceFragment.class.getName().equals(fragmentName)
                || super.isValidFragment(fragmentName);
    }

    /**
     * This fragment shows general preferences only.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        /**
         * Listener that can force start and stop the server service.
         */
        private Preference.OnPreferenceChangeListener serverServiceListener
                = new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean serviceStartedValue = (Boolean) newValue;

                if (serviceStartedValue) {
                    Intent intent = new Intent(getActivity().getApplicationContext(), ServerService.class);
                    getActivity().startService(intent);

                } else {
                    Intent intent = new Intent(getActivity().getApplicationContext(), ServerService.class);
                    getActivity().stopService(intent);
                }

                return true;
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            // Bind the summaries of EditText/List/Dialog preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.

            // No special preferences in this Fragment.

            // Connect service checkbox to start and stop Android service.
            CheckBoxPreference serverServiceCheckbox =
                    (CheckBoxPreference) findPreference("server_service_checkbox");
            serverServiceCheckbox.setOnPreferenceChangeListener(serverServiceListener);
        }
    }

    /**
     * This fragment shows server preferences only.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ServerPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_server);

            // Bind the summaries of EditText/List/Dialog preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("madara_server_multicast_address_text"));
        }
    }

    /**
     * This fragment shows mechanical preferences only.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class MechanicalPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_mechanical);

            // Bind the summaries of EditText/List/Dialog preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("phone_mount_list"));
        }
    }
}
