package com.platypus.android.server;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import com.platypus.crw.data.Pose3D;
import com.platypus.crw.data.Quaternion;

/**
 * Contains the actual implementation of vehicle functionality, accessible as a
 * singleton that is updated and maintained by a background service.
 *
 * @author pkv
 * @author kss
 */
public class VehicleServerImpl extends AbstractVehicleServer
{

		public static final int UPDATE_INTERVAL_MS = 100;
		public static final int NUM_SENSORS = 5;

		////////////////////////////////////////////////////////////////////////////////////////////////
		// ASDF

		<F> F getState(String state_name)
		{
				Object result = vehicle_state.get(state_name);
				if (result == null)
				{
						Log.w("AP", String.format("state \"%s\" returned null", state_name));
						return null;
				}
				return (F)result;
		}
		<F> F getState(String state_name, int index)
		{
				Object result = vehicle_state.get(state_name, index);
				if (result == null)
				{
						Log.w("AP", String.format("state \"%s\"[%d] returned null", state_name, index));
						return null;
				}
				return (F)result;
		}
		<F> void setState(String state_name, F value) { vehicle_state.set(state_name, value); }
		<F> void setState(String state_name, F value, int index) { vehicle_state.set(state_name, index, value); }

		/*
		list of actions {* if immediately required}
		- return home {*}
		- winch column of data
		- turn on sampler (and station keep) {*}
		- turn off sampler
		- reset sampler {*}
		- station keep {*}
		- speed up
		- slow down
		- personal genomics pump
		- filter sensor data somehow
		- avoid obstacles
		 */
		enum Actions
		{
				EXAMPLE("example"),
				RETURN_HOME("return_home"),
				START_SAMPLER("start_sampler"),
				STOP_SAMPLER("sampler_stop"),
				RESET_SAMPLER("sampler_reset"),
				START_SAMPLER_TEST("start_sampler_test"),
				DO_NOTHING("do_nothing");

				final String name;

				Actions(String s)
				{
						name = s;
				}

				public static boolean contains(String s)
				{
						for (Actions action : values())
						{
								if (action.name.equals(s)) return true;
						}
						return false;
				}

				public static Actions fromString(final String s)
				{
						for (Actions action : values())
						{
								if (action.name.equals(s)) return action;
						}
						Log.w("AP", String.format("Action \"%s\" not available. No action will be performed", s));
						return DO_NOTHING;
				}
		}

		private AutonomousPredicates autonomous_predicates;
		private VehicleState vehicle_state;

		void exampleAction()
		{
				Log.i("AP", "PERFORMING EXAMPLE ACTION");
		}

		void performAction(String action_string)
		{
				Actions action = Actions.fromString(action_string);
				switch (action)
				{
						case EXAMPLE:
						{
								exampleAction();
								break;
						}
						case DO_NOTHING:
						{
								break;
						}
						case START_SAMPLER:
						{
								// First, find next available sample jar
								Object retrieval = getState(VehicleState.States.NEXT_AVAILABLE_JAR.name);
								if (retrieval == null)
								{
										Log.e("AP", "Retrieved a null after requesting next available jar");
										return;
								}
								Integer next_available_jar = (Integer) retrieval;

								if (next_available_jar < 0)
								{
										Log.w("AP", "No sampler jars are available. Ignoring START_SAMPLER command");
										return;
								}
								Log.i("AP", String.format("Starting sampler jar # %d", next_available_jar));
								JSONObject command = new JSONObject();
								JSONObject samplerSettings = new JSONObject();
								try
								{
										samplerSettings.put("e", next_available_jar.toString());
										for (int i = 1; i < 4; i++)
										{
												String sensor_array_name = "pref_sensor_" + Integer.toString(i) + "_type";
												String expected_type = mPrefs.getString(sensor_array_name, "NONE");
												if (expected_type.equals("SAMPLER"))
												{
														/*
														Log.v("AP", String.format("Before insertWaypoint: \n" +
																		"# of WPs = %d\n" +
																		"current index = %d\n" +
																		"WPs: %s", _waypoints.length, current_waypoint_index.get(), Arrays.toString(_waypoints)));
														*/

														final long SAMPLER_STATION_KEEP_TIME = 4*60*1000; // TODO: don't hardcode this
														int cwp = current_waypoint_index.get();
														UtmPose current_utmpose = getState(VehicleState.States.CURRENT_POSE.name);
														insertWaypoint((cwp > 0 ? cwp : 0), // never less than 0
																		current_utmpose.getLatLong(),
																		SAMPLER_STATION_KEEP_TIME);

														/*
														Log.v("AP", String.format("After insertWaypoint: \n" +
																		"# of WPs = %d\n" +
																		"current index = %d\n" +
																		"WPs: %s", _waypoints.length, current_waypoint_index.get(), Arrays.toString(_waypoints)));
														*/

														command.put(String.format("s%d", i), samplerSettings);

														// TODO: only call mController.send() if hardware is connected
														setState(VehicleState.States.IS_TAKING_SAMPLE.name, true);
														vehicle_state.usingJar(next_available_jar);
														mController.send(command);

														// TODO: start a time task that will run after SAMPLER_STATION_KEEP_TIME, setting is_taking_sample to false


														return;
												}
										}
								}
								catch (Exception e)
								{
										Log.e("AP", String.format("START_SAMPLER action error: %s", e.getMessage()));
								}
								break;
						}

						case RESET_SAMPLER:
						{
								vehicle_state.resetSampleJars();
								Log.i("AP", "Resetting the sampler");
								JSONObject command = new JSONObject();
								JSONObject samplerSettings = new JSONObject();
								try
								{
										samplerSettings.put("r", "");
										for (int i = 1; i < 4; i++)
										{
												String sensor_array_name = "pref_sensor_" + Integer.toString(i) + "_type";
												String expected_type = mPrefs.getString(sensor_array_name, "NONE");
												if (expected_type.equals("SAMPLER"))
												{
														command.put(String.format("s%d", i), samplerSettings);
														mController.send(command);
														return;
												}
										}
								}
								catch (Exception e)
								{
										Log.e("AP", String.format("Reset sampler action error: %s", e.getMessage()));
								}
								break;
						}

						// TODO: finish up the remaining actions
						case RETURN_HOME:
						{
								Log.w("AP", "RETURNING HOME");
								break;
						}

						case START_SAMPLER_TEST:
						{
								Log.i("AP", "Starting the forced sampler test");
								// ASDF
								// call startWaypoints
								// force the server to think it is at one of the waypoints
								// see if the sampler starts, a new waypoint is inserted, and station keeping starts
								/*
								UtmPose[] example_waypoints =
												{
														new UtmPose(new Pose3D(656471.32, 5029766.27, 0, 0, 0, 0), new Utm(32, true)),
														new UtmPose(new Pose3D(656480., 5029766, 0, 0, 0, 0), new Utm(32, true)),
														new UtmPose(new Pose3D(656490., 5029766, 0, 0, 0, 0), new Utm(32, true))
												};
								setAutonomous(true);
								startWaypoints(example_waypoints, "whatever");
								current_waypoint_index.set(2);
								*/
								break;
						}

						default:
								break;
				}
		}
		////////////////////////////////////////////////////////////////////////////////////////////////

