package com.platypus.android.server;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.platypus.crw.CrwSecurityManager;
import com.platypus.crw.data.Utm;
import com.platypus.crw.data.UtmPose;
import com.platypus.crw.udp.UdpVehicleService;

import org.jscience.geography.coordinates.LatLong;
import org.jscience.geography.coordinates.UTM;
import org.jscience.geography.coordinates.crs.ReferenceEllipsoid;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import robotutils.Pose3D;
import robotutils.Quaternion;

/**
 * Android Service to register sensor and Amarino handlers for Android.s
 * Contains a RosVehicleServer and a VehicleServer object.
 *
 * @author pkv
 * @author kshaurya
 */
public class VehicleService extends Service {
    private static final int SERVICE_ID = 11312;
    private static final String TAG = VehicleService.class.getSimpleName();
    public static final String START_ACTION = "com.platypus.android.server.SERVICE_START";
    public static final String STOP_ACTION = "com.platypus.android.server.SERVICE_STOP";

    // Default values for parameters
    private static final int DEFAULT_UDP_PORT = 11411;
    final int GPS_UPDATE_RATE = 200; // in milliseconds

    // Variable storing the current started/stopped status of the service.
    protected AtomicBoolean isRunning = new AtomicBoolean(false);

    // Reference to vehicle logfile.
    private VehicleLogger mLogger;

    // Reference to vehicle controller;
    private Controller mController;

    // Objects implementing actual functionality
    private VehicleServerImpl _vehicleServerImpl;
    private UdpVehicleService _udpService;
    // Lock objects that prevent the phone from sleeping
    private WakeLock _wakeLock = null;
    private WifiLock _wifiLock = null;
    // global variable to reference rotation vector values
    private float[] rotationMatrix = new float[9];
    private final SensorEventListener rotationVectorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // TODO Auto-generated method stub
                SensorManager.getRotationMatrixFromVector(rotationMatrix,
                        event.values);
                double yaw = Math.atan2(-rotationMatrix[5], -rotationMatrix[2]);

                if (_vehicleServerImpl != null) {
                    _vehicleServerImpl.filter.compassUpdate(yaw,
                            System.currentTimeMillis());
//					logger.info("COMPASS: " + yaw);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    /**
     * UPDATE: 7/03/12 - Handles gyro updates by calling the appropriate update.
     */
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // TODO Auto-generated method stub
            /*
             * Convert phone coordinates to world coordinates. use magnetometer
			 * and accelerometer to get orientation Simple rotation is 90�
			 * clockwise about positive y axis. Thus, transformation is: // / M[
			 * 0] M[ 1] M[ 2] \ / values[0] \ = gyroValues[0] // | M[ 3] M[ 4]
			 * M[ 5] | | values[1] | = gyroValues[1] // \ M[ 6] M[ 7] M[ 8] / \
			 * values[2] / = gyroValues[2] //
			 */
            float[] gyroValues = new float[3];
            gyroValues[0] = rotationMatrix[0] * event.values[0]
                    + rotationMatrix[1] * event.values[1] + rotationMatrix[2]
                    * event.values[2];
            gyroValues[1] = rotationMatrix[3] * event.values[0]
                    + rotationMatrix[4] * event.values[1] + rotationMatrix[5]
                    * event.values[2];
            gyroValues[2] = rotationMatrix[6] * event.values[0]
                    + rotationMatrix[7] * event.values[1] + rotationMatrix[8]
                    * event.values[2];

            if (_vehicleServerImpl != null)
                _vehicleServerImpl.setPhoneGyro(gyroValues);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    /**
     * Handles GPS updates by calling the appropriate update.
     */
    private LocationListener locationListener = new LocationListener() {
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }

        public void onLocationChanged(Location location) {

            // Convert from lat/long to UTM coordinates
            UTM utmLoc = UTM.latLongToUtm(
                    LatLong.valueOf(location.getLatitude(),
                            location.getLongitude(), NonSI.DEGREE_ANGLE),
                    ReferenceEllipsoid.WGS84);

            // Convert to UTM data structure
            Pose3D pose = new Pose3D(utmLoc.eastingValue(SI.METER),
                    utmLoc.northingValue(SI.METER), (location.hasAltitude()
                    ? location.getAltitude()
                    : 0.0), (location.hasBearing()
                    ? Quaternion.fromEulerAngles(0.0, 0.0,
                    (90.0 - location.getBearing()) * Math.PI
                            / 180.0)
                    : Quaternion.fromEulerAngles(0, 0, 0)));
            Utm origin = new Utm(utmLoc.longitudeZone(),
                    utmLoc.latitudeZone() > 'O');
            UtmPose utm = new UtmPose(pose, origin);

            // Apply update using filter object
            if (_vehicleServerImpl != null) {
                _vehicleServerImpl.filter.gpsUpdate(utm, location.getTime());
//				logger.info("GPS: " + utmLoc + ", " + utmLoc.longitudeZone()
//						+ utmLoc.latitudeZone() + ", " + location.getAltitude()
//						+ ", " + location.getBearing());
            }
        }
    };

