package com.platypus.android.server;

import android.util.Log;

import com.platypus.crw.data.UtmPose;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by jason on 8/11/17.
 */

public class VehicleState <S, K, F>
{
		enum States
		{
				EXAMPLE_STATE("example_state"),
				EXAMPLE_VALUE("example_value"),
				EC("EC"),
				DO("DO"),
				T("T"),
				CURRENT_POSE("current_pose"), // location using UTM
				HOME_POSE("home_pose"), // home location using UTM
				ELAPSED_TIME("elapsed_time"),
				TIME_SINCE_OPERATOR("time_since_operator"), // time elapsed past last time operator detected
				BATTERY_VOLTAGE("battery_voltage"),
				IS_CONNECTED("is_connected"),
				IS_AUTONOMOUS("is_autonomous"),
				HAS_FIRST_AUTONOMY("has_first_autonomy"),
				IS_RUNNING("is_running"),
				IS_EXECUTING_FAILSAFE("is_exec_failsafe"),
				IS_TAKING_SAMPLE("is_taking_sample"),
				NEXT_AVAILABLE_JAR("next_available_jar"),
				ALWAYS_TRUE("always_true"),
				ALWAYS_FALSE("always_false");

				final String name;
				States(final String _name) { name = _name; }
		}
		abstract class State
		{
				/* Generic for storing and retrieving the state */
				public abstract S get();
				public abstract F get(K key); // getting value in a map or array
				public abstract void set(S in);
				public abstract void set(K key, F in); // changing value in a map or array
		}

		class BooleanState extends State
		{
				AtomicBoolean value = new AtomicBoolean(false);
				@Override
				public S get()
				{
						return (S)Boolean.valueOf(value.get());
				}
				@Override
				public F get(K key) { return null; }
				@Override
				public void set(S in) { value.set((Boolean)in); }
				@Override
				public void set(K key, F in) { }
		}

		class DoubleState extends State
		{
				Double value = 0.0;
				final Object lock = new Object();
				@Override
				public S get()
				{
						synchronized (lock)
						{
								return (S)value;
						}
				}
				@Override
				public F get(K key) { return null; }
				@Override
				public void set(S in)
				{
						synchronized (lock)
						{
								value = (Double)in;
						}
				}
				@Override
				public void set(K key, F in) { }
		}

		class UtmPoseState extends State
		{
				UtmPose value;
				final Object lock = new Object();
				@Override
				public S get()
				{
						synchronized (lock)
						{
								return (S) value.clone();
						}
				}
				@Override
				public F get(K key) { return null; }
				@Override
				public void set(S in)
				{
						synchronized (lock)
						{
								value = ((UtmPose)in).clone();
						}
				}
				@Override
				public void set(K key, F in) { }
		}

		HashMap<String, State> state_map = new HashMap<>();
		public S get(String state_name)
		{
				return state_map.get(state_name).get();
		}
		public F get(String state_name, K key)
		{
				return state_map.get(state_name).get(key);
		}
		public void set(String state_name, S in)
		{
				state_map.get(state_name).set(in);
		}
		public void set(String state_name, K key, F in)
		{
				state_map.get(state_name).set(key, in);
		}

		private VehicleServerImpl _serverImpl;
		static String logTag = "AP";

		AtomicBoolean[] jar_available = new AtomicBoolean[4];

		VehicleState(VehicleServerImpl server)
		{
				_serverImpl = server;

				for (int i = 0; i < jar_available.length; i++)
				{
						jar_available[i] = new AtomicBoolean(true); // all jars initially available
				}

				state_map.put(States.EXAMPLE_STATE.name, new State()
				{
						AtomicBoolean value = new AtomicBoolean(false);

						@Override
						public S get()
						{
								value.set(!value.get()); // flip true/false
								return (S)Boolean.valueOf(value.get());
						}

						@Override
						public F get(K key) { return null; }

						@Override
						public void set(S in) { value.set((Boolean)in); }

						@Override
						public void set(K key, F in) { }
				});

				state_map.put(States.EXAMPLE_VALUE.name, new State()
				{
						Double value = 0.0;
						Object lock = new Object();

						@Override
						public S get()
						{
								synchronized (lock)
								{
										value += 1.; // increment when queried
										return (S)Double.valueOf(value);
								}
						}
						@Override
						public F get(K key) { return null; }
						@Override
						public void set(S in)
						{
								synchronized (lock)
								{
										value = (Double)in;
								}
						}
						@Override
						public void set(K key, F in) { }
				});

				state_map.put(States.ALWAYS_FALSE.name, new State()
				{
						@Override
						public S get() { return (S)Boolean.valueOf(false);}
						@Override
						public F get(K key) { return null; }
						@Override
						public void set(S in) { }
						@Override
						public void set(K key, F in) { }
				});
				state_map.put(States.ALWAYS_TRUE.name, new State()
				{
						@Override
						public S get() { return (S)Boolean.valueOf(true);}
						@Override
						public F get(K key) { return null; }
						@Override
						public void set(S in) { }
						@Override
						public void set(K key, F in) { }
				});

				state_map.put(States.IS_CONNECTED.name, new BooleanState());
				state_map.put(States.IS_AUTONOMOUS.name, new BooleanState());
				state_map.put(States.IS_RUNNING.name, new BooleanState());
				state_map.put(States.HAS_FIRST_AUTONOMY.name, new BooleanState());
				state_map.put(States.IS_EXECUTING_FAILSAFE.name, new BooleanState());
				state_map.put(States.IS_TAKING_SAMPLE.name, new BooleanState());
				state_map.put(States.EC.name, new DoubleState());
				state_map.put(States.T.name, new DoubleState());
				state_map.put(States.DO.name, new DoubleState());
				state_map.put(States.BATTERY_VOLTAGE.name, new DoubleState());
				state_map.put(States.ELAPSED_TIME.name, new State()
				{
						AtomicLong value = new AtomicLong(0);
						long first = System.currentTimeMillis();
						@Override
						public S get()
						{
								value.set(System.currentTimeMillis() - first);
								return (S)Long.valueOf(value.get());
						}
						@Override
						public F get(K key) { return null; }
						@Override
						public void set(S in) { }
						@Override
						public void set(K key, F in) { }
				});
				state_map.put(States.TIME_SINCE_OPERATOR.name, new State()
				{
						AtomicLong value = new AtomicLong(0);
						@Override
						public S get()
						{
								return (S)Long.valueOf(value.get());
						}
						@Override
						public F get(K key) { return null;}
						@Override
						public void set(S in)
						{
								value.set((Long)in);
						}
						@Override
						public void set(K key, F in) { }
				});
				state_map.put(States.CURRENT_POSE.name, new UtmPoseState());
				state_map.put(States.HOME_POSE.name, new UtmPoseState());
				state_map.put(States.NEXT_AVAILABLE_JAR.name, new State()
				{
						@Override
						public S get()
						{
								for (int i = 0; i < jar_available.length; i++)
								{
										if (jar_available[i].get())
										{
												jar_available[i].set(false); // jar is now unavailable
												return (S)Long.valueOf(i);
										}
								}
								return (S)Long.valueOf(-1);
						}
						@Override
						public F get(K key) { return null; }
						@Override
						public void set(S in) { }
						@Override
						public void set(K key, F in) { }
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
