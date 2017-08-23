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
		AtomicBoolean is_connected = new AtomicBoolean(false);
		AtomicBoolean is_autonomous = new AtomicBoolean(false);
		AtomicBoolean has_first_autonomy = new AtomicBoolean(false);
		AtomicBoolean is_running = new AtomicBoolean(false);
		AtomicBoolean is_executing_failsafe = new AtomicBoolean(false);
		AtomicBoolean is_taking_sample = new AtomicBoolean(false);

		AtomicBoolean[] jar_available = new AtomicBoolean[4];

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
				EXAMPLE_VALUE("example_value", "double", new Object()),
				EC("EC", "double", new Object()),
				DO("DO", "double", new Object()),
				T("T", "double", new Object()),
				UTM_POSE("utm_pose", "utmPose", new Object()), // location using UTM
				HOME_POSE("home_pose", "utmPose", new Object()), // home location using UTM
				ELAPSED_TIME("elapsed_time", "long", null),
				TIME_SINCE_OPERATOR("time_since_operator", "long", null), // time elapsed past last time operator detected
				BATTERY_VOLTAGE("battery_voltage", "long", null),
				IS_CONNECTED("is_connected", "boolean", null),
				IS_AUTONOMOUS("is_autonomous", "boolean", null),
				HAS_FIRST_AUTONOMY("has_first_autonomy", "boolean", null),
				IS_RUNNING("is_running", "boolean", null),
				IS_EXECUTING_FAILSAFE("is_exec_failsafe", "boolean", null),
				IS_TAKING_SAMPLE("is_taking_sample", "boolean", null),
				NEXT_AVAILABLE_JAR("next_available_jar", "long", null),
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
				public static boolean isLong(final String s)
				{
						for (States state: values())
						{
								if (state.name.equals(s)) return state.type.equals("long");
						}
						return false;
				}
				public static boolean isDouble(final String s)
				{
						for (States state: values())
						{
								if (state.name.equals(s)) return state.type.equals("double");
						}
						return false;
				}
				public static boolean isNumeric(final String s)
				{
						return (isLong(s) || isDouble(s));
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
								if (state.name.equals(s)) return state.type.equals("utmPose");
						}
						return false;
				}
		}

		public VehicleState(VehicleServerImpl server)
		{
				_serverImpl = server;

				for (int i = 0; i < jar_available.length; i++)
				{
						jar_available[i].set(true); // all jars initially available
				}

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

				getters.put(States.T, new Supplier<Double>()
				{
						@Override
						public Double get()
						{
								synchronized (States.T.lock)
								{
										Log.v(logTag, String.format("T queried = %f", T));
										return T;
								}
						}
				});
				setters.put(States.T, new Consumer<Double>()
				{
						@Override
						public void accept(Double o)
						{
								synchronized (States.T.lock)
								{
										Log.v(logTag, String.format("T set to %f", o));
										T = o;
								}
						}
				});

				getters.put(States.DO, new Supplier<Double>()
				{
						@Override
						public Double get()
						{
								synchronized (States.DO.lock)
								{
										Log.v(logTag, String.format("DO queried = %f", DO));
										return DO;
								}
						}
				});
				setters.put(States.DO, new Consumer<Double>()
				{
						@Override
						public void accept(Double o)
						{
								synchronized (States.DO.lock)
								{
										Log.v(logTag, String.format("DO set to %f", o));
										DO = o;
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

				getters.put(States.HOME_POSE, new Supplier<UtmPose>()
				{
						@Override
						public UtmPose get()
						{
								synchronized (States.HOME_POSE.lock)
								{
										return homeUtmPose.clone();
								}
						}
				});
				setters.put(States.HOME_POSE, new Consumer<UtmPose>()
				{
						@Override
						public void accept(UtmPose o)
						{
								synchronized (States.HOME_POSE.lock)
								{
										homeUtmPose = o.clone();
								}
						}
				});

				getters.put(States.ELAPSED_TIME, new Supplier<Long>()
				{
						@Override
						public Long get()
						{
								return elapsed_time.get();
						}
				});
				setters.put(States.ELAPSED_TIME, new Consumer<Long>()
				{
						@Override
						public void accept(Long o)
						{
								elapsed_time.set(o);
						}
				});

				getters.put(States.TIME_SINCE_OPERATOR, new Supplier<Long>()
				{
						@Override
						public Long get()
						{
								return time_since_operator.get();
						}
				});
				setters.put(States.TIME_SINCE_OPERATOR, new Consumer<Long>()
				{
						@Override
						public void accept(Long o)
						{
								time_since_operator.set(o);
						}
				});

				getters.put(States.BATTERY_VOLTAGE, new Supplier<Long>()
				{
						@Override
						public Long get()
						{
								return battery_voltage.get();
						}
				});
				setters.put(States.BATTERY_VOLTAGE, new Consumer<Long>()
				{
						@Override
						public void accept(Long o)
						{
								battery_voltage.set(o);
						}
				});

				getters.put(States.IS_CONNECTED, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return is_connected.get();
						}
				});
				setters.put(States.IS_CONNECTED, new Consumer<Boolean>()
				{
						@Override
						public void accept(Boolean o)
						{
								is_connected.set(o);
						}
				});

				getters.put(States.IS_AUTONOMOUS, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return is_autonomous.get();
						}
				});
				setters.put(States.IS_AUTONOMOUS, new Consumer<Boolean>()
				{
						@Override
						public void accept(Boolean o)
						{
								is_autonomous.set(o);
						}
				});

				getters.put(States.HAS_FIRST_AUTONOMY, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return has_first_autonomy.get();
						}
				});
				setters.put(States.HAS_FIRST_AUTONOMY, new Consumer<Boolean>()
				{
						@Override
						public void accept(Boolean o)
						{
								has_first_autonomy.set(o);
						}
				});

				getters.put(States.IS_RUNNING, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return is_running.get();
						}
				});
				setters.put(States.IS_RUNNING, new Consumer<Boolean>()
				{
						@Override
						public void accept(Boolean o)
						{
								is_running.set(o);
						}
				});

				getters.put(States.IS_EXECUTING_FAILSAFE, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return is_executing_failsafe.get();
						}
				});
				setters.put(States.IS_EXECUTING_FAILSAFE, new Consumer<Boolean>()
				{
						@Override
						public void accept(Boolean o)
						{
								is_executing_failsafe.set(o);
						}
				});

				getters.put(States.IS_TAKING_SAMPLE, new Supplier<Boolean>()
				{
						@Override
						public Boolean get()
						{
								return is_taking_sample.get();
						}
				});
				setters.put(States.IS_TAKING_SAMPLE, new Consumer<Boolean>()
				{
						@Override
						public void accept(Boolean o)
						{
								is_taking_sample.set(o);
						}
				});

				getters.put(States.NEXT_AVAILABLE_JAR, new Supplier<Long>()
				{
						@Override
						public Long get()
						{
								for (int i = 0; i < jar_available.length; i++)
								{
										if (jar_available[i].get())
										{
												jar_available[i].set(false); // jar is now unavailable
												return Long.class.cast(i);
										}
								}
								return -1L;
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

		public void resetSampleJars()
		{
				for (int i = 0; i < jar_available.length; i++)
				{
						resetSampleJar(i);
				}
		}
		public void resetSampleJar(int i)
		{
				jar_available[i].set(true);
		}

}
