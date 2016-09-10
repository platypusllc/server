package com.platypus.android.server;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


/**
 * Fragment that implements a dynamically-generated settings panel for the server.
 * <p/>
 * This panel is generated from an XML layout resource, and changes settings in the application
 * SharedPreferences.  These preferences can be dynamically queried by the server, and are
 * automatically saved and preserved across software updates.
 */
public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener, FirebaseAuth.AuthStateListener {

    protected Preference mCloudSyncPreference;
    protected Preference mCloudTokenPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Add a click handler to synchronize logs.
        mCloudSyncPreference = findPreference("pref_cloud_sync");
        mCloudSyncPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                LogUploadService.runSyncNow(getActivity());
                return true;
            }
        });

        // Add a click handler to update cloud token.
        mCloudTokenPreference = findPreference("pref_cloud_token");
        mCloudTokenPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                IntentIntegrator integrator = IntentIntegrator.forFragment(SettingsFragment.this);
                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
                integrator.setPrompt("Scan a Platypus Authentication Token");
                integrator.initiateScan();
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set up a preference listener for whenever a key changes.
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

        // Set up a Firebase listener to report login status.
        FirebaseAuth.getInstance().addAuthStateListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the preference listener for whenever a key changes.
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);

        // Remove the Firebase listener that reports login status.
        FirebaseAuth.getInstance().removeAuthStateListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "pref_cloud_autosync_enable":
                LogUploadService.updateAutoSync(getActivity());
                break;
            case "pref_cloud_token":
                FirebaseAuth.getInstance().signOut();
                FirebaseUtils.firebaseSignin(getActivity(), null, null);
                break;
        }
    }

    @Override
    public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            mCloudTokenPreference.setSummary(R.string.pref_cloud_token_summary_auth);
        } else {
            mCloudTokenPreference.setSummary(R.string.pref_cloud_token_summary_noauth);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // If this is the result of a scanning operation, check for a valid token.
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            // Store scan result as an authentication token.
            getPreferenceScreen().getSharedPreferences().edit()
                    .putString("pref_cloud_token", scanResult.getContents())
                    .commit();
        }
    }
}
