package com.platypus.android.server;

import android.app.ActivityManager;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
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

    private static final String TAG = LauncherFragment.class.getSimpleName();

    final Handler mHandler = new Handler();

    protected TextView mHomeText;
    protected Button mLaunchButton;
    protected Button mSetHomeButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment.
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);

        // Get references to UI elements.
        mHomeText = (TextView) view.findViewById(R.id.launcher_home_text);
        mLaunchButton = (Button) view.findViewById(R.id.launcher_launch_button);
        mSetHomeButton = (Button) view.findViewById(R.id.launcher_home_button);

        // Add listener for starting/stopping vehicle service.
        mLaunchButton.setOnLongClickListener(new LaunchListener());

        // Add listener for home button click.
        mSetHomeButton.setOnLongClickListener(new SetHomeListener());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .registerOnSharedPreferenceChangeListener(this);

        // Update UI from most recent settings.
        updateHomeLocation();
        updateLaunchStatus();
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

        // Set the home location label to a formatted string of the home latitude and longitude.
        mHomeText.setText(String.format(
                getResources().getString(R.string.launcher_home_text_format),
                sharedPreferences.getFloat("pref_home_latitude", Float.NaN),
                sharedPreferences.getFloat("pref_home_longitude", Float.NaN)));
    }

    /**
     * Updates the launch button depending on whether the service is running or not.
     */
    protected void updateLaunchStatus() {
        // TODO: improve this UI.
        if (isVehicleServiceRunning()) {
            mLaunchButton.setText(getResources().getString(R.string.launcher_launch_button_stop));
        } else {
            mLaunchButton.setText(getResources().getString(R.string.launcher_launch_button_start));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // If the home location was changed, update the server application.
        if (key.equals("pref_home_latitude") || key.equals("pref_home_longitude")) {
            updateHomeLocation();
        }
    }

    /**
     * Listens for long-click events on "Set Home" button and updates home location.
     */
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
                Log.w(TAG, "Home location not set: location was unavailable.");
                return true;
            }

            // If the fix is older than 10 minutes (600000 milliseconds), reject it.
            if (System.currentTimeMillis() - home.getTime() > 600000) {
                Toast.makeText(getActivity().getApplicationContext(),
                        "Location was older than 10 minutes. Try opening Google Maps or starting " +
                                "the server to acquire an up-to-date GPS update.",
                        Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Home location not set: location was too old.");
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
            Log.i(TAG, "Home location was set to " +
                       "[" + home.getLatitude() + "," + home.getLongitude() + "]");
            return true;
        }
    }

    /**
     * Listens for long-click events on "Launch" button and starts/stops vehicle service.
     */
    class LaunchListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            // Disable the launch button temporarily (until the service is done processing).
            mLaunchButton.setEnabled(false);

            if (!isVehicleServiceRunning()) {
                // If the service is not running, start it.
                getActivity().startService(new Intent(getActivity(), VehicleService.class));
                Log.i(TAG, "Vehicle service started.");
            } else {
                // If the service is running, stop it.
                getActivity().stopService(new Intent(getActivity(), VehicleService.class));
                Log.i(TAG, "Vehicle service stopped.");
            }

            // Schedule the button to update launch status and re-enable.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateLaunchStatus();
                    mLaunchButton.setEnabled(true);
                }
            }, 3000);
            return true;
        }
    }

    /**
     * Checks whether the vehicle service is still running by iterating through all services.
     * @return whether the vehicle service is currently running or not
     */
    protected boolean isVehicleServiceRunning() {
        ActivityManager manager =
                (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (VehicleService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