		/**
		 * Defines the PID gains that will be returned if there is an error.
		 */
		public static final double[] NAN_GAINS =
						new double[]{Double.NaN, Double.NaN, Double.NaN};
		public static final double[] DEFAULT_TWIST = {0, 0, 0, 0, 0, 0};
		public static final double SAFE_DIFFERENTIAL_THRUST = 1.0;
		public static final double SAFE_VECTORED_THRUST = 1.0;
		public static final long VELOCITY_TIMEOUT_MS = 10000;
		private static final String TAG = "VehicleServerImpl"; //VehicleServerImpl.class.getName();
		protected final SharedPreferences mPrefs;
		protected final SensorType[] _sensorTypes = new SensorType[NUM_SENSORS];
		protected final Object _captureLock = new Object();
		protected final Object _navigationLock = new Object();
		protected final Object _waypointLock = new Object();
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
		double[][] _waypoints = new double[0][0];
		Long[] _waypointsKeepTimes = new Long[0];

		AtomicInteger current_waypoint_index = new AtomicInteger(-1);

		public int getCurrentWaypointIndex()
		{
				return current_waypoint_index.get();
		}

		public void incrementWaypointIndex()
		{
				current_waypoint_index.incrementAndGet();
				Log.i(TAG, String.format("New waypoint index = %d", current_waypoint_index.get()));
		}

		public double[] getCurrentWaypoint()
		{
				synchronized (_waypointLock)
				{
						if (current_waypoint_index.get() >= 0)
						{
								return _waypoints[current_waypoint_index.get()];
						}
						else
						{
								return null;
						}
				}
		}

		public double[] getSpecificWaypoint(int i)
		{
				synchronized (_waypointLock)
				{
						if (i >= 0 && i < _waypoints.length)
						{
								return _waypoints[i];
						}
						else
						{
								return null;
						}
				}
		}

		public Long getCurrentWaypointKeepTime()
		{
				synchronized (_waypointLock)
				{
						if (current_waypoint_index.get() >= 0)
						{
								return _waypointsKeepTimes[current_waypoint_index.get()];
						}
						else
						{
								return null;
						}
				}
		}

		public Long getSpecificWaypointKeepTime(int i)
		{
				synchronized (_waypointLock)
				{
						if (i >= 0 && i < _waypointsKeepTimes.length)
						{
								return _waypointsKeepTimes[i];
						}
						else
						{
								return null;
						}
				}
		}



		protected TimerTask _captureTask = null;
		protected TimerTask _navigationTask = null;
		ScheduledFuture mVelocityFuture = null;

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

		//Define Notification Manager
		NotificationManager notificationManager;
		//Define sound URI
		Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

		public UTM UtmPose_to_UTM(UtmPose utmPose)
		{
				return UTM.valueOf(
								utmPose.origin.zone,
								utmPose.origin.isNorth ? 'T' : 'L',
								utmPose.pose.getX(),
								utmPose.pose.getY(),
								SI.METER
				);
		}

		public LatLong UtmPose_to_LatLng(UtmPose utmPose)
		{
				return UTM.utmToLatLong(UtmPose_to_UTM(utmPose), ReferenceEllipsoid.WGS84);
		}

		public UtmPose UTM_to_UtmPose(UTM utm)
		{
				if (utm == null) return null;
				Pose3D pose = new Pose3D(utm.eastingValue(SI.METER),
								utm.northingValue(SI.METER),
								0.0,
								Quaternion.fromEulerAngles(0, 0, 0));
				Utm origin = new Utm(utm.longitudeZone(),
								utm.latitudeZone() > 'O');
				return new UtmPose(pose, origin);
		}

		public UtmPose LatLng_to_UtmPose(LatLong latlong)
		{
				// Convert from lat/long to UTM coordinates
				UTM utm = UTM.latLongToUtm(latlong, ReferenceEllipsoid.WGS84);
				return UTM_to_UtmPose(utm);
		}

