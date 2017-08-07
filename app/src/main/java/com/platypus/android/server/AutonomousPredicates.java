package com.platypus.android.server;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;


/**
 * Created by jason on 8/4/17.
 *
 * https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html
 * https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html
 * https://developer.android.com/studio/preview/install-preview.html
 * http://www.java2s.com/Tutorials/Java_Lambda/java.util.function/BooleanSupplier/BooleanSupplier_example.htm
 * "currying"
 *
 */

public class AutonomousPredicates
{
		VehicleServerImpl _serverImpl;
		String logTag = "AP";
		static long ap_count = 0;
		Map<Long, ScheduledFuture> triggered_actions_map = new HashMap<>();
		ScheduledThreadPoolExecutor poolExecutor = new ScheduledThreadPoolExecutor(4);
		// TODO: do we want something that can increase and decrease the thread pool, rather than fixed?

		JSONObject example_JSONObject = new JSONObject();

		class TriggeredAction implements Runnable
		{
				long _id;
				boolean _isPermanent; // if true, this triggered action Runnable will be canceled after it runs once
				BooleanSupplier _test; // meant to call methods in the VehicleServerImpl
				Consumer<Void> _action; // meant to call methods in the VehicleServerImpl

				public TriggeredAction(BooleanSupplier test, Consumer<Void> action, boolean isPermanent)
				{
						_id = ap_count++;
						_isPermanent = isPermanent;
						_test = test;
						_action = action;
				}

				public long getID() { return _id; }

				@Override
				public void run()
				{
						Log.d("AP", String.format("Task # %d running...", _id));
						if (_test.getAsBoolean())
						{
								Log.i("AP", String.format("Task # %d test returned TRUE, executing task...", _id));
								_action.accept(null);
								if (!_isPermanent)
								{
										Log.i("AP", String.format("Task # %d completed, removing...", _id));
										triggered_actions_map.get(_id).cancel(true);
										triggered_actions_map.remove(_id);
								}
						}
						else
						{
								Log.i("AP", String.format("Task # %d test returned FALSE", _id));
						}
				}
		}

		class DynamicPredicateComposition <R extends Number>
		{
				Predicate<R> predicate = new Predicate<R>()
				{
						@Override
						public boolean test(R r)
						{
								Log.v("AP", "Executing the original default predicate");
								return false; // must start as false and composition must start with an OR
						}
				};
				int depth = 0;
				R stateGetOutput;
				Supplier<R> stateGetFunction;
				public DynamicPredicateComposition(Supplier<R> _stateGetFunction)
				{
						stateGetFunction = _stateGetFunction;
				}
				private void update()
				{
						stateGetOutput = stateGetFunction.get();
						Double a = Double.class.cast(stateGetOutput);
						Log.d("AP", String.format("update(): stateGetOutput = %.0f", a));
				}
				public BooleanSupplier build()
				{
						return new BooleanSupplier()
						{
								@Override
								public boolean getAsBoolean()
								{
										Log.d("AP", "Executing BooleanSupplier...");
										update();
										// TODO: duplicate predicate so we can reuse DynamicPredicateComposition objects
										return predicate.test(null); // don't need to use the input
								}
						};
				}
				private Predicate<R> generatePredicate(final String comparator, final R right_hand_side)
				{
						Log.d("AP", String.format("Generating new predicate: %s", comparator));
						return new Predicate<R>()
						{
								@Override
								public boolean test(R r) // the input to this is never used
								{
										Double a = Double.class.cast(stateGetOutput);
										Double b = Double.class.cast(right_hand_side);
										boolean result = false;
										if (comparator.equals("eq")) result = a == b;
										else if (comparator.equals("gt")) result = a > b;
										else if (comparator.equals("lt")) result = a < b;
										else if (comparator.equals("le")) result = a <= b;
										else if (comparator.equals("ge")) result = a >= b;
										Log.d("AP", String.format("Executed a predicate: %.0f %s %.0f %s",
														a, comparator, b, Boolean.toString(result)));
										return result;
								}
						};
				}
				public DynamicPredicateComposition and(final String comparator, final R right_hand_side)
				{
						depth++;
						if (depth == 1) // MUST START WITH AN OR
						{
								depth = 0;
								return or(comparator, right_hand_side);
						}
						predicate = predicate.and(generatePredicate(comparator, right_hand_side));
						Log.d("AP", String.format("Added an AND: %s %.0f", comparator, right_hand_side));
						return this;
				}
				public DynamicPredicateComposition or(final String comparator, final R right_hand_side)
				{
						depth++;
						predicate = predicate.or(generatePredicate(comparator, right_hand_side));
						Log.d("AP", String.format("Added an OR: %s %.0f", comparator, right_hand_side));
						return this;
				}
		}

