package com.platypus.android.server;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fragment that controls the starting and stopping of the vehicle server.
 * <p/>
 * This fragment provides a simple full-screen one-touch interface for starting and stopping the
 * vehicle server.  While this screen is up, it can also lock the display on and disables button
 * functionality, making it easier to ensure that the server stays up and running.
 */
public class LauncherFragment extends Fragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment.
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);

        // Add listener for home button click.
        Button setHomeButton = (Button) view.findViewById(R.id.launcher_home_button);
        setHomeButton.setOnLongClickListener(new SetHomeListener());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .registerOnSharedPreferenceChangeListener(this);

        // Update home location from most recent settings.
        updateHomeLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Updates the home location text from the current values in SharedPreferences.
     */
    protected void updateHomeLocation() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        View rootView = getView();
        if (rootView == null) return;

        TextView homeText = (TextView) getView().findViewById(R.id.launcher_home_text);
        if (homeText == null) return;

        // Set the home location label to a formatted string of the home latitude and longitude.
        homeText.setText(String.format(
                getResources().getString(R.string.launcher_home_text_format),
                sharedPreferences.getFloat("pref_home_latitude", Float.NaN),
                sharedPreferences.getFloat("pref_home_longitude", Float.NaN)));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // If the home location was changed, update the server application.
        if (key.equals("pref_home_latitude") || key.equals("pref_home_longitude")) {
            updateHomeLocation();
        }
    }

    class SetHomeListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            // Get last-known GPS location recorded on this phone.
            LocationManager manager =
                    (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            Location home = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            // If there is no fix, alert the user.
            if (home == null) {
                Toast.makeText(getActivity().getApplicationContext(),
                        "No location available. Try opening Google Maps or starting the server " +
                                "to acquire an up-to-date GPS update.",
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            // If the fix is older than 10 minutes (600000 milliseconds), reject it.
            if (System.currentTimeMillis() - home.getTime() > 600000) {
                Toast.makeText(getActivity().getApplicationContext(),
                        "Location was older than 10 minutes. Try opening Google Maps or starting " +
                                "the server to acquire an up-to-date GPS update.",
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            // Change the home location in the shared preferences.
            Toast.makeText(getActivity().getApplicationContext(),
                    "Home location successfully updated.",
                    Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .putFloat("pref_home_latitude", (float) home.getLatitude())
                    .putFloat("pref_home_longitude", (float) home.getLongitude())
                    .commit();
            return true;
        }
    }
}
