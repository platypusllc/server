package com.platypus.android.server;

import android.util.Log;

import com.platypus.crw.data.UtmPose;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by jason on 8/11/17.
 */

public class VehicleState
{

		final int NUMBER_OF_SAMPLER_JARS = 4;
		private VehicleServerImpl _serverImpl;
		static String logTag = "AP";
		AtomicBoolean[] jar_available = new AtomicBoolean[NUMBER_OF_SAMPLER_JARS];

		enum States
		{
				EXAMPLE_STATE("example_state"),
				EXAMPLE_VALUE("example_value"),
				EXAMPLE_ARRAY("example_array"),
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
				NEXT_AVAILABLE_JAR("next_jar"),
				ALWAYS_TRUE("always_true"),
				ALWAYS_FALSE("always_false");

				final String name;
				States(final String _name) { name = _name; }
		}
		abstract class State <S, F>
		{
				/*
					Generic for storing and retrieving the state
					Template type F is the value used in get and set, which may be different than S.
					For example, S is AtomicBoolean and F is Boolean for threadsafe booleans.
					There is an array of AtomicBoolean objects, and the get and set use Boolean objects.
					IMPORTANT NOTE: child classes MUST implement 2 constructors!!!
					Constructor 1: takes integer argument as size of value_array, initializes entire array
					Constructor 2: same as constructor 1, but assume size = 1
					https://stackoverflow.com/questions/529085/how-to-create-a-generic-array-in-java
				*/

				S[] value_array;
				State(Class<S> array_class, Class<F> value_class, int size, F default_value)
				{
						value_array = (S[])(Array.newInstance(array_class, size));
						try
						{
								Class primitive_class;
								// TODO: improve this ugly workaround (Double, Boolean, Long, and Integer not having constructors that make any sense)
								if (default_value instanceof Double)
								{
										primitive_class = Double.TYPE;
								}
								else if (default_value instanceof Boolean)
								{
										primitive_class = Boolean.TYPE;
								}
								else if (default_value instanceof Long)
								{
										primitive_class = Long.TYPE;
								}
								else if (default_value instanceof Integer)
								{
										primitive_class = Integer.TYPE;
								}
								else
								{
										primitive_class = Void.TYPE;
								}
								Constructor value_constructor = value_class.getConstructor(primitive_class);
								Constructor array_constructor = array_class.getConstructor(primitive_class);
								for (int i = 0; i < value_array.length; i++)
								{
										Object constructed_value = value_constructor.newInstance(default_value);
										value_array[i] = (S)array_constructor.newInstance(constructed_value);
								}
						}
						catch (Exception e)
						{
								Log.e(logTag, String.format("State class constructor error: %s", e.getMessage()));
						}
				}
				State(Class<S> array_class, Class<F> value_class, F default_value)
				{
						this(array_class, value_class, 1, default_value); // default size = 1
				}
				abstract F customGet(int index); // the custom implementation of get
				abstract void customSet(int index, F in); // changing value in a map or array

				public F get() { return get(0); } // get first element

				public F get(int index) // the interface get(), automatically check for acceptable index
				{
						if (index >= 0 && index < value_array.length)
						{
								return customGet(index);
						}
						return null;
				}

				public void set(int index, F in)
				{
						if (index >= 0 && index < value_array.length)
						{
								customSet(index, in);
						}
				}

				public void set(F in) { set(0, in); } // set first element

				public void setAll(F in)
				{
						for (int i = 0; i < value_array.length; i++)
						{
								set(i, in);
						}
				}
		}

		class BooleanState extends State<AtomicBoolean, Boolean>
		{
				BooleanState() { super(AtomicBoolean.class, Boolean.class, Boolean.valueOf(false)); }
				BooleanState(int size) { super(AtomicBoolean.class, Boolean.class, size, Boolean.valueOf(false)); }
				@Override
				public Boolean customGet(int index)
				{
						return value_array[index].get();
				}
				@Override
				public void customSet(int index, Boolean in)
				{
						value_array[index].set(in);
				}
		}

		class LongState extends State<AtomicLong, Long>
		{
				LongState() { super(AtomicLong.class, Long.class, Long.valueOf(0)); }
				LongState(int size) { super(AtomicLong.class, Long.class, size, Long.valueOf(0)); }
				@Override
				Long customGet(int index)
				{
						return value_array[index].get();
				}
				@Override
				void customSet(int index, Long in)
				{
						value_array[index].set(in);
				}
		}

		class DoubleState extends State<Double, Double>
		{
				DoubleState() { super(Double.class, Double.class, Double.valueOf(0.0)); }
				DoubleState(int size) { super(Double.class, Double.class, size, Double.valueOf(0.0)); }
				Object lock = new Object();
				@Override
				public Double customGet(int index)
				{
						synchronized (lock)
						{
								return value_array[index];
						}
				}

				@Override
				public void customSet(int index, Double in)
				{
						synchronized (lock)
						{
								value_array[index] = in;
						}
				}
		}