		public AutonomousPredicates(VehicleServerImpl server)
		{
				_serverImpl = server;

				// example
				try
				{
						example_JSONObject
										.put("action", "sample")
										.put("trigger", new JSONObject()
														.put("type", "EC")
														.put("value", new JSONObject()
																		.put("and", new JSONObject()
																						.put("lt", 2000)
																						.put("ge", 1500)
																		)
														)
										)
										.put("ends", true)
										.put("Hz", 1);
				}
				catch (JSONException e)
				{
						Log.e("AP", e.getMessage());
				}
				parseActionDefinition(example_JSONObject);

		}

		public void cancelAll()
		{
				for (Map.Entry<Long, ScheduledFuture> entry : triggered_actions_map.entrySet())
				{
						entry.getValue().cancel(true);
				}
				triggered_actions_map.clear();
				ap_count = 0;
		}

		void readDefaultFile()
		{
				// read the default text file and generate a JSONObject with the file contents
		}

		void parseActionDefinition(JSONObject definition)
		{
				// parse the JSON to generated the necessary stuff
				double hz = 2.;
				Double ms_delay = 1./hz*1000.;

				// add triggered actions to the list
				/*
				TriggeredAction ta = new TriggeredAction
								(
												new BooleanSupplier()
												{
														@Override
														public boolean getAsBoolean()
														{
																return _serverImpl.getExampleState();
														}
												},
												new Consumer<Void>()
												{
														@Override
														public void accept(Void aVoid)
														{
																_serverImpl.exampleAction();
														}
												},
												false
								);

				// put the triggered action task into the scheduler queue and store its ScheduledFuture in the HashMap (so we can cancel it later)
				triggered_actions_map.put(ta.getID(), poolExecutor.scheduleAtFixedRate(ta, 0, ms_delay.intValue(), TimeUnit.MILLISECONDS));
				*/

				Log.w("AP", "****** NEW EXAMPLE ******");
				DynamicPredicateComposition <Double> example_predicate_composition =
								new DynamicPredicateComposition<>(new Supplier<Double>()
								{
										@Override
										public Double get()
										{
												double example_value = _serverImpl.getExampleValue();
												Log.d("AP", String.format("Update function example value = %.0f", example_value));
												return example_value;
										}
								});

				example_predicate_composition.or("lt", 20.).and("gt", 5.);
				TriggeredAction ta = new TriggeredAction
								(
												example_predicate_composition.build(),
												new Consumer<Void>()
												{
														@Override
														public void accept(Void aVoid)
														{
																_serverImpl.exampleAction();
														}
												},
												false
								);

				// put the triggered action task into the scheduler queue and store its ScheduledFuture in the HashMap (so we can cancel it later)
				triggered_actions_map.put(ta.getID(), poolExecutor.scheduleAtFixedRate(ta, 0, ms_delay.intValue(), TimeUnit.MILLISECONDS));
		}

		void displayActions()
		{
				// display some kind of summary of the current trigger definitions in the debug activity
		}
}