		boolean[] received_expected_sensor_type = {false, false, false};

		public void reset_expected_sensors()
		{
				for (int i = 0; i < 3; i++)
				{
						received_expected_sensor_type[i] = false;
				}
		}

		private final Timer _sensorTypeTimer = new Timer();
		private TimerTask expect_sensor_type_task = new TimerTask()
		{
				@Override
				public void run()
				{
						for (int i = 0; i < 3; i++)
						{
								try
								{
										Thread.sleep(1000); // sleep for all sensor slots, even if empty
								}
								catch (InterruptedException ex)
								{
										Thread.currentThread().interrupt();
								}
								if (!received_expected_sensor_type[i])
								{
										String sensor_array_name = "pref_sensor_" + Integer.toString(i + 1) + "_type";
										String expected_type = mPrefs.getString(sensor_array_name, "NONE");
										if (expected_type.equals("NONE")
														|| expected_type.equals("RC_SBUS")
														|| expected_type.equals("HDS")
														|| expected_type.equals("SAMPLER"))
										{
												continue;
										}
										String message = "s" + (i + 1) + " expects " + expected_type + " not received yet";
										Log.w(TAG, message);
										NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(_context)
														.setSmallIcon(R.drawable.camera_icon) //just some random icon placeholder
														.setContentTitle("Sensor Warning")
														.setContentText(message)
														.setSound(soundUri); //This sets the sound to play
										notificationManager.notify(0, mBuilder.build());
								}
						}
				}
		};

		double[] scaleDown(double[] raw_signals)
		{
        /*ASDF*/
				double[] scaled_signals = raw_signals.clone();
				boolean needs_scaling = false;
				double max_signal = 0.0;
				for (double signal : raw_signals)
				{
						if (Math.abs(signal) > 1.0)
						{
								needs_scaling = true;
						}
						if (Math.abs(signal) > max_signal)
						{
								max_signal = Math.abs(signal);
						}
				}
				if (needs_scaling)
				{
						for (int i = 0; i < raw_signals.length; i++)
						{
								scaled_signals[i] = raw_signals[i] / max_signal;
						}
				}
				return scaled_signals;
		}

