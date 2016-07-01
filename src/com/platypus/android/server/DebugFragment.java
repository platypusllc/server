package com.platypus.android.server;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.platypus.android.server.gui.GainView;
import com.platypus.android.server.gui.SensorView;
import com.platypus.android.server.gui.TeleopView;
import com.platypus.crw.udp.UdpVehicleServer;

import java.net.InetSocketAddress;

/**
 * Implements a debugging panel that can be used to test the vehicle server.
 *
 * This fragment implements a debugging panel to observe incoming sensor data and teleoperate the
 * vehicle directly for testing purposes.
 */
public class DebugFragment extends Fragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected UdpVehicleServer mServer = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create a new connection to a vehicle server.
        mServer = new UdpVehicleServer();

        // Listen for updates to the server port settings.
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        // Update the connection of the vehicle server to use the current port.
        new UpdateServerConnection().execute(sharedPreferences);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment.
        View view = inflater.inflate(R.layout.fragment_debug, container, false);

        // Register the vehicle service with the view elements.
        TeleopView teleopView = (TeleopView) view.findViewById(R.id.teleop);
        teleopView.setVehicleServer(mServer);

        GainView gainView = (GainView) view.findViewById(R.id.gains);
        gainView.setVehicleServer(mServer);

        SensorView sensorView = (SensorView) view.findViewById(R.id.sensors);
        sensorView.setVehicleServer(mServer);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Shutdown vehicle server and null the reference.
        mServer.shutdown();
        mServer = null;
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_server_port")) {
            // Update the connection of the vehicle server to use the current port.
            new UpdateServerConnection().execute(sharedPreferences);
        }
    }

    protected class UpdateServerConnection extends AsyncTask<SharedPreferences, Void, Void> {

        @Override
        protected Void doInBackground(SharedPreferences... params) {
            // Update the connection of the vehicle server to use the current port.
            int port = Short.parseShort(params[0].getString("pref_server_port", "11411"));
            mServer.setVehicleService(new InetSocketAddress("localhost", port));
            return null;
        }
    }
}
