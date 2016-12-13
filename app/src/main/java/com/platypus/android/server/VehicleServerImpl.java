package com.platypus.android.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.platypus.crw.AbstractVehicleServer;
import com.platypus.crw.VehicleController;
import com.platypus.crw.VehicleFilter;
import com.platypus.crw.VehicleServer;
import com.platypus.crw.data.SensorData;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.Utm;
import com.platypus.crw.data.UtmPose;

import org.jscience.geography.coordinates.LatLong;
import org.jscience.geography.coordinates.UTM;
import org.jscience.geography.coordinates.crs.ReferenceEllipsoid;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import robotutils.Pose3D;
import robotutils.Quaternion;

/**
 * Contains the actual implementation of vehicle functionality, accessible as a
 * singleton that is updated and maintained by a background service.
 *
 * @author pkv
 * @author kss
 */
public class VehicleServerImpl extends AbstractVehicleServer {

    public static final int UPDATE_INTERVAL_MS = 100;
    public static final int NUM_SENSORS = 5;
    public static final VehicleController DEFAULT_CONTROLLER = AirboatController.STOP.controller;
    /**
     * Defines the PID gains that will be returned if there is an error.
     */
    public static final double[] NAN_GAINS =
            new double[]{Double.NaN, Double.NaN, Double.NaN};
    public static final double[] DEFAULT_TWIST = {0, 0, 0, 0, 0, 0};
    public static final double SAFE_DIFFERENTIAL_THRUST = 1.0;
    public static final double SAFE_VECTORED_THRUST = 1.0;
    public static final long VELOCITY_TIMEOUT_MS = 2000;
    private static final String TAG = VehicleServerImpl.class.getName();
    protected final SharedPreferences mPrefs;
    protected final SensorType[] _sensorTypes = new SensorType[NUM_SENSORS];
    protected final Object _captureLock = new Object();
    protected final Object _navigationLock = new Object();
    // Status information
    final AtomicBoolean _isConnected = new AtomicBoolean(false);
    final AtomicBoolean _isAutonomous = new AtomicBoolean(false);
    final AtomicBoolean _isRunning = new AtomicBoolean(true);
    // Internal references.
    final Context _context;
    final VehicleLogger mLogger;
    final Controller mController;
    // Velocity shutdown timer.
    final ScheduledThreadPoolExecutor mVelocityExecutor = new ScheduledThreadPoolExecutor(1);
    /**
     * Raw gyroscopic readings from the phone gyro.
     */
    final double[] _gyroPhone = new double[3];
    private final Timer _updateTimer = new Timer();
    private final Timer _navigationTimer = new Timer();
    private final Timer _captureTimer = new Timer();
    protected UtmPose[] _waypoints = new UtmPose[0];
    protected TimerTask _captureTask = null;
    protected TimerTask _navigationTask = null;
    ScheduledFuture mVelocityFuture = null;
    /**
     * Inertial state vector, currently containing a 6D pose estimate:
     * [x,y,z,roll,pitch,yaw]
     */
    UtmPose _utmPose = new UtmPose(new Pose3D(476608.34, 4671214.40, 172.35, 0, 0, 0), new Utm(17, true));

    /**
     * Filter used internally to update the current pose estimate
     */
    VehicleFilter filter = new SimpleFilter();

    /**
     * Inertial velocity vector, containing a 6D angular velocity estimate: [rx,
     * ry, rz, rPhi, rPsi, rOmega]
     */
    Twist _velocities = new Twist(DEFAULT_TWIST);
    /**
     * Hard-coded PID gains and thrust limits per vehicle type.
     * These values are loaded from the application SharedPreferences in the class constructor.
     */
    double[] r_PID = new double[3];
    double[] t_PID = new double[3];

