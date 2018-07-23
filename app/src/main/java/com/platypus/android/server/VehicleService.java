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
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.platypus.crw.CrwSecurityManager;
import com.platypus.crw.data.Utm;
import com.platypus.crw.data.UtmPose;
import com.platypus.crw.udp.UdpVehicleService;

import org.jscience.geography.coordinates.LatLong;
import org.jscience.geography.coordinates.UTM;
import org.jscience.geography.coordinates.crs.ReferenceEllipsoid;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import com.platypus.crw.data.Pose3D;
import com.platypus.crw.data.Quaternion;

/**
 * Android Service to register sensor and Amarino handlers for Android.s
 * Contains a RosVehicleServer and a VehicleServer object.
 *
 * @author pkv
 * @author kshaurya
 */
public class VehicleService extends Service {
    public static final String START_ACTION = "com.platypus.android.server.SERVICE_START";
    public static final String STOP_ACTION = "com.platypus.android.server.SERVICE_STOP";
    private static final int SERVICE_ID = 11312;
    private static final String TAG = VehicleService.class.getSimpleName();
    final int GPS_UPDATE_RATE = 100; // in milliseconds

    // Variable storing the current started/stopped status of the service.
    protected AtomicBoolean isRunning = new AtomicBoolean(false);

    // Variable storing the Firebase instance ID associated with this particular run.
    protected String mFirebaseId = null;

    // Reference to vehicle logfile.
    private VehicleLogger mLogger;

    // Reference to vehicle controller;
    private Controller mController;
    public void send(JSONObject jsonObject)
    {
        Log.d(TAG, "VehicleService.send() called...");
        if (mController != null)
        {
            try
            {
                if (mController.isConnected())
                {
                    Log.d(TAG, "    sending this JSON: ");
                    Log.d(TAG, jsonObject.toString());
                    mController.send(jsonObject);
                }
                else
                {
                    Log.w(TAG, "    mController is NOT connected");
                }
            }
            catch (IOException e)
            {
                Log.w(TAG, "Failed to send command.", e);
            }
        }
        else
        {
            Log.w(TAG, "    mController is null");
        }
    }


