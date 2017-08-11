package com.platypus.android.server;

import android.util.Log;

import com.platypus.crw.data.UtmPose;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by jason on 8/11/17.
 */

public class VehicleState
{
		VehicleServerImpl _serverImpl;
		static String logTag = "AP";

		AtomicBoolean example_state = new AtomicBoolean(true);

		boolean[] jar_available = {true, true, true, true};

		AtomicLong elapsed_time = new AtomicLong(0);
		AtomicLong time_since_operator = new AtomicLong(0);
		AtomicLong battery_voltage = new AtomicLong(0); // mV

		double example_value = 0.0;
		double EC = 0.0;
		double T = 0.0;
		double DO = 0.0;

		UtmPose currentUtmPose;
		UtmPose homeUtmPose;

		public Object get(String state_string)
		{
				try
				{
						States state = States.fromString(state_string);
						return get(state);
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
						return null;
				}
		}
		public Object get(States state)
		{
				try
				{
						return getters.get(state).get();
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
						return null;
				}
		}
		public void set(String state_string, Object in)
		{
				try
				{
						States state = States.fromString(state_string);
						set(state, in);
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
				}
		}
		public void set(States state, Object in)
		{
				try
				{
						setters.get(state).accept(in);
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
				}
		}

		private HashMap<States, Supplier> getters = new HashMap<>();
		private HashMap<States, Consumer> setters = new HashMap<>();

		enum States
		{
				EXAMPLE_STATE("example_state", "boolean", null),
				EXAMPLE_VALUE("example_value", "numeric", new Object()),
				EC("EC", "numeric", new Object()),
				DO("DO", "numeric", new Object()),
				T("T", "numeric", new Object()),
				UTM_POSE("utm_pose", "location", new Object()), // location using UTM
				HOME_POSE("home_pose", "location", new Object()), // home location using UTM
				ELAPSED_TIME("elapsed_time", "numeric", null),
				TIME_SINCE_OPERATOR("time_since_operator", "numeric", null), // time elapsed past last time operator detected
				BATTERY_VOLTAGE("battery_voltage", "numeric", null),
				IS_CONNECTED("is_connected", "boolean", null),
				IS_AUTONOMOUS("is_autonomous", "boolean", null),
				HAS_FIRST_AUTONOMY("has_first_autonomy", "boolean", null),
				IS_RUNNING("is_running", "boolean", null),
				IS_EXECUTING_FAILSAFE("is_exec_failsafe", "boolean", null),
				IS_TAKING_SAMPLE("is_taking_sample", "boolean", null),
				ALWAYS_TRUE("always_true", "boolean", null),
				ALWAYS_FALSE("always_false", "boolean", null);

				final String name;
				final String type;
				Object lock;

				States(final String _name, final String _type, Object _lock)
				{
						name = _name;
						type = _type;
						lock = _lock;
				}

				public static States fromString(final String s)
				{
						for (States state: values())
						{
								if (state.name.equals(s)) return state;
						}
						Log.w("AP", String.format("State \"%s\" not available. Will always return false instead.", s));
						return ALWAYS_FALSE;
				}
				public static boolean isNumeric(final String s)
				{
						for (States state: values())
						{
								if (state.name.equals(s)) return state.type.equals("numeric");
						}
						return false;
				}
				public static boolean isBoolean(final String s)
				{
						for (States state: values())
						{
								if (state.name.equals(s)) return state.type.equals("boolean");
						}
						return false;
				}
				public static boolean isLocation(final String s)
				{
						for (States state: values())
						{
								if (state.name.equals(s)) return state.type.equals("location");
						}
						return false;
				}
		}

		public VehicleState(VehicleServerImpl server)
		{
				_serverImpl = server;

				getters.put(States.EXAMPLE_STATE, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								example_state.set(!example_state.get());
								return example_state.get();
						}
				});
				getters.put(States.EXAMPLE_VALUE, new Supplier<Double>()
				{
						@Override
						public Double get()
						{
								synchronized (States.EXAMPLE_VALUE.lock)
								{
										example_value += 1.0;
										Log.d("AP", String.format("Example value = %.0f", example_value));
										return example_value;
								}
						}
				});
				setters.put(States.EXAMPLE_VALUE, new Consumer<Double>()
				{
						@Override
						public void accept(Double o)
						{
								synchronized (States.EXAMPLE_VALUE.lock)
								{
										example_value = o;
								}
						}
				});

				getters.put(States.EC, new Supplier<Double>()
				{
						@Override
						public Double get()
						{
								synchronized (States.EC.lock)
								{
										Log.v(logTag, String.format("EC queried = %f", EC));
										return EC;
								}
						}
				});
				setters.put(States.EC, new Consumer<Double>()
				{
						@Override
						public void accept(Double o)
						{
								synchronized (States.EC.lock)
								{
										Log.v(logTag, String.format("EC set to %f", o));
										EC = o;
								}
						}
				});

				getters.put(States.UTM_POSE, new Supplier<UtmPose>()
				{
						@Override
						public UtmPose get()
						{
								synchronized (States.UTM_POSE.lock)
								{
										return currentUtmPose.clone();
								}
						}
				});
				setters.put(States.UTM_POSE, new Consumer<UtmPose>()
				{
						@Override
						public void accept(UtmPose o)
						{
								synchronized (States.UTM_POSE.lock)
								{
										currentUtmPose = o.clone();
								}
						}
				});

				getters.put(States.ALWAYS_TRUE, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return true;
						}
				});
				getters.put(States.ALWAYS_FALSE, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return false;
						}
				});
		}



}