    // TODO: Remove this variable, it is totally arbitrary
    private double winch_depth_ = Double.NaN;
    // Last known temperature and EC values for sensor compensation
    private double _lastTemp = 20.0; // Deg C
    private double _lastEC = 0.0; // uS/cm
    /**
     * Internal update function called at regular intervals to process command
     * and control events.
     */
    private TimerTask _updateTask = new TimerTask() {

        @Override
        public void run() {
            // Do an intelligent state prediction update here
            _utmPose = filter.pose(System.currentTimeMillis()); // TODO: what the hell is this?
            try {
                mLogger.info(new JSONObject()
                        .put("pose", new JSONObject()
                                .put("p", new JSONArray(_utmPose.pose.getPosition()))
                                .put("q", new JSONArray(_utmPose.pose.getRotation().getArray()))
                                .put("zone", _utmPose.origin.toString())));
            } catch (JSONException e) {
                Log.w(TAG, "Unable to serialize pose.");
            }
            sendState(_utmPose.clone());

            // Send vehicle command by converting raw command to appropriate vehicle model.
            JSONObject command = new JSONObject();
            String vehicleType = mPrefs.getString("pref_vehicle_type",
                    _context.getResources().getString(R.string.pref_vehicle_type_default));
            switch (vehicleType) {
                case "DIFFERENTIAL":
                    // Construct objects to hold velocities
                    JSONObject velocity0 = new JSONObject();
                    JSONObject velocity1 = new JSONObject();

                    // Send velocities as a JSON command
                    try {
                        double constrainedV0 = clip(_velocities.dx() - _velocities.drz(), -1.0, 1.0);
                        double constrainedV1 = clip(_velocities.dx() + _velocities.drz(), -1.0, 1.0);

                        // Until ESC reboot is fixed, set the upper limit to SAFE_THRUST
                        constrainedV0 = map(constrainedV0,
                                -1.0, 1.0, // Original range.
                                -VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST, VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST); // New range.
                        constrainedV1 = map(constrainedV1,
                                -1.0, 1.0, // Original range.
                                -VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST, VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST); // New range.

                        velocity0.put("v", (float) constrainedV0);
                        velocity1.put("v", (float) constrainedV1);

                        command.put("m0", velocity0);
                        command.put("m1", velocity1);

                        // Send and log the transmitted command.
                        if (mController.isConnected())
                            mController.send(command);
                        mLogger.info(new JSONObject().put("cmd", command));
                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to serialize command.", e);
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to send command.", e);
                    }
                    break;

                case "VECTORED":
                    // Construct objects to hold velocities
                    JSONObject thrust = new JSONObject();
                    JSONObject rudder = new JSONObject();

                    // Send velocities as a JSON command
                    try {
                        double constrainedV = clip(_velocities.dx(), -1.0, 1.0);

                        // Until ESC reboot is fixed, set the upper limit to SAFE_THRUST
                        constrainedV = map(constrainedV,
                                0.0, 1.0, // Original range.
                                0.0, VehicleServerImpl.SAFE_VECTORED_THRUST); // New range.

                        // Rudder is constrained to +/-1.0
                        double constrainedP = clip(_velocities.drz(), -1.0, 1.0);

                        // Fix for rudder being reversed.
                        constrainedP *= -1.0;

                        thrust.put("v", (float) constrainedV);
                        rudder.put("p", (float) constrainedP);

                        command.put("m0", thrust);
                        command.put("s0", rudder);

                        // Send and log the transmitted command.
                        if (mController.isConnected())
                            mController.send(command);
                        mLogger.info(new JSONObject().put("cmd", command));
                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to serialize command.", e);
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to send command.", e);
                    }
                    break;
                default:
                    Log.w(TAG, "Unknown vehicle type: " + vehicleType);
            }
        }
    };

    /**
     * Creates a new instance of the vehicle implementation. This function
     * should only be used internally when the corresponding vehicle service is
     * started and stopped.
     *
     * @param context the application context to use
     */