    public class LocalBinder extends Binder {
        VehicleService getService() {
            Log.d(TAG, "VehicleService: binding in process...");
            return VehicleService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    // Objects implementing actual functionality
    private VehicleServerImpl _vehicleServerImpl;
    private UdpVehicleService _udpService;
    private final Object mUdpLock = new Object();

    // Lock objects that prevent the phone from sleeping
    private WakeLock _wakeLock = null;
    private WifiLock _wifiLock = null;
    // global variable to reference rotation vector values
    private float[] rotationMatrix = new float[9];

    SharedPreferences sp;

    private final SensorEventListener rotationVectorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // TODO Auto-generated method stub
                SensorManager.getRotationMatrixFromVector(rotationMatrix,
                        event.values);

                float[] values = new float[3];
                SensorManager.getOrientation(rotationMatrix,values);
                //double yaw = Math.atan2(-rotationMatrix[5], -rotationMatrix[2]);
                double yaw = Math.PI-values[0];

                // include 90 degrees offset if a bluebox housing is installed on the boat
		            if (sp != null)
		            {
				            boolean bluebox_installed = sp.getBoolean("pref_bluebox_installed", false);
				            if (bluebox_installed) yaw -= Math.PI / 2.0;
		            }

                if (_vehicleServerImpl != null)
                {
                    _vehicleServerImpl.filter.compassUpdate(yaw, System.currentTimeMillis());
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
            {
                Log.v("gyro", String.format("gyro:  %.2f,  %.2f,  %.2f  rev./sec",
                        gyroValues[0] / 2. / Math.PI,
                        gyroValues[1] / 2. / Math.PI,
                        gyroValues[2] / 2. / Math.PI));
                _vehicleServerImpl.setPhoneGyro(gyroValues);
            }
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
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            Log.d("onLocationChanged", String.format("latlng = %f, %f", lat, lng));
            UTM utmLoc = UTM.latLongToUtm(
                    LatLong.valueOf(lat, lng, NonSI.DEGREE_ANGLE),
                    ReferenceEllipsoid.WGS84);
            Log.d("onLocationChanged", String.format("utm = %s  %d%c",
                    utmLoc.toString(), utmLoc.longitudeZone(), utmLoc.latitudeZone()));

            // Convert to UTM data structure
            Pose3D pose = new Pose3D(utmLoc.eastingValue(SI.METER),
                    utmLoc.northingValue(SI.METER), (location.hasAltitude()
                    ? location.getAltitude()
                    : 0.0), (location.hasBearing()
                    ? Quaternion.fromEulerAngles(0.0, 0.0,
                    (90.0 - location.getBearing()) * Math.PI
                            / 180.0)
                    : Quaternion.fromEulerAngles(0, 0, 0)));

            boolean isNorth = utmLoc.latitudeZone() > 'M';
            Log.d("onLocationChanged", "isNorth = " + (isNorth ? "True" : "False"));
            Utm origin = new Utm(utmLoc.longitudeZone(), isNorth);
            UtmPose utm = new UtmPose(pose, origin);

            // Apply update using filter object
            if (_vehicleServerImpl != null) _vehicleServerImpl.filter.gpsUpdate(utm, location.getTime());
            if (!(Boolean)_vehicleServerImpl.getState(VehicleState.States.HAS_FIRST_GPS.name))
            {
                // use the first GPS fix as the default home pose
                _vehicleServerImpl.setState(VehicleState.States.HAS_FIRST_GPS.name, true);
                _vehicleServerImpl.setState(VehicleState.States.HOME_POSE.name, utm);
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
                    if ("pref_server_port".equals(key))
                        startOrUpdateUdpServer();
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
     * Starts UDP server, either at startup or when the UDP port is changed.
     * If a server instance already exists, it is shutdown first.
     */
    private void startOrUpdateUdpServer() {
        // Start up UDP vehicle service in the background
        new Thread(new Runnable() {
            @Override
            public void run() {
                final SharedPreferences preferences =
                        PreferenceManager.getDefaultSharedPreferences(VehicleService.this);

                synchronized (mUdpLock) {
                    if (_udpService != null)
                        _udpService.shutdown();

                    try {
                        final int port = Integer.parseInt(preferences.getString("pref_server_port", "11411").trim());
                        _udpService = new UdpVehicleService(port, _vehicleServerImpl);
                        Log.i(TAG, "UdpVehicleService launched on port " + port + ".");
                    } catch (Exception e) {
                        Log.e(TAG, "UdpVehicleService failed to launch", e);
                        sendNotification("UdpVehicleService failed: " + e.getMessage());
                        stopSelf();
                    }
                }
            }
        }).start();
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
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to start Platypus Server: Inadequate permissions to access accurate location.");
            sendNotification("Failed to start Platypus Server: Inadequate permissions to access accurate location.");
            stopSelf();
            return Service.START_STICKY;
        }
        // Create the internal vehicle server implementation.
        _vehicleServerImpl = new VehicleServerImpl(this, mLogger, mController);
		    sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		    // turn off gps provider if preferences say to use decawave instead
        if (sp.getBoolean("pref_using_decawave", false))
        {
            gps.removeUpdates(locationListener);
        }

        // Start up UDP vehicle service in the background
        startOrUpdateUdpServer();

        // Load and save gains to trigger logging of the values.
        // (This should not change gains at all.)
        int[] axes = {0, 5};
        for (int axis : axes) {
            double[] gains = _vehicleServerImpl.getGains(axis);
            _vehicleServerImpl.setGains(axis, gains);
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

        // Record the startup of this server to the Firebase database.
        DatabaseReference usageRef = FirebaseUtils.getDatabase()
                .getReference("usage")
                .child(Build.SERIAL)
                .push();
        mFirebaseId = usageRef.getKey();
        usageRef.child("start")
                .setValue(System.currentTimeMillis())
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Failed to report usage:", e);
                            }
                        });

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
        } catch (SecurityException e) {
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

        // Record the shutdown of this server to the Firebase database.
        if (mFirebaseId != null) {
            FirebaseUtils.getDatabase()
                    .getReference("usage")
                    .child(Build.SERIAL)
                    .child(mFirebaseId)
                    .child("stop")
                    .setValue(System.currentTimeMillis())
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.w(TAG, "Failed to report usage:", e);
                                }
                            });
            FirebaseUtils.getDatabase()
                    .getReference("vehicles")
                    .child(Build.SERIAL)
                    .child("lastUpdated")
                    .setValue(ServerValue.TIMESTAMP)
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.w(TAG, "Failed to update timestamp:", e);
                                }
                            });
        } else {
            Log.w(TAG, "Failed to report usage: start record not generated.");
        }
        mFirebaseId = null;

        Log.i(TAG, "VehicleService stopped.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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