		/**
		 * Internal update function called at regular intervals to process command
		 * and control events.
		 */
		private TimerTask _updateTask = new TimerTask()
		{

				@Override
				public void run()
				{
						UtmPose pose = filter.pose(System.currentTimeMillis());
						setState(VehicleState.States.CURRENT_POSE.name, pose);
						try
						{
								mLogger.info(new JSONObject()
												.put("pose", new JSONObject()
																.put("p", new JSONArray(pose.pose.getPosition()))
																.put("q", new JSONArray(pose.pose.getRotation().getArray()))
																.put("zone", pose.origin.toString())));
						}
						catch (JSONException e)
						{
								Log.w(TAG, "Unable to serialize pose.");
						}
						sendState(pose);

						// Send vehicle command by converting raw command to appropriate vehicle model.
						JSONObject command = new JSONObject();
						String vehicleType = mPrefs.getString("pref_vehicle_type",
										_context.getResources().getString(R.string.pref_vehicle_type_default));
						switch (vehicleType)
						{
								case "DIFFERENTIAL":
								{
										// Construct objects to hold velocities
										JSONObject velocity0 = new JSONObject();
										JSONObject velocity1 = new JSONObject();

										// Send velocities as a JSON command
										try
										{
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
										}
										catch (JSONException e)
										{
												Log.w(TAG, "Failed to serialize command.", e);
										}
										catch (IOException e)
										{
												Log.w(TAG, "Failed to send command.", e);
										}
								}
								break;

								case "VECTORED":
								{
										// Construct objects to hold velocities
										JSONObject thrust = new JSONObject();
										JSONObject rudder = new JSONObject();

										// Send velocities as a JSON command
										try
										{
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
										}
										catch (JSONException e)
										{
												Log.w(TAG, "Failed to serialize command.", e);
										}
										catch (IOException e)
										{
												Log.w(TAG, "Failed to send command.", e);
										}
								}
								break;

								case "PROPGUARD":
								{
										// Construct objects to hold velocities
										JSONObject velocity0 = new JSONObject();
										JSONObject velocity1 = new JSONObject();

										// Send velocities as a JSON command
										try
										{
                        /*ASDF*/
												// to start out, I will *not* include the negative thrust bias
												// instead, i'll just have it just set thrust to zero while error is > 45 degrees

												// _velocities.dx() --> thrust effort fraction
												// _velocities.drz() --> heading effort fraction

												// try using the integral gain for thrust as the scale between positive and negative thrust
												double[] thrust_pids = getGains(0);
												if (thrust_pids[1] == 0)
												{
														thrust_pids[1] = 5.;
												}
												double T = _velocities.dx();
												double H = _velocities.drz();
												// bias thrust backwards according to heading
												// T -= 0.5*H;

												//double[] rawV = {_velocities.dx() - _velocities.drz(),
												//        _velocities.dx() + _velocities.drz()};
												double[] rawV = {T - H, T + H};

												double[] constrainedV = scaleDown(rawV);
												double constrainedV0 = constrainedV[0];
												double constrainedV1 = constrainedV[1];

												// need to account for prop guard, reduce positive motor signals if turning in place
												if (Math.signum(constrainedV0) > 0 && Math.signum(constrainedV1) < 0)
												{
														constrainedV0 = constrainedV0 / (thrust_pids[1]);
												}
												if (Math.signum(constrainedV0) < 0 && Math.signum(constrainedV1) > 0)
												{
														constrainedV1 = constrainedV1 / (thrust_pids[1]);
												}

												// Until ESC reboot is fixed, set the upper limit to SAFE_THRUST
                        /*
                        constrainedV0 = map(constrainedV0,
                                -1.0, 1.0, // Original range.
                                -VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST, VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST); // New range.
                        constrainedV1 = map(constrainedV1,
                                -1.0, 1.0, // Original range.
                                -VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST, VehicleServerImpl.SAFE_DIFFERENTIAL_THRUST); // New range.
                        */

												velocity0.put("v", (float) constrainedV0);
												velocity1.put("v", (float) constrainedV1);

												command.put("m0", velocity0);
												command.put("m1", velocity1);

												// Send and log the transmitted command.
												if (mController.isConnected())
														mController.send(command);
												mLogger.info(new JSONObject().put("cmd", command));
										}
										catch (JSONException e)
										{
												Log.w(TAG, "Failed to serialize command.", e);
										}
										catch (IOException e)
										{
												Log.w(TAG, "Failed to send command.", e);
										}
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

		protected VehicleServerImpl(Context context, VehicleLogger logger, Controller controller)
		{
				_context = context;
				mLogger = logger;
				mController = controller;

				// Connect to the Shared Preferences for this process.
				mPrefs = PreferenceManager.getDefaultSharedPreferences(_context);

				notificationManager = (NotificationManager) _context.getSystemService(Context.NOTIFICATION_SERVICE);
				_sensorTypeTimer.scheduleAtFixedRate(expect_sensor_type_task, 0, 100);
				//_failsafe_timer.scheduleAtFixedRate(failsafe_check, 0, 10000);
				vehicle_state = new VehicleState(this);
				autonomous_predicates = new AutonomousPredicates(this);
				autonomous_predicates.loadDefaults();


				// Load PID values from SharedPreferences.
				// Use hard-coded defaults if not specified.
				r_PID[0] = mPrefs.getFloat("gain_rP", 0.7f);
				r_PID[1] = mPrefs.getFloat("gain_rI", 0.0f);
				r_PID[2] = mPrefs.getFloat("gain_rD", 0.5f);

				t_PID[0] = mPrefs.getFloat("gain_tP", 0.5f);
				t_PID[1] = mPrefs.getFloat("gain_tI", 0.0f);
				t_PID[2] = mPrefs.getFloat("gain_tD", 0.0f);

				// Start a regular update function
				_updateTimer.scheduleAtFixedRate(_updateTask, 0, UPDATE_INTERVAL_MS);

				// Create a thread to read data from the controller board.
				Thread receiveThread = new Thread(new Runnable()
				{
						@Override
						public void run()
						{
								// Start a loop to receive data from accessory.
								//while (_isRunning.get())
								while (getState(VehicleState.States.IS_RUNNING.name))
								{
										try
										{
												onCommand(mController.receive());
										}
										catch (Controller.ConnectionException e)
										{
												// Do nothing, we don't need to detect this here.
										}
										catch (IOException | Controller.ControllerException e)
										{
												Log.w(TAG, e);
										}
										finally
										{
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
		public static double clip(double input, double min, double max)
		{
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
		                         double output_min, double output_max)
		{
				return (input - input_min) / (input_max - input_min)
								* (output_max - output_min) + output_min;
		}

		/**
		 * @see VehicleServer#getGains(int)
		 */
		@Override
		public double[] getGains(int axis)
		{

				if (axis == 5)
						return r_PID.clone();
				else if (axis == 0)
						return t_PID.clone();
				else if (axis == 3)
						return new double[]{winch_depth_, 0.0, 0.0};
				else
						return NAN_GAINS;
		}

		@Override
		public void setHome(double[] new_home)
		{
				setState(VehicleState.States.HOME_POSE.name, new UtmPose(new_home));
		}

		@Override
		public double[] getHome()
		{
				UtmPose utmPose = getState(VehicleState.States.HOME_POSE.name);
				return utmPose.getLatLong();
		}

		@Override
		public void startGoHome()
		{
				/*
				if (home_UTM == null)
				{
						Log.e(TAG, "Cannot trigger failsafe, home is null");
				}
				is_executing_failsafe.set(true);
				// need to execute a single start waypoints command
				// need current position and home position
				// START the go home action
				UTM current_location = UTM.valueOf(
								_utmPose.origin.zone,
								_utmPose.origin.isNorth ? 'T' : 'L',
								_utmPose.pose.getX(),
								_utmPose.pose.getY(),
								SI.METER);

				/////////////////////////////////////////////
				// List<Long> path_crumb_indices = Crumb.aStar(current_location, home_UTM);
				List<Long> path_crumb_indices = Crumb.straightHome(current_location, home_UTM);
				/////////////////////////////////////////////

				UtmPose[] path_waypoints = new UtmPose[path_crumb_indices.size()];
				int wp_index = 0;
				for (long index : path_crumb_indices)
				{
						UTM wp = Crumb.crumbs_by_index.get(index).getLocation();
						path_waypoints[wp_index] = UTM_to_UtmPose(wp);
						wp_index++;
				}
				startWaypoints(path_waypoints);
				*/
		}

		/**
		 * @see VehicleServer#setGains(int, double[])
		 */
		@Override
		public void setGains(int axis, double[] k)
		{
				// TODO: Get rid of this, it is a hack.
				// Special case to handle winch commands...
				if (axis == 3)
				{
						JSONObject command = new JSONObject();
						JSONObject winchSettings = new JSONObject();

						// Call command to adjust winch
						try
						{
								//Set desired winch movement distance
								winchSettings.put("p", (float) Math.abs(k[0]));

								//Hardcoded velocity - get rid of this eventually
								winchSettings.put("v", 500 * Math.signum(k[0]));
								command.put("s2", winchSettings);

								mController.send(command);
								mLogger.info(new JSONObject().put("winch", command));
						}
						catch (JSONException e)
						{
								Log.w(TAG, "Unable to construct JSON string from winch command: " + Arrays.toString(k));
						}
						catch (IOException e)
						{
								Log.w(TAG, "Unable to send winch command.", e);
						}
						return;
				}
				else if (axis == 5)
				{
						r_PID = k.clone();

						// Save the PID values to the SharedPreferences as well.
						mPrefs.edit()
										.putFloat("gain_rP", (float) r_PID[0])
										.putFloat("gain_rI", (float) r_PID[1])
										.putFloat("gain_rD", (float) r_PID[2])
										.apply();
				}
				else if (axis == 0)
				{
						t_PID = k.clone();

						// Save the PID values to the SharedPreferences as well.
						mPrefs.edit()
										.putFloat("gain_tP", (float) t_PID[0])
										.putFloat("gain_tI", (float) t_PID[1])
										.putFloat("gain_tD", (float) t_PID[2])
										.apply();
				}

				// Log the new gain settings to the logfile.
				try
				{
						mLogger.info(new JSONObject()
										.put("gain", new JSONObject()
														.put("axis", axis)
														.put("values", Arrays.toString(k))));
				}
				catch (JSONException e)
				{
						Log.w(TAG, "Failed to serialize gains.");
				}
		}

		/**
		 * Returns the current gyro readings
		 */
		public double[] getGyro()
		{
				return _gyroPhone.clone();
		}

		public void setPhoneGyro(float[] gyroValues)
		{
				for (int i = 0; i < gyroValues.length; i++)
						_gyroPhone[i] = (double) gyroValues[i];
		}

		/**
		 * @see com.platypus.crw.VehicleServer#isConnected()
		 */
		public boolean isConnected()
		{
				return mController.isConnected();
		}

		/**
		 * Handles complete Arduino commands, once they are reassembled.
		 *
		 * @param cmd the list of arguments composing a command
		 */
		protected void onCommand(JSONObject cmd)
		{

				@SuppressWarnings("unchecked")
				Iterator<String> keyIterator = cmd.keys();

				// Iterate through JSON fields
				while (keyIterator.hasNext())
				{
						String name = keyIterator.next();
						try
						{
								JSONObject value = cmd.getJSONObject(name);
								if (name.startsWith("m"))
								{
										int motor = name.charAt(1) - 48;
								}
								else if (name.startsWith("s"))
								{
										int sensor = name.charAt(1) - 48;

										// check sensor type expected in the preferences
										String sensor_array_name = "pref_sensor_" + Integer.toString(sensor) + "_type";
										String expected_type = mPrefs.getString(sensor_array_name, "NONE");

										// Hacks to send sensor information
										if (value.has("type"))
										{
												String type = value.getString("type");

												// check if received type matches expected type
												if (!type.equalsIgnoreCase("battery"))
												{
														if (type.equalsIgnoreCase(expected_type))
														{
																received_expected_sensor_type[sensor - 1] = true;
                                /*
                                String message = "s" + sensor + ": expected = " + expected_type + " received = " + type;
                                Log.w(TAG, message);
                                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(_context)
                                        .setSmallIcon(R.drawable.camera_icon) //just some random icon placeholder
                                        .setContentTitle("Sensor Success")
                                        .setContentText(message)
                                        .setSound(soundUri); //This sets the sound to play
                                notificationManager.notify(0, mBuilder.build());
                                */
														}
														else
														{
																String message = "s" + sensor + ": expected = " + expected_type + " received = " + type;
																Log.w(TAG, message);
																NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(_context)
																				.setSmallIcon(R.drawable.camera_icon) //just some random icon placeholder
																				.setContentTitle("Sensor Warning")
																				.setContentText(message)
																				.setSound(soundUri); //This sets the sound to play
																notificationManager.notify(0, mBuilder.build());
														}
												}

												SensorData reading = new SensorData();

												if (type.equalsIgnoreCase("es2"))
												{
														try
														{
																// Parse out temperature and ec values
																String[] data = value.getString("data").trim().split(" ");
																double ecData = Double.parseDouble(data[0]);
																double tempData = Double.parseDouble(data[1]);

																// Todo: update stored temp and ec values then push to DO/pH probes
																// Fill in readings from parsed sensor data.
																reading.channel = sensor;
																reading.type = SensorType.ES2;
																reading.data = new double[]{ecData, tempData};
																setState(VehicleState.States.EC.name, ecData);
																setState(VehicleState.States.T.name, tempData);
														}
														catch (NumberFormatException e)
														{
																Log.w(TAG, "Received malformed ES2 Sensor Data: " + value);
																continue;
														}
												}
												else if (type.equalsIgnoreCase("atlas_do"))
												{
														// Fill in readings from parsed sensor data.
														reading.channel = sensor;
														reading.type = SensorType.ATLAS_DO;
														reading.data = new double[]{value.getDouble("data")};
														setState(VehicleState.States.DO.name, reading.data[0]);
												}
												else if (type.equalsIgnoreCase("atlas_ph"))
												{
														// Fill in readings from parsed sensor data.
														reading.channel = sensor;
														reading.type = SensorType.ATLAS_PH;
														reading.data = new double[]{value.getDouble("data")};
														setState(VehicleState.States.PH.name, reading.data[0]);
												}
												else if (type.equalsIgnoreCase("hds"))
												{
														String nmea = value.getString("data");
														if (nmea.startsWith("$SDDBT"))
														{ //Depth Below Transducer
																try
																{
																		double depth = Double.parseDouble(nmea.split(",")[3]);

																		// Fill in readings from parsed sensor data.
																		reading.type = SensorType.HDS_DEPTH;
																		reading.channel = sensor;
																		reading.data = new double[]{depth};
																		setState(VehicleState.States.WATER_DEPTH.name, reading.data[0]);
																}
																catch (Exception e)
																{
																		Log.w(TAG, "Failed to parse depth reading: " + nmea);
																		continue;
																}
														}
														else if (nmea.startsWith("$SDMTW"))
														{ //Water Temperature
																try
																{
																		double temp = Double.parseDouble((nmea.split(",")[1]));

																		reading.type = SensorType.HDS_TEMP;
																		reading.channel = sensor;
																		reading.data = new double[]{temp};
																}
																catch (Exception e)
																{
																		Log.w(TAG, "Failed to parse temperature reading: " + nmea);
																		continue;
																}
														}
														else if (nmea.startsWith("$SDRMC"))
														{ //GPS
																continue;
														}
														else
														{
																Log.w(TAG, "Unknown NMEA String: " + nmea);
																continue;
														}
												}
												else if (type.equalsIgnoreCase("battery"))
												{
														try
														{
																// Parse out voltage and motor velocity values
																String[] data = value.getString("data").trim().split(" ");
																double voltage = Double.parseDouble(data[0]);
																double motor0Velocity = Double.parseDouble(data[1]);
																double motor1Velocity = Double.parseDouble(data[2]);

																// Fill in readings from parsed sensor data.
																reading.channel = sensor;
																reading.type = SensorType.BATTERY;
																reading.data = new double[]{voltage, motor0Velocity, motor1Velocity};

																setState(VehicleState.States.BATTERY_VOLTAGE.name, voltage);
														}
														catch (NumberFormatException e)
														{
																Log.w(TAG, "Received malformed Battery Sensor Data: " + value);
														}
												}
												else if (type.equalsIgnoreCase("winch"))
												{
														// Fill in readings from parsed sensor data.
														reading.channel = sensor;
														reading.type = SensorType.UNKNOWN;
														reading.data = new double[]{value.getDouble("depth")};

														// TODO: Remove this hack to store winch depth
														winch_depth_ = reading.data[0];

												}
												else if (type.equalsIgnoreCase("bluebox"))
												{
														// need to log sensor types that don't appear in the core library enum
														mLogger.info(value);
														continue;
												}
												else
												{ // unrecognized sensor type
														Log.w(TAG, "Received data from sensor of unknown type: " + type);
														continue;
												}

												mLogger.info(new JSONObject()
																.put("sensor", new JSONObject()
																				.put("channel", reading.channel)
																				.put("type", reading.type.toString())
																				.put("data", new JSONArray(reading.data))));

												// Send out the collected sensor reading
												sendSensor(sensor, reading);
										}
								}
								else if (name.startsWith("g"))
								{
										int gpsReceiver = name.charAt(1) - 48;
										double latitude = -999.;
										double longitude = -999.;
										long time_ = 0;
										if (value.has("lat"))
										{
												latitude = value.getDouble("lat");
										}
										else
										{
												continue;
										}
										if (value.has("lon"))
										{
												longitude = value.getDouble("lon");
										}
										else
										{
												continue;
										}
										if (value.has("time"))
										{
												time_ = value.getLong("time");
										}
										else
										{
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
								}
								else
								{
										Log.w(TAG, "Received unknown param '" + cmd + "'.");
								}
						}
						catch (JSONException e)
						{
								Log.w(TAG, "Malformed JSON command '" + cmd + "'.", e);
						}
				}
		}

		// TODO: Revert capture image to take images
		// This is a hack to support the water sampler until PID is working again.
		public synchronized byte[] captureImage(int width, int height)
		{
				// Call command to fire sampler
				try
				{
						JSONObject samplerCommand = new JSONObject()
										.put("s0", new JSONObject()
														.put("sample", true));
						mController.send(samplerCommand);
						mLogger.info(new JSONObject().put("sampler", true));
						Log.i(TAG, "Triggering sampler.");
				}
				catch (JSONException e)
				{
						Log.w(TAG, "Unable to serialize sampler command.");
				}
				catch (IOException e)
				{
						Log.w(TAG, "Unable to send sampler command.");
				}
				return new byte[1];
		}

		public synchronized byte[] captureImageInternal(int width, int height)
		{
				byte[] bytes = AirboatCameraActivity.takePhoto(_context, width, height);
				Log.i(TAG, "Sending image [" + bytes.length + "]");
				return bytes;
		}

		public synchronized boolean saveImage()
		{
				AirboatCameraActivity.savePhoto(_context);
				Log.i(TAG, "Saving image.");
				return true;
		}

		@Override
		public void startCamera(final int numFrames, final double interval,
		                        final int width, final int height)
		{
				Log.i(TAG, "Starting capture: " + numFrames + "(" + width + "x"
								+ height + ") frames @ " + interval + "s");

				// Create a camera capture task
				TimerTask newCaptureTask = new TimerTask()
				{
						int iFrame = 0;

						@Override
						public void run()
						{
								synchronized (_captureLock)
								{
										// Take a new image and send it out
										sendImage(captureImageInternal(width, height));
										iFrame++;

										// If we exceed numFrames, we finished
										if (numFrames > 0 && iFrame >= numFrames)
										{
												sendCameraUpdate(CameraState.DONE);
												this.cancel();
												_captureTask = null;
										}
										else
										{
												sendCameraUpdate(CameraState.CAPTURING);
										}
								}
						}
				};

				synchronized (_captureLock)
				{
						// Cancel any previous capture tasks
						if (_captureTask != null)
								_captureTask.cancel();

						// Schedule this task for execution
						_captureTask = newCaptureTask;
						_captureTimer.scheduleAtFixedRate(_captureTask, 0,
										(long) (interval * 1000.0));
				}

				// Report the new imaging job in the log file
				try
				{
						mLogger.info(new JSONObject()
										.put("img", new JSONObject()
														.put("num", numFrames)
														.put("interval", interval)
														.put("w", width)
														.put("h", height)));
				}
				catch (JSONException e)
				{
						Log.w(TAG, "Unable to serialize image properties.", e);
				}
		}

		@Override
		public void stopCamera()
		{
				// Stop the thread that sends out images by terminating its
				// navigation flag and then removing the reference to the old flag.
				synchronized (_captureLock)
				{
						if (_captureTask != null)
						{
								_captureTask.cancel();
								_captureTask = null;
						}
				}
				sendCameraUpdate(CameraState.CANCELLED);
		}

		@Override
		public CameraState getCameraStatus()
		{
				synchronized (_captureLock)
				{
						if (_captureTask != null)
						{
								return CameraState.CAPTURING;
						}
						else
						{
								return CameraState.OFF;
						}
				}
		}

		@Override
		public SensorType getSensorType(int channel)
		{
				return _sensorTypes[channel];
		}

		@Override
		public void setSensorType(int channel, SensorType type)
		{
				_sensorTypes[channel] = type;
		}

		@Override
		public int getNumSensors()
		{
				return NUM_SENSORS;
		}

		@Override
		public UtmPose getPose()
		{
				//return _utmPose;
				return getState(VehicleState.States.CURRENT_POSE.name);
		}

		/**
		 * Takes a 6D vehicle pose, does appropriate internal computation to change
		 * the current estimate of vehicle state to match the specified pose. Used
		 * for user- or multirobot- pose corrections.
		 *
		 * @param pose the corrected 6D pose of the vehicle: [x,y,z,roll,pitch,yaw]
		 */
		@Override
		public void setPose(UtmPose pose)
		{
				// Change the offset of this vehicle by modifying filter
				filter.reset(pose, System.currentTimeMillis());

				// Copy this pose over the existing value
				setState(VehicleState.States.CURRENT_POSE.name, pose);

				// Report the new pose in the log file and to listeners.
				try
				{
						mLogger.info(new JSONObject()
										.put("pose", new JSONObject()
														.put("p", new JSONArray(pose.pose.getPosition()))
														.put("q", new JSONArray(pose.pose.getRotation().getArray()))
														.put("zone", pose.origin.toString())));
				}
				catch (JSONException e)
				{
						Log.w(TAG, "Unable to serialize pose.");
				}
				sendState(pose);
		}

		void insertWaypoint(int inserted_index, double[] waypoint, long station_keep_time)
		{
				synchronized (_waypointLock)
				{
						if (inserted_index < 0)
						{
								Log.w("AP", "insertWaypoint(): inserted_index is less than 0. Setting to 0.");
								inserted_index = 0;
						}
						ArrayList<double[]> waypoint_list = new ArrayList<>();
						ArrayList<Long> times_list = new ArrayList<>();
						waypoint_list.addAll(Arrays.asList(_waypoints));
						times_list.addAll(Arrays.asList(_waypointsKeepTimes));
						waypoint_list.add(inserted_index, waypoint);
						times_list.add(inserted_index, station_keep_time);
						// start the new batch of waypoints, but begin at the inserted waypoint
						setAutonomous(true);
						startWaypoints(waypoint_list.toArray(new double[0][0]));
						_waypointsKeepTimes = times_list.toArray(new Long[0]);
						current_waypoint_index.set(inserted_index);
				}
		}

		@Override
		public void startWaypoints(final double[][] waypoints)
		{
				setState(VehicleState.States.TIME_SINCE_OPERATOR.name, null);
				Log.i(TAG, "Starting waypoints...");
				StringBuilder waypoints_printout = new StringBuilder("Latitude    Longitude\n");
				for (double[] waypoint : waypoints)
				{
						waypoints_printout.append(String.format("%.6f    %.6f\n", waypoint[0], waypoint[1]));
				}
				Log.d(TAG, waypoints_printout.toString());


				synchronized (_waypointLock)
				{
						if (waypoints.length > 0)
						{
								current_waypoint_index.set(0);
						}
						_waypoints = waypoints.clone();
						_waypointsKeepTimes = new Long[_waypoints.length];
						for (int i = 0; i < _waypointsKeepTimes.length; i++)
						{
								_waypointsKeepTimes[i] = 0L; // assume all keep times are zero
						}
				}

				// Create a waypoint navigation task
				TimerTask newNavigationTask = new TimerTask()
				{
						final double dt = (double) UPDATE_INTERVAL_MS / 1000.0;

						LineFollowController lf = new LineFollowController();
						VehicleController vc = (VehicleController) lf;

						@Override
						public void run()
						{
								int wp_index = current_waypoint_index.get();
								//if (!_isAutonomous.get())
								if (!(Boolean)getState(VehicleState.States.IS_AUTONOMOUS.name))
								{
										// If we are not autonomous, do nothing
										Log.d(TAG, "Paused");
										sendWaypointUpdate(WaypointState.PAUSED);
								}
								else if (wp_index == _waypoints.length)
								{
										// finished
										current_waypoint_index.set(-1);
										Log.i(TAG, "Done");
										sendWaypointUpdate(WaypointState.DONE);
										synchronized (_navigationLock)
										{
												setVelocity(new Twist(DEFAULT_TWIST));
												this.cancel();
												_navigationTask = null;
										}
								}
								else
								{
										// TODO: measure dt directly instead of approximating
										Log.v(TAG, "controller update()");
										vc.update(VehicleServerImpl.this, dt);
										sendWaypointUpdate(WaypointState.GOING);
								}
						}
				};

				synchronized (_navigationLock)
				{
						// Cancel any previous navigation tasks
						if (_navigationTask != null) _navigationTask.cancel();

						// Schedule this task for execution
						_navigationTask = newNavigationTask;
						_navigationTimer.scheduleAtFixedRate(_navigationTask, 0, UPDATE_INTERVAL_MS);
				}

				// Report the new waypoint in the log file.
				try
				{
						mLogger.info(new JSONObject()
										.put("nav", new JSONObject()
														.put("waypoints", new JSONArray(waypoints))));
				}
				catch (JSONException e)
				{
						Log.w(TAG, "Unable to serialize waypoints.");
				}
		}

		@Override
		public void stopWaypoints()
		{
				setState(VehicleState.States.TIME_SINCE_OPERATOR.name, null);
				// Stop the thread that is doing the "navigation" by terminating its
				// navigation process, clear all the waypoints, and stop the vehicle.
				synchronized (_navigationLock)
				{
						if (_navigationTask != null)
						{
								_navigationTask.cancel();
								_navigationTask = null;
								setVelocity(new Twist(DEFAULT_TWIST));
								Log.i(TAG, "StopWaypoint");
						}
				}
				synchronized (_waypointLock)
				{
						_waypoints = new double[0][0];
						current_waypoint_index.set(-1);
				}
				sendWaypointUpdate(WaypointState.CANCELLED);
		}

		@Override
		public double[][] getWaypoints()
		{
				synchronized (_waypointLock)
				{
						return _waypoints.clone();
				}
		}

		@Override
		public WaypointState getWaypointStatus()
		{
				synchronized (_waypointLock)
				{
						if (_waypoints.length > 0)
						{
								//return _isAutonomous.get() ? WaypointState.PAUSED
								return getState(VehicleState.States.IS_AUTONOMOUS.name) ? WaypointState.PAUSED
												: WaypointState.GOING;
						}
						else
						{
								return WaypointState.DONE;
						}
				}
		}


		@Override
		public int getWaypointsIndex()
		{
				setState(VehicleState.States.TIME_SINCE_OPERATOR.name, null);
				Log.i(TAG, String.format("Current waypoint index = %d", current_waypoint_index.get()));
				return current_waypoint_index.get();
		}


		/**
		 * Returns the current estimated 6D velocity of the vehicle.
		 */
		public Twist getVelocity()
		{
				return _velocities.clone();
		}

		/**
		 * Sets a desired 6D velocity for the vehicle.
		 */
		public void setVelocity(Twist vel)
		{
				setState(VehicleState.States.TIME_SINCE_OPERATOR.name, null);
				_velocities = vel.clone();

				// Schedule a task to shutdown the velocity if no command is received within the timeout.
				// Normally, this task will be canceled by a subsequent call to the setVelocity function,
				// but if no call is made within the timeout, the task will execute, stopping the vehicle.
				synchronized (mVelocityExecutor)
				{
						// Cancel the previous shutdown task.
						if (mVelocityFuture != null)
								mVelocityFuture.cancel(false);

						// Schedule a new shutdown task.
						mVelocityFuture = mVelocityExecutor.schedule(new Runnable()
						{
								@Override
								public void run()
								{
										setVelocity(new Twist());
								}
						}, VELOCITY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				}
		}

		@Override
		public boolean isAutonomous()
		{
				return getState(VehicleState.States.IS_AUTONOMOUS.name);
		}

		@Override
		public void setAutonomous(boolean isAutonomous)
		{
				setState(VehicleState.States.TIME_SINCE_OPERATOR.name, null);
				//_isAutonomous.set(isAutonomous);
				setState(VehicleState.States.IS_AUTONOMOUS.name, isAutonomous);
				//if (isAutonomous && first_autonomy.get())
				if (isAutonomous && !(Boolean) getState(VehicleState.States.HAS_FIRST_AUTONOMY.name))
				{
						Log.i("AP", "Setting HAS_FIRST_AUTONOMY to true");
						//first_autonomy.set(false);
						setState(VehicleState.States.HAS_FIRST_AUTONOMY.name, true);
						//home_UTM = UtmPose_to_UTM(_utmPose);
						setState(VehicleState.States.HOME_POSE.name, getState(VehicleState.States.CURRENT_POSE.name));
				}

				// Set velocities to zero to allow for safer transitions
				_velocities = new Twist(DEFAULT_TWIST);
		}

		/**
		 * Performs cleanup functions in preparation for stopping the server.
		 */
		public void shutdown()
		{
				stopWaypoints();
				stopCamera();
				autonomous_predicates.cancelAll();

				setState(VehicleState.States.IS_AUTONOMOUS.name, false);
				setState(VehicleState.States.IS_CONNECTED.name, false);
				setState(VehicleState.States.IS_RUNNING.name, false);

				_updateTimer.cancel();
				_updateTimer.purge();

				_navigationTimer.cancel();
				_navigationTimer.purge();

				_captureTimer.cancel();
				_captureTimer.purge();
		}
}