    protected VehicleServerImpl(Context context, VehicleLogger logger, Controller controller) {
        _context = context;
        mLogger = logger;
        mController = controller;

        // Connect to the Shared Preferences for this process.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(_context);

        // Load PID values from SharedPreferences.
        // Use hard-coded defaults if not specified.
        r_PID[0] = mPrefs.getFloat("gain_rP", 1.0f);
        r_PID[1] = mPrefs.getFloat("gain_rI", 0.0f);
        r_PID[2] = mPrefs.getFloat("gain_rD", 1.0f);

        t_PID[0] = mPrefs.getFloat("gain_tP", 0.1f);
        t_PID[1] = mPrefs.getFloat("gain_tI", 0.0f);
        t_PID[2] = mPrefs.getFloat("gain_tD", 0.0f);

        // Start a regular update function
        _updateTimer.scheduleAtFixedRate(_updateTask, 0, UPDATE_INTERVAL_MS);

        // Create a thread to read data from the controller board.
        Thread receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Start a loop to receive data from accessory.
                while (_isRunning.get()) {
                    try {
                        onCommand(mController.receive());
                    } catch (Controller.ConnectionException e) {
                        // Do nothing, we don't need to detect this here.
                    } catch (IOException | Controller.ControllerException e) {
                        Log.w(TAG, e);
                    } finally {
                        Thread.yield();
                    }
                }
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * Simple clipping function that restricts a value to a given range.
     *
     * @param input value that needs to be clipped
     * @param min   minimum allowable value
     * @param max   maximum allowable value
     * @return value after it has been clipped between min and max.
     */
    public static double clip(double input, double min, double max) {
        return Math.min(Math.max(input, min), max);
    }

    /**
     * Simple linear scaling function that maps a value from a given input range to a desired output range.
     * <p/>
     * This does *not* clip out of range values.  To invert values, swap min and max.
     *
     * @param input      value that needs to be scaled
     * @param input_min  lower bound of original mapping
     * @param input_max  upper bound of original mapping
     * @param output_min lower bound of desired mapping
     * @param output_max upper bound of desired mapping.
     * @return the input value mapped into the output range.
     */
    public static double map(double input,
                             double input_min, double input_max,
                             double output_min, double output_max) {
        return (input - input_min) / (input_max - input_min)
                * (output_max - output_min) + output_min;
    }

    /**
     * @see VehicleServer#getGains(int)
     */
    @Override
    public double[] getGains(int axis) {

        if (axis == 5)
            return r_PID.clone();
        else if (axis == 0)
            return t_PID.clone();
        else if (axis == 3)
            return new double[]{winch_depth_, 0.0, 0.0};
        else
            return NAN_GAINS;
    }

    /**
     * @see VehicleServer#setGains(int, double[])
     */
    @Override
    public void setGains(int axis, double[] k) {
        // TODO: Get rid of this, it is a hack.
        // Special case to handle winch commands...
        if (axis == 3) {
            JSONObject command = new JSONObject();
            JSONObject winchSettings = new JSONObject();

            // Call command to adjust winch
            try {
                //Set desired winch movement distance
                winchSettings.put("p", (float) Math.abs(k[0]));

                //Hardcoded velocity - get rid of this eventually
                winchSettings.put("v", 500 * Math.signum(k[0]));
                command.put("s2", winchSettings);

                mController.send(command);
                mLogger.info(new JSONObject().put("winch", command));
            } catch (JSONException e) {
                Log.w(TAG, "Unable to construct JSON string from winch command: " + Arrays.toString(k));
            } catch (IOException e) {
                Log.w(TAG, "Unable to send winch command.", e);
            }
            return;
        } else if (axis == 5) {
            r_PID = k.clone();

            // Save the PID values to the SharedPreferences as well.
            mPrefs.edit()
                    .putFloat("gain_rP", (float) r_PID[0])
                    .putFloat("gain_rI", (float) r_PID[1])
                    .putFloat("gain_rD", (float) r_PID[2])
                    .apply();
        } else if (axis == 0) {
            t_PID = k.clone();

            // Save the PID values to the SharedPreferences as well.
            mPrefs.edit()
                    .putFloat("gain_tP", (float) t_PID[0])
                    .putFloat("gain_tI", (float) t_PID[1])
                    .putFloat("gain_tD", (float) t_PID[2])
                    .apply();
        }

        // Log the new gain settings to the logfile.
        try {
            mLogger.info(new JSONObject()
                    .put("gain", new JSONObject()
                            .put("axis", axis)
                            .put("values", Arrays.toString(k))));
        } catch (JSONException e) {
            Log.w(TAG, "Failed to serialize gains.");
        }
    }

    /**
     * Returns the current gyro readings
     */
    public double[] getGyro() {
        return _gyroPhone.clone();
    }

    public void setPhoneGyro(float[] gyroValues) {
        for (int i = 0; i < gyroValues.length; i++)
            _gyroPhone[i] = (double) gyroValues[i];
    }

    /**
     * @see com.platypus.crw.VehicleServer#isConnected()
     */
    public boolean isConnected() {
        return mController.isConnected();
    }

    /**
     * Handles complete Arduino commands, once they are reassembled.
     *
     * @param cmd the list of arguments composing a command
     */
    protected void onCommand(JSONObject cmd) {

        @SuppressWarnings("unchecked")
        Iterator<String> keyIterator = cmd.keys();

        // Iterate through JSON fields
        while (keyIterator.hasNext()) {
            String name = keyIterator.next();
            try {
                JSONObject value = cmd.getJSONObject(name);
                if (name.startsWith("m")) {
                    int motor = name.charAt(1) - 48;
                } else if (name.startsWith("s")) {
                    int sensor = name.charAt(1) - 48;

                    // Hacks to send sensor information
                    if (value.has("type")) {
                        String type = value.getString("type");
                        SensorData reading = new SensorData();

                        if (type.equalsIgnoreCase("es2")) {
                            try {
                                // Parse out temperature and ec values
                                String[] data = value.getString("data").trim().split(" ");
                                double ecData = Double.parseDouble(data[0]);
                                double tempData = Double.parseDouble(data[1]);

                                // Todo: update stored temp and ec values then push to DO/pH probes
                                // Fill in readings from parsed sensor data.
                                reading.channel = sensor;
                                reading.type = SensorType.ES2;
                                reading.data = new double[]{ecData, tempData};
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "Received malformed ES2 Sensor Data: " + value);
                            }
                        } else if (type.equalsIgnoreCase("atlas_do")) {
                            // Fill in readings from parsed sensor data.
                            reading.channel = sensor;
                            reading.type = SensorType.ATLAS_DO;
                            reading.data = new double[]{value.getDouble("data")};
                        } else if (type.equalsIgnoreCase("atlas_ph")) {
                            // Fill in readings from parsed sensor data.
                            reading.channel = sensor;
                            reading.type = SensorType.ATLAS_PH;
                            reading.data = new double[]{value.getDouble("data")};
                        } else if (type.equalsIgnoreCase("hds")) {
                            String nmea = value.getString("data");
                            if (nmea.startsWith("$SDDBT")) { //Depth Below Transducer
                                try {
                                    double depth = Double.parseDouble(nmea.split(",")[3]);

                                    // Fill in readings from parsed sensor data.
                                    reading.type = SensorType.HDS_DEPTH;
                                    reading.channel = sensor;
                                    reading.data = new double[]{depth};
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to parse depth reading: " + nmea);
                                    continue;
                                }
                            } else if (nmea.startsWith("$SDMTW")) { //Water Temperature
                                continue;
                            } else if (nmea.startsWith("$SDRMC")) { //GPS
                                continue;
                            } else {
                                Log.w(TAG, "Unknown NMEA String: " + nmea);
                                continue;
                            }
                        } else if (type.equalsIgnoreCase("battery")) {
                            try {
                                // Parse out voltage and motor velocity values
                                String[] data = value.getString("data").trim().split(" ");
                                double voltage = Double.parseDouble(data[0]);
                                double motor0Velocity = Double.parseDouble(data[1]);
                                double motor1Velocity = Double.parseDouble(data[2]);

                                // Fill in readings from parsed sensor data.
                                reading.channel = sensor;
                                reading.type = SensorType.BATTERY;
                                reading.data = new double[]{voltage, motor0Velocity, motor1Velocity};
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "Received malformed Battery Sensor Data: " + value);
                            }
                        } else if (type.equalsIgnoreCase("winch")) {
                            // Fill in readings from parsed sensor data.
                            reading.channel = sensor;
                            reading.type = SensorType.UNKNOWN;
                            reading.data = new double[]{value.getDouble("depth")};

                            // TODO: Remove this hack to store winch depth
                            winch_depth_ = reading.data[0];
                        }
                        else{   // this is for cases where the type is not parsable
                            Log.w(TAG, "Received sensing of unknown type: '" + type + "'.");
                            continue;
                        }

                        // Send out and log the collected sensor reading.
                        sendSensor(sensor, reading);
                        mLogger.info(new JSONObject()
                                .put("sensor", new JSONObject()
                                        .put("channel", reading.channel)
                                        .put("type", reading.type.toString())
                                        .put("data", new JSONArray(reading.data))));
                    }
                } else if (name.startsWith("g")) {
                    int gpsReceiver = name.charAt(1) - 48;
                    double latitude = -999.;
                    double longitude = -999.;
                    long time_ = 0;
                    if (value.has("lat")) {
                        latitude = value.getDouble("lat");
                    } else {
                        continue;
                    }
                    if (value.has("lon")) {
                        longitude = value.getDouble("lon");
                    } else {
                        continue;
                    }
                    if (value.has("time")) {
                        time_ = value.getLong("time");
                    } else {
                        continue;
                    }

                    // Convert from lat/long to UTM coordinates
                    UTM utmLoc = UTM.latLongToUtm(
                            LatLong.valueOf(latitude, longitude, NonSI.DEGREE_ANGLE),
                            ReferenceEllipsoid.WGS84);

                    // Convert to UTM data structure
                    Pose3D pose = new Pose3D(utmLoc.eastingValue(SI.METER),
                            utmLoc.northingValue(SI.METER),
                            0.0,
                            Quaternion.fromEulerAngles(0, 0, 0));
                    Utm origin = new Utm(utmLoc.longitudeZone(),
                            utmLoc.latitudeZone() > 'O');
                    UtmPose utm = new UtmPose(pose, origin);

                    filter.gpsUpdate(utm, time_);
                } else {
                    Log.w(TAG, "Received unknown param '" + cmd + "'.");
                }
            } catch (JSONException e) {
                Log.w(TAG, "Malformed JSON command '" + cmd + "'.", e);
            }
        }
    }

    // TODO: Revert capture image to take images
    // This is a hack to support the water sampler until PID is working again.
    public synchronized byte[] captureImage(int width, int height) {
        // Call command to fire sampler
        try {
            JSONObject samplerCommand = new JSONObject()
                    .put("s0", new JSONObject()
                            .put("sample", true));
            mController.send(samplerCommand);
            mLogger.info(new JSONObject().put("sampler", true));
            Log.i(TAG, "Triggering sampler.");
        } catch (JSONException e) {
            Log.w(TAG, "Unable to serialize sampler command.");
        } catch (IOException e) {
            Log.w(TAG, "Unable to send sampler command.");
        }
        return new byte[1];
    }

    public synchronized byte[] captureImageInternal(int width, int height) {
        byte[] bytes = AirboatCameraActivity.takePhoto(_context, width, height);
        Log.i(TAG, "Sending image [" + bytes.length + "]");
        return bytes;
    }

    public synchronized boolean saveImage() {
        AirboatCameraActivity.savePhoto(_context);
        Log.i(TAG, "Saving image.");
        return true;
    }

    @Override
    public void startCamera(final int numFrames, final double interval,
                            final int width, final int height) {
        Log.i(TAG, "Starting capture: " + numFrames + "(" + width + "x"
                + height + ") frames @ " + interval + "s");

        // Create a camera capture task
        TimerTask newCaptureTask = new TimerTask() {
            int iFrame = 0;

            @Override
            public void run() {
                synchronized (_captureLock) {
                    // Take a new image and send it out
                    sendImage(captureImageInternal(width, height));
                    iFrame++;

                    // If we exceed numFrames, we finished
                    if (numFrames > 0 && iFrame >= numFrames) {
                        sendCameraUpdate(CameraState.DONE);
                        this.cancel();
                        _captureTask = null;
                    } else {
                        sendCameraUpdate(CameraState.CAPTURING);
                    }
                }
            }
        };

        synchronized (_captureLock) {
            // Cancel any previous capture tasks
            if (_captureTask != null)
                _captureTask.cancel();

            // Schedule this task for execution
            _captureTask = newCaptureTask;
            _captureTimer.scheduleAtFixedRate(_captureTask, 0,
                    (long) (interval * 1000.0));
        }

        // Report the new imaging job in the log file
        try {
            mLogger.info(new JSONObject()
                    .put("img", new JSONObject()
                            .put("num", numFrames)
                            .put("interval", interval)
                            .put("w", width)
                            .put("h", height)));
        } catch (JSONException e) {
            Log.w(TAG, "Unable to serialize image properties.", e);
        }
    }

    @Override
    public void stopCamera() {
        // Stop the thread that sends out images by terminating its
        // navigation flag and then removing the reference to the old flag.
        synchronized (_captureLock) {
            if (_captureTask != null) {
                _captureTask.cancel();
                _captureTask = null;
            }
        }
        sendCameraUpdate(CameraState.CANCELLED);
    }

    @Override
    public CameraState getCameraStatus() {
        synchronized (_captureLock) {
            if (_captureTask != null) {
                return CameraState.CAPTURING;
            } else {
                return CameraState.OFF;
            }
        }
    }

    @Override
    public SensorType getSensorType(int channel) {
        return _sensorTypes[channel];
    }

    @Override
    public void setSensorType(int channel, SensorType type) {
        _sensorTypes[channel] = type;
    }

    @Override
    public int getNumSensors() {
        return NUM_SENSORS;
    }

    @Override
    public UtmPose getPose() {
        return _utmPose;
    }

    /**
     * Takes a 6D vehicle pose, does appropriate internal computation to change
     * the current estimate of vehicle state to match the specified pose. Used
     * for user- or multirobot- pose corrections.
     *
     * @param pose the corrected 6D pose of the vehicle: [x,y,z,roll,pitch,yaw]
     */
    @Override
    public void setPose(UtmPose pose) {

        // Change the offset of this vehicle by modifying filter
        filter.reset(pose, System.currentTimeMillis());

        // Copy this pose over the existing value
        _utmPose = pose.clone();

        // Report the new pose in the log file and to listeners.
        try {
            mLogger.info(new JSONObject()
                    .put("pose", new JSONObject()
                            .put("p", new JSONArray(_utmPose.pose.getPosition()))
                            .put("q", new JSONArray(_utmPose.pose.getRotation().getArray()))
                            .put("zone", _utmPose.origin.toString())));
        } catch (JSONException e) {
            Log.w(TAG, "Unable to serialize pose.");
        }
        sendState(_utmPose);
    }

    @Override
    public void startWaypoints(final UtmPose[] waypoints,
                               final String controller) {
        Log.i(TAG, "Starting waypoints with " + controller + ": "
                + Arrays.toString(waypoints));

        // Create a waypoint navigation task
        TimerTask newNavigationTask = new TimerTask() {
            final double dt = (double) UPDATE_INTERVAL_MS / 1000.0;

            // Retrieve the appropriate controller in initializer
            VehicleController vc = DEFAULT_CONTROLLER;

            {
                try {
                    vc = (controller == null) ? vc : AirboatController.valueOf(controller).controller;
                    //Log.i(TAG, "vc:"+ vc.toString() + "controller" + controller );
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Unknown controller specified (using " + vc
                            + " instead): " + controller);
                }
            }

            @Override
            public void run() {
                synchronized (_navigationLock) {
                    //Log.i(TAG, "Synchronized");

                    if (!_isAutonomous.get()) {
                        // If we are not autonomous, do nothing
                        Log.i(TAG, "Paused");
                        sendWaypointUpdate(WaypointState.PAUSED);
                    } else if (_waypoints.length == 0) {
                        // If we are finished with waypoints, stop in place
                        Log.i(TAG, "Done");
                        sendWaypointUpdate(WaypointState.DONE);
                        setVelocity(new Twist(DEFAULT_TWIST));
                        this.cancel();
                        _navigationTask = null;

                    } else {
                        // If we are still executing waypoints, use a
                        // controller to figure out how to get to waypoint
                        // TODO: measure dt directly instead of approximating
                        Log.i(TAG, "controller :" + controller);
                        vc.update(VehicleServerImpl.this, dt);
                        sendWaypointUpdate(WaypointState.GOING);
                        //Log.i(TAG, "Waypoint Status: POINT_AND_SHOOT");
                    }
                }
            }
        };

        synchronized (_navigationLock) {
            // Change waypoints to new set of waypoints
            _waypoints = new UtmPose[waypoints.length];
            System.arraycopy(waypoints, 0, _waypoints, 0, _waypoints.length);

            // Cancel any previous navigation tasks
            if (_navigationTask != null)
                _navigationTask.cancel();

            // Schedule this task for execution
            _navigationTask = newNavigationTask;
            _navigationTimer.scheduleAtFixedRate(_navigationTask, 0, UPDATE_INTERVAL_MS);
        }

        // Report the new waypoint in the log file.
        try {
            mLogger.info(new JSONObject()
                    .put("nav", new JSONObject()
                            .put("controller", controller)
                            .put("waypoints", new JSONArray(waypoints))));
        } catch (JSONException e) {
            Log.w(TAG, "Unable to serialize waypoints.");
        }
    }

    @Override
    public void stopWaypoints() {
        // Stop the thread that is doing the "navigation" by terminating its
        // navigation process, clear all the waypoints, and stop the vehicle.
        synchronized (_navigationLock) {
            if (_navigationTask != null) {
                _navigationTask.cancel();
                _navigationTask = null;
                _waypoints = new UtmPose[0];
                setVelocity(new Twist(DEFAULT_TWIST));
                Log.i(TAG, "StopWaypoint");
            }
        }
        sendWaypointUpdate(WaypointState.CANCELLED);
    }

    @Override
    public UtmPose[] getWaypoints() {
        UtmPose[] wpts;
        synchronized (_navigationLock) {
            wpts = new UtmPose[_waypoints.length];
            System.arraycopy(_waypoints, 0, wpts, 0, wpts.length);
        }
        return wpts;
    }

    @Override
    public WaypointState getWaypointStatus() {
        synchronized (_navigationLock) {
            if (_waypoints.length > 0) {
                return _isAutonomous.get() ? WaypointState.PAUSED
                        : WaypointState.GOING;
            } else {
                return WaypointState.DONE;
            }
        }
    }

    /**
     * Returns the current estimated 6D velocity of the vehicle.
     */
    public Twist getVelocity() {
        return _velocities.clone();
    }

    /**
     * Sets a desired 6D velocity for the vehicle.
     */
    public void setVelocity(Twist vel) {
        _velocities = vel.clone();

        // Schedule a task to shutdown the velocity if no command is received within the timeout.
        // Normally, this task will be canceled by a subsequent call to the setVelocity function,
        // but if no call is made within the timeout, the task will execute, stopping the vehicle.
        synchronized (mVelocityExecutor) {
            // Cancel the previous shutdown task.
            if (mVelocityFuture != null)
                mVelocityFuture.cancel(false);

            // Schedule a new shutdown task.
            mVelocityFuture = mVelocityExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    setVelocity(new Twist());
                }
            }, VELOCITY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean isAutonomous() {
        return _isAutonomous.get();
    }

    @Override
    public void setAutonomous(boolean isAutonomous) {
        _isAutonomous.set(isAutonomous);

        // Set velocities to zero to allow for safer transitions
        _velocities = new Twist(DEFAULT_TWIST);
    }

    /**
     * Performs cleanup functions in preparation for stopping the server.
     */
    public void shutdown() {
        stopWaypoints();
        stopCamera();

        _isAutonomous.set(false);
        _isConnected.set(false);
        _isRunning.set(false);

        _updateTimer.cancel();
        _updateTimer.purge();

        _navigationTimer.cancel();
        _navigationTimer.purge();

        _captureTimer.cancel();
        _captureTimer.purge();
    }
}
