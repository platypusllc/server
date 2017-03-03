package com.platypus.android.server;

import android.app.ActivityManager;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.platypus.android.server.gui.SwipeOnlySwitch;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

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

    // bind the VehicleService so we can send sensor type JSON commands
    // https://developer.android.com/guide/components/bound-services.html
    VehicleService mService;
    boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            System.out.println("LauncherFragment: onServiceConnected() called...");
            VehicleService.LocalBinder binder = (VehicleService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            sendSensorsJSON();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            System.out.println("LauncherFragment: onServiceDisconnected() called...");
            mBound= false;
        }
    };

    final Handler mHandler = new Handler();

    protected TextView mHomeText;
    protected TextView mIpAddressText;
    protected Switch mLaunchSwitch;
    protected Button mSetHomeButton;
    protected Button mSetSensorsButton;
    protected ImageView mVehicleImage;
    protected LocationManager mLocationManager;
    /**
     * Listens for checked events on "Launch" button and starts/stops vehicle service.
     */
    CompoundButton.OnCheckedChangeListener mLaunchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            // Disable the launch button temporarily (until the service is done processing).
            mLaunchSwitch.setEnabled(false);
            if (!isVehicleServiceRunning()) {
                // If the service is not running, start it.
                System.out.println("LauncherFragment: slider listener called...");
                Intent intent = new Intent(getActivity(), VehicleService.class);
                getActivity().startService(intent);
                getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                Log.i(TAG, "Vehicle service started.");
            } else {
                // If the service is running, stop it.
                getActivity().unbindService(mConnection);
                getActivity().stopService(new Intent(getActivity(), VehicleService.class));
                Log.i(TAG, "Vehicle service stopped.");
            }

            // Schedule the button to update launch status and re-enable.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateLaunchStatus();
                    mLaunchSwitch.setEnabled(true);
                }
            }, 2000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationManager =
                (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment.
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);

        // Get references to UI elements.
        mHomeText = (TextView) view.findViewById(R.id.launcher_home_text);
        mIpAddressText = (TextView) view.findViewById(R.id.ip_address_text);
        mLaunchSwitch = (SwipeOnlySwitch) view.findViewById(R.id.launcher_launch_switch);
        mSetHomeButton = (Button) view.findViewById(R.id.launcher_home_button);
        mSetSensorsButton = (Button) view.findViewById(R.id.launcher_sensors_button);
        mVehicleImage = (ImageView) view.findViewById(R.id.launcher_vehicle_image);

        // Add listener for starting/stopping vehicle service.
        mLaunchSwitch.setOnCheckedChangeListener(mLaunchListener);

        // Add listener for home button click.
        mSetHomeButton.setOnLongClickListener(new SetHomeListener());

        // Add listener for sensors update button click
        mSetSensorsButton.setOnLongClickListener(new UpdateSensorsListener());

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
        updateServerAddress();
        updateVehicleType();
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Updates the image showing the currently selected vehicle type.
     */
    protected void updateVehicleType() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        switch (sharedPreferences.getString("pref_vehicle_type", "")) {
            case "DIFFERENTIAL":
                mVehicleImage.setImageResource(R.drawable.ic_vehicle_differential);
                break;
            case "VECTORED":
                mVehicleImage.setImageResource(R.drawable.ic_vehicle_vectored);
                break;
            default:
                mVehicleImage.setImageResource(R.drawable.ic_vehicle_unknown);
                break;
        }
    }

    /**
     * Updates the home location text from the current values in SharedPreferences.
     */
    protected void updateHomeLocation() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        // Set the home location label to a formatted string of the home latitude and longitude.
        if (sharedPreferences.contains("pref_home_latitude") &&
                sharedPreferences.contains("pref_home_longitude")) {
            mHomeText.setText(String.format(
                    getResources().getString(R.string.launcher_home_text_format),
                    sharedPreferences.getFloat("pref_home_latitude", Float.NaN),
                    sharedPreferences.getFloat("pref_home_longitude", Float.NaN)));
        } else {
            mHomeText.setText(getResources().getString(R.string.launcher_home_text_content));
        }
    }

    /**
     * Updates the launch button depending on whether the service is running or not.
     */
    protected void updateLaunchStatus() {
        mLaunchSwitch.setOnCheckedChangeListener(null);
        mLaunchSwitch.setChecked(isVehicleServiceRunning());
        mLaunchSwitch.setOnCheckedChangeListener(mLaunchListener);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "pref_home_latitude":
            case "pref_home_longitude":
                updateHomeLocation();
                break;
            case "pref_server_port":
                updateServerAddress();
                break;
            case "pref_vehicle_type":
                updateVehicleType();
                break;
        }
    }

    /**
     * Checks whether the vehicle service is still running by iterating through all services.
     *
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

    /**
     * Helper function that retrieves first valid (non-loopback) IP address
     * over all available interfaces.
     *
     * @return Text representation of current local IP address.
     */
    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, "Failed to get local IP.", ex);
        }
        return null;
    }

    /**
     * Updates the server address that is used by the vehicle.
     */
    public void updateServerAddress() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());

        String port = sharedPreferences.getString("pref_server_port", "11411");
        mIpAddressText.setText(getLocalIpAddress() + ":" + port);
    }
    
    /**
     * Listens for long-click events on "Update Sensors" button and updates sensor types
     */
    class UpdateSensorsListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            sendSensorsJSON();
            return true;
        }
    }
    void sendSensorsJSON()
    {
        System.out.println("LauncherFragment: sendSensorsJSON() called...");

        if (mBound)
        {
            JSONObject sensors_JSON = generateSensorsJSON();

            // TODO: Add vehicle type to this as well
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String vehicle_array_name = "pref_vehicle_type";
            try
            {
                switch (sharedPreferences.getString(vehicle_array_name, "DIFFERENTIAL")) {
                    case "DIFFERENTIAL":
                        sensors_JSON.put("t0", "Prop");
                        break;
                    case "VECTORED":
                        sensors_JSON.put("t0", "Air");
                        break;
                    default:
                        break;
                }
            }
            catch (JSONException e)
            {
                Log.w(TAG, "Failed to serialize vehicle type.", e);
            }

            System.out.println("    sensor JSON: ");
            System.out.println(sensors_JSON.toString());
            mService.send(sensors_JSON);
            mService.getServer().reset_expected_sensors(); // reset sensor type checks
        }
        else
        {
            System.out.println("    mBound = false, sending nothing");
        }
    }
    JSONObject generateSensorsJSON()
    {
        JSONObject sensors_JSON = new JSONObject();
        for (int i = 1; i < 4; i++)
        {
            insertSensorJSON(i, sensors_JSON);
        }
        return sensors_JSON;
    }
    void insertSensorJSON(int sensorID, JSONObject sensors_JSON)
    {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sensor_array_name = "pref_sensor_" + Integer.toString(sensorID) + "_type";
        try
        {
            switch (sharedPreferences.getString(sensor_array_name, "NONE"))
            {
                case "NONE":
                    sensors_JSON.put(String.format("i%d", sensorID), "None");
                    break;
                case "ATLAS_DO":
                    sensors_JSON.put(String.format("i%d", sensorID), "AtlasDO");
                    break;
                case "ATLAS_PH":
                    sensors_JSON.put(String.format("i%d", sensorID), "AtlasPH");
                    break;
                case "ES2":
                    sensors_JSON.put(String.format("i%d", sensorID), "ES2");
                    break;
                case "HDS":
                    sensors_JSON.put(String.format("i%d", sensorID), "HDS");
                    break;
                case "ADAFRUIT_GPS":
                    sensors_JSON.put(String.format("i%d", sensorID), "AdaGPS");
                    break;
                case "AHRS_PLUS":
                    sensors_JSON.put(String.format("i%d", sensorID), "AHRSp");
                    break;
                case "BLUEBOX":
                    sensors_JSON.put(String.format("i%d", sensorID), "BlueBox");
                    break;
                case "WINCH":
                    sensors_JSON.put(String.format("i%d", sensorID), "Winch");
                    break;
                case "SAMPLER":
                    sensors_JSON.put(String.format("i%d", sensorID), "Sampler");
                    break;
                case "RC_SBUS":
                    sensors_JSON.put(String.format("i%d", sensorID), "RC_SBUS");
                    break;
                case "RC_PWM":
                    sensors_JSON.put(String.format("i%d", sensorID), "RC_PWM");
                    break;
                default:
                    break;
            }
        }
        catch (JSONException e)
        {
            Log.w(TAG, "Failed to serialize sensor type.", e);
        }
    }

    /**
     * Listens for long-click events on "Set Home" button and updates home location.
     */
    class SetHomeListener implements View.OnLongClickListener, LocationListener {
        @Override
        public boolean onLongClick(View v) {
            final AtomicInteger countdown = new AtomicInteger(30); // Timeout in seconds.

            // Disable this button until a timeout or a location lock.
            mSetHomeButton.setEnabled(false);
            mSetHomeButton.setText(
                    getResources().getString(
                            R.string.launcher_home_button_title_disabled, countdown.get()));

            // Register for GPS updates (until one of sufficient accuracy, or a timeout).
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);

            // Create a timeout which cancels the homing command.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // If button is re-enabled, we are done here.
                    if (mSetHomeButton.isEnabled())
                        return;

                    // Decrement the counter, update text if not at zero.
                    int count = countdown.decrementAndGet();
                    if (count > 0) {
                        mSetHomeButton.setText(
                                getResources().getString(
                                        R.string.launcher_home_button_title_disabled, count));
                        mHandler.postDelayed(this, 1000);
                        return;
                    }

                    // Remove listener and restore button.
                    mLocationManager.removeUpdates(SetHomeListener.this);
                    mSetHomeButton.setEnabled(true);
                    mSetHomeButton.setText(R.string.launcher_home_button_title);

                    // Alert the user to the failure.
                    Toast.makeText(getActivity().getApplicationContext(),
                            "No location available. Try opening Google Maps or starting the server " +
                                    "to acquire an up-to-date GPS update.",
                            Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Home location not set: location was unavailable.");
                }
            }, 1000);

            return true;
        }

        @Override
        public void onLocationChanged(Location location) {
            // Ignore inaccurate updates (typical during GPS startup).
            if (location.getAccuracy() > 20.0f)
                return;

            // Change the home location in the shared preferences.
            Toast.makeText(getActivity().getApplicationContext(),
                    "Home location successfully updated.",
                    Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .putFloat("pref_home_latitude", (float) location.getLatitude())
                    .putFloat("pref_home_longitude", (float) location.getLongitude())
                    .commit();
            Log.i(TAG, "Home location was set to " +
                    "[" + location.getLatitude() + "," + location.getLongitude() + "]");

            // Re-enable home button and cleanup location updates.
            mLocationManager.removeUpdates(SetHomeListener.this);
            mSetHomeButton.setEnabled(true);
            mSetHomeButton.setText(R.string.launcher_home_button_title);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Do nothing.
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Do nothing.
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Do nothing.
        }
    }
}