    /**
     * A shared preference listener that changes settings on the implementation if
     * settings are changed.
     */
    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    // TODO: fill this in to update preferences.
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();

        // Disable all DNS lookups (safer for private/ad-hoc networks)
        CrwSecurityManager.loadIfDNSIsSlow();

        // Disable strict-mode (TODO: remove this and use handlers)
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Register a shared preference listener to listen for updates.
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(mPreferenceListener);

        // Get reference to vehicle controller service.
        mController = new Controller(this);

        // TODO: optimize this to allocate resources up here and handle multiple
        // start commands
    }

    /**
     * Access method to get underlying implementation of server functionality.
     *
     * @return An interface allowing high-level control of the boat.
     */
    public VehicleServerImpl getServer() {
        return _vehicleServerImpl;
    }

    /**
     * Main service initialization: called whenever a request is made to start
     * the Airboat service.
     * <p/>
     * This is where the vehicle implementation is started, sensors are
     * registered, and the update loop and RPC server are started.
     */
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // Ensure that we do not reinitialize if not necessary.
        if (isRunning.get()) {
            Log.w(TAG, "Attempted to start while running.");
            return Service.START_STICKY;
        }

        // start tracing to "/sdcard/trace_crw.trace"
        // Debug.startMethodTracing("trace_crw");

        // Create a new vehicle log file for this service.
        if (mLogger != null)
            mLogger.close();
        mLogger = new VehicleLogger();

        // Get context (used for system functions)
        Context context = getApplicationContext();

        // Hook up to necessary Android sensors
        SensorManager sm;
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sm.registerListener(gyroListener, gyro,
                SensorManager.SENSOR_DELAY_NORMAL);
        Sensor rotation_vector = sm
                .getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sm.registerListener(rotationVectorListener, rotation_vector,
                SensorManager.SENSOR_DELAY_NORMAL);

        // Hook up to the GPS system
        LocationManager gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria c = new Criteria();
        c.setAccuracy(Criteria.ACCURACY_FINE);
        c.setPowerRequirement(Criteria.NO_REQUIREMENT);
        String provider = gps.getBestProvider(c, false);
        if (provider == null) {
            Log.e(TAG, "Failed to start Platypus Server: No sufficiently accurate location provider.");
            sendNotification("Failed to start Platypus Server: No sufficiently accurate location provider.");
            stopSelf();
            return Service.START_STICKY;
        }

        try {
            gps.requestLocationUpdates(provider, GPS_UPDATE_RATE, 0, locationListener);
        } catch (SecurityException e){
            Log.e(TAG, "Failed to start Platypus Server: Inadequate permissions to access accurate location.");
            sendNotification("Failed to start Platypus Server: Inadequate permissions to access accurate location.");
            stopSelf();
            return Service.START_STICKY;
        }
        // Create the internal vehicle server implementation.
        _vehicleServerImpl = new VehicleServerImpl(this, mLogger, mController);

        // Start up UDP vehicle service in the background
        new Thread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(VehicleService.this);

                try {
                    // Create a UdpVehicleService to expose the data object
                    _udpService = new UdpVehicleService(DEFAULT_UDP_PORT, _vehicleServerImpl);
                    // If given a UDP registry parameter, add registry to
                    // service
					/*
					String udpRegistryStr = intent
							.getStringExtra(UDP_REGISTRY_ADDR);
					//_udpRegistryAddr = CrwNetworkUtils.toInetSocketAddress(udpRegistryStr);
					// TODO: add registry from the SharedPreferences.
                    if (_udpRegistryAddr! = null) {
						_udpService.addRegistry(_udpRegistryAddr);

                    } else {
                        //((PlatypusApplication)getApplicationContext()).setFailsafe_IPAddress(_udpRegistryAddr.getHostName());
                        Log.w(TAG, "Unable to parse '" + udpRegistryStr
								+ "' into UDP address.");
					}
					*/

                    //((PlatypusApplication)getApplicationContext()).setFailsafe_IPAddress(CrwNetworkUtils.getLocalhost(udpRegistryStr));
                } catch (Exception e) {
                    Log.e(TAG, "UdpVehicleService failed to launch", e);
                    sendNotification("UdpVehicleService failed: " + e.getMessage());
                    stopSelf();
                }
            }
        }).start();

        // Log the velocity gains before starting the service
        int[] axes = {0, 5};
        for (int axis : axes) {
            double[] gains = _vehicleServerImpl.getGains(axis);
            try {
                mLogger.info(new JSONObject()
                        .put("gain", new JSONObject()
                                .put("axis", axis)
                                .put("values", gains)));
            } catch (JSONException e) {
                Log.w(TAG, "Failed to serialize gains.");
            }
        }

        // Prevent phone from sleeping or turning off wifi
        {
            // Acquire a WakeLock to keep the CPU running
            PowerManager pm = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            _wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "PlatypusVehicleWakeLock");
            _wakeLock.acquire();

            // Acquire a WifiLock to keep the phone from turning off wifi
            WifiManager wm = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            _wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "PlatypusVehicleWifiLock");
            _wifiLock.acquire();
        }

        // Indicate that the service should not be stopped arbitrarily.
        // This is now a foreground service.  It should not be stopped by the system.
        {
            // Set up the notification to open main activity when clicked.
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            // Add a notification to the menu.
            Notification notification = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Platypus Server")
                    .setContentText("Running normally.")
                    .setContentIntent(contentIntent)
                    .build();
            startForeground(SERVICE_ID, notification);
        }

        // Report successful initialization of the service.
        Intent startupIntent = new Intent().setAction(START_ACTION);
        LocalBroadcastManager.getInstance(this).sendBroadcast(startupIntent);
        isRunning.set(true);

        Log.i(TAG, "VehicleService started.");
        return Service.START_STICKY;
    }

    /**
     * Called when there are no longer any consumers of the service and
     * stopService has been called.
     * <p/>
     * This is where the RPC server and update loops are killed, the sensors are
     * unregistered, and the current vehicle implementation is unhooked from all
     * of its callbacks and discarded (allowing safe spawning of a new
     * implementation when the service is restarted).
     */
    @Override
    public void onDestroy() {
        // Stop tracing to "/sdcard/trace_crw.trace"
        Debug.stopMethodTracing();

        // Stop the vehicle log for this run.
        if (mLogger != null) {
            mLogger.close();
            mLogger = null;
        }

        // Disconnect from the vehicle controller.
        if (mController != null) {
            mController.shutdown();
            mController = null;
        }

        // Unregister shared preference listener to listen for updates.
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(mPreferenceListener);

        // Shutdown the vehicle services
        if (_udpService != null) {
            try {
                _udpService.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "UdpVehicleService shutdown error", e);
            }
            _udpService = null;
        }

        // Release locks on wifi and CPU
        if (_wakeLock != null) {
            _wakeLock.release();
        }
        if (_wifiLock != null) {
            _wifiLock.release();
        }

        // Disconnect from the Android sensors
        SensorManager sm;
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sm.unregisterListener(gyroListener);
        sm.unregisterListener(rotationVectorListener);

        // Disconnect from GPS updates
        LocationManager gps;
        gps = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            gps.removeUpdates(locationListener);
        } catch (SecurityException e){
            // Optionally notify user
        }

        // Disconnect the data object from this service
        if (_vehicleServerImpl != null) {
            _vehicleServerImpl.shutdown();
            _vehicleServerImpl = null;
        }

        // Disable this as a foreground service
        stopForeground(true);

        // Report successful destruction of the service.
        Intent startupIntent = new Intent().setAction(STOP_ACTION);
        LocalBroadcastManager.getInstance(this).sendBroadcast(startupIntent);
        isRunning.set(false);

        Log.i(TAG, "VehicleService stopped.");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void sendNotification(CharSequence text) {
        // Create an intent that refers to this service.
        Intent notificationIntent = new Intent(this, VehicleService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        // Build a notification with the specified text.
        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("Platypus Server")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();

        // Send the notification to the manager for display.
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(SERVICE_ID, notification);
    }
}