		class UtmPoseState extends State<UtmPose, UtmPose>
		{
				UtmPoseState() { super(UtmPose.class, UtmPose.class, new UtmPose()); }
				UtmPoseState(int size) { super(UtmPose.class, UtmPose.class, size, new UtmPose()); }
				Object lock = new Object();
				@Override
				public UtmPose customGet(int index)
				{
						synchronized (lock) { return value_array[index].clone(); }
				}

				@Override
				public void customSet(int index, UtmPose in)
				{
						synchronized (lock)
						{
								value_array[index] = in.clone();
						}
				}
		}

		HashMap<String, State> state_map = new HashMap<>();

		public <F> F get(String state_name)
		{
				if (!state_map.containsKey(state_name))
				{
						Log.e(logTag, String.format("state \"%s\" does not exist", state_name));
				}
				Object result = state_map.get(state_name).get();
				if (result == null)
				{
						Log.w(logTag, String.format("state \"%s\" returned null", state_name));
						return null;
				}
				return (F)result;
		}
		public <F> F get(String state_name, int index)
		{
				if (!state_map.containsKey(state_name))
				{
						Log.e(logTag, String.format("state \"%s\" does not exist", state_name));
				}
				Object result = state_map.get(state_name).get(index);
				if (result == null)
				{
						Log.w(logTag, String.format("state \"%s\"[%d] returned null", state_name, index));
						return null;
				}
				return (F)result;
		}
		public <F> void set(String state_name, F in)
		{
				if (!state_map.containsKey(state_name))
				{
						Log.e(logTag, String.format("Tried to set \"%s\", which does not exist", state_name));
						return;
				}
				state_map.get(state_name).set(in);
		}
		public <F> void set(String state_name, int index, F in)
		{
				if (!state_map.containsKey(state_name))
				{
						Log.e(logTag, String.format("Tried to set \"%s\", which does not exist", state_name));
						return;
				}
				state_map.get(state_name).set(index, in);
		}

		VehicleState(VehicleServerImpl server)
		{
				_serverImpl = server;

				for (int i = 0; i < jar_available.length; i++)
				{
						jar_available[i] = new AtomicBoolean(true); // all jars initially available
				}

				state_map.put(States.EXAMPLE_STATE.name,
								new State<AtomicBoolean, Boolean>(AtomicBoolean.class, Boolean.class, false)
				{
						@Override
						Boolean customGet(int index)
						{
								value_array[index].set(!value_array[index].get());
								return value_array[index].get();
						}
						@Override
						void customSet(int index, Boolean in) { }
				});

				state_map.put(States.EXAMPLE_VALUE.name,
								new State<Double, Double>(Double.class, Double.class, 0.0)
				{
						Object lock = new Object();
						@Override
						Double customGet(int index)
						{
								synchronized (lock)
								{
										value_array[index] += 1.0;
										return value_array[index];
								}
						}
						@Override
						void customSet(int index, Double in) { }
				});

				state_map.put(States.ALWAYS_FALSE.name, new State<Boolean, Boolean>(Boolean.class, Boolean.class, Boolean.valueOf(false))
				{
						@Override
						Boolean customGet(int index) { return false; }
						@Override
						void customSet(int index, Boolean in) { }
				});
				state_map.put(States.ALWAYS_TRUE.name, new State<Boolean, Boolean>(Boolean.class, Boolean.class, Boolean.valueOf(true))
				{
						@Override
						Boolean customGet(int index) { return true; }
						@Override
						void customSet(int index, Boolean in) { }
				});

				state_map.put(States.EXAMPLE_ARRAY.name, new State<Long, Long>(Long.class, Long.class, 3, Long.valueOf(0))
				{
						long counter;
						Object lock = new Object();
						@Override
						Long customGet(int index)
						{
								synchronized (lock)
								{
										value_array[index] = ++counter;
										return value_array[index];
								}
						}
						@Override
						void customSet(int index, Long in) { }
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
				state_map.put(States.ELAPSED_TIME.name, new State<AtomicLong, Long>(AtomicLong.class, Long.class, Long.valueOf(0))
				{
						long first = System.currentTimeMillis();

						@Override
						Long customGet(int index)
						{
								value_array[index].set(System.currentTimeMillis() - first);
								return null;
						}
						@Override
						void customSet(int index, Long in) { }
				});
				state_map.put(States.TIME_SINCE_OPERATOR.name, new LongState());
				state_map.put(States.CURRENT_POSE.name, new UtmPoseState());
				state_map.put(States.HOME_POSE.name, new UtmPoseState());
				state_map.put(States.NEXT_AVAILABLE_JAR.name, new State<Void, Long>(Void.class, Long.class, Long.valueOf(0))
				{
						// note how the value_array is totally ignored here.
						// Limitations of the class force the available jar boolean array to be outside
						@Override
						Long customGet(int index)
						{
								for (int i = 0; i < jar_available.length; i++)
								{
										if (jar_available[i].get())
										{
												jar_available[i].set(false); // jar is now unavailable
												return Long.valueOf(i);
										}
								}
								return -1L;
						}
						@Override
						void customSet(int index, Long in) { }
				});
		}

		void resetSampleJars()
		{
				for (int i = 0; i < jar_available.length; i++)
				{
						resetSampleJar(i);
				}
		}
		private void resetSampleJar(int i)
		{
				jar_available[i].set(true);
		}
}
