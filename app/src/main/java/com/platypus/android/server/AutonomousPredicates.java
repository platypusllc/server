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

		class DynamicPredicateComposition <T>
		{
				/*
				VERY IMPORTANT NOTE
				Predicates chained with and() and or() short circuit (i.e. skip evaluating subsequent
				predicates).
				From the Java docs for and():
					Returns a composed predicate that represents a short-circuiting logical AND of this
					predicate and another. When evaluating the composed predicate, if this predicate is
					false, then the other predicate is not evaluated.
				From the Java docs for or():
					Returns a composed predicate that represents a short-circuiting logical OR of this
					predicate and another. When evaluating the composed predicate, if this predicate is
					true, then the other predicate is not evaluated.
				Because of this, the first predicate in the chain is ALWAYS evaluated.
			  Note that something.or(other) short circuits if *something* is true, NOT if *other* is true.
			  For example,
				example_predicate_composition
					.or("example_state")  --> always runs b/c false.or(other) will always evaluate *other*
					.and("example_value", "gt", 5.) --> only runs if "example_state" was TRUE (b/c "and")
					.and("example_value","lt", 20.); --> only runs if value > 5 is TRUE
				The above is equivalent to
				example_state && {doesn't matter if example_state is false}
				true && value > 5 && {doesn't matter if value is not > 5}

				Another example,
				example_predicate_composition
					.or("example_state")  --> always runs b/c false.or(other) will always evaluate *other*
					.or("example_value", "gt", 5.) --> only runs if "example_state" was FALSE
					.and("example_value","lt", 20.); --> only runs if value > 5 is TRUE
				The above is equivalent to
				example_state || {doesn't matter if example_state is true}
				false || value > 5 && {doesn't matter if value is not > 5}

				Another example,
				example_predicate_composition
					.and("example_value", "lt", 20.) --> always runs b/c we force it to be OR rather than AND
				  .and("example_value", "gt", 5.) --> only runs if value < 20 is TRUE
				  .or("example_state") --> only runs if value > 5 is FALSE
				The above is equivalent to
				value < 20 && {doesn't matter if value is not < 20}
				true && value > 5 || {doesn't matter if value > 5}
				*/
				int depth = 0; // can serve as unique ID for the predicates
				Predicate<T> predicate = new Predicate<T>()
				{
						@Override
						public boolean test(T t)
						{
								Log.v("AP", "Executing the original default predicate");
								return false; // must start as false and composition must start with an OR
						}
				};
				public BooleanSupplier build()
				{
						return new BooleanSupplier()
						{
								@Override
								public boolean getAsBoolean()
								{
										Log.d("AP", "Executing composite predicate...");
										// TODO: duplicate predicate so we can reuse DynamicPredicateComposition objects
										return predicate.test(null); // don't need to use the input
								}
						};
				}
				private Predicate<T> generatePredicate(final String left_hand_side, final String comparator, final double right_hand_side)
				{
						Log.v("AP", String.format("Generating new predicate: %s", comparator));
						return new Predicate<T>()
						{
								String definition = String.format("%s %s %.0f", left_hand_side, comparator, right_hand_side);
								@Override
								public boolean test(T t) // the input to this is never used
								{
										Double a = Double.class.cast(_serverImpl.getState(left_hand_side));
										Double b = Double.class.cast(right_hand_side);
										boolean result = false;
										if (comparator.equals("eq")) result = a == b;
										else if (comparator.equals("ne")) result = a != b;
										else if (comparator.equals("gt")) result = a > b;
										else if (comparator.equals("lt")) result = a < b;
										else if (comparator.equals("le")) result = a <= b;
										else if (comparator.equals("ge")) result = a >= b;
										Log.d("AP", String.format("Executed a predicate: %s = %s", definition, Boolean.toString(result)));
										return result;
								}
						};
				}
				private Predicate<T> generatePredicate(final String boolean_state)
				{
						Log.v("AP", String.format("Generating new boolean only predicate"));
						return new Predicate<T>()
						{
								@Override
								public boolean test(T t) // the input to this is never used
								{
										Boolean result = Boolean.class.cast(_serverImpl.getState(boolean_state));
										Log.d("AP", String.format("Executed a predicate: %s = %s", boolean_state, Boolean.toString(result)));
										return result;
								}
						};
				}
				public DynamicPredicateComposition and(final String left_hand_side, final String comparator, final double right_hand_side)
				{
						depth++;
						if (depth == 1) // MUST START WITH AN OR
						{
								depth = 0;
								return or(left_hand_side, comparator, right_hand_side);
						}
						predicate = predicate.and(generatePredicate(left_hand_side, comparator, right_hand_side));
						Log.d("AP", String.format("Added an AND: %s %s %.0f", left_hand_side, comparator, right_hand_side));
						return this;
				}
				public DynamicPredicateComposition and(final String boolean_state)
				{
						depth++;
						if (depth == 1) // MUST START WITH AN OR
						{
								depth = 0;
								return or(boolean_state);
						}
						predicate = predicate.and(generatePredicate(boolean_state));
						Log.d("AP", String.format("Added an AND: %s", boolean_state));
						return this;
				}
				public DynamicPredicateComposition or(final String left_hand_side, final String comparator, final double right_hand_side)
				{
						depth++;
						predicate = predicate.or(generatePredicate(left_hand_side, comparator, right_hand_side));
						Log.d("AP", String.format("Added an OR: %s %s %.0f", left_hand_side, comparator, right_hand_side));
						return this;
				}
				public DynamicPredicateComposition or(final String boolean_state)
				{
						depth++;
						predicate = predicate.or(generatePredicate(boolean_state));
						Log.d("AP", String.format("Added an OR: %s", boolean_state));
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
				DynamicPredicateComposition example_predicate_composition = new DynamicPredicateComposition();

				example_predicate_composition
								.or("example_state")
								.or("example_value", "gt", 5.)
								.and("example_value","lt", 20.);
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
