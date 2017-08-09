package com.platypus.android.server;

import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import java.util.Scanner;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import java.util.function.Predicate;


/**
 * Created by jason on 8/4/17.
 *
 * https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html
 * https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html
 * https://developer.android.com/studio/preview/install-preview.html
 * http://www.java2s.com/Tutorials/Java_Lambda/java.util.function/BooleanSupplier/BooleanSupplier_example.htm
 * "currying"
 * https://developer.android.com/reference/org/json/JSONTokener.html
 */

public class AutonomousPredicates
{
		VehicleServerImpl _serverImpl;
		String logTag = "AP";
		static long ap_count = 0;
		Map<Long, ScheduledFuture> triggered_actions_map = new HashMap<>();
		ScheduledThreadPoolExecutor poolExecutor = new ScheduledThreadPoolExecutor(4);
		// TODO: do we want something that can increase and decrease the thread pool, rather than fixed?

		class TriggeredAction implements Runnable
		{
				long _id;
				boolean _isPermanent; // if true, this triggered action Runnable will be canceled after it runs once
				Predicate<Void> _test; // meant to call methods in the VehicleServerImpl
				String _action; // meant to call methods in the VehicleServerImpl

				public TriggeredAction(Predicate<Void> test, String action, boolean isPermanent)
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
						Log.d(logTag, String.format("Task # %d running...", _id));
						if (_test.test(null))
						{
								Log.i(logTag, String.format("Task # %d test returned TRUE, executing task...", _id));
								_serverImpl.performAction(_action);
								if (!_isPermanent)
								{
										Log.i(logTag, String.format("Task # %d completed, removing...", _id));
										triggered_actions_map.get(_id).cancel(true);
										triggered_actions_map.remove(_id);
								}
						}
						else
						{
								Log.i(logTag, String.format("Task # %d test returned FALSE", _id));
						}
				}
		}

		class DynamicPredicateComposition
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

				true/false && {only matters if other thing is true}
				true/false || {only matters if other thing is false}

				TODO: Need to completely reproduce conjunctive normal form: (A or B or C) AND (C or D or E)

			  ANOTHER VERY IMPORTANT NOTE
			  The "builder pattern" is used here, and that has some consequences.
			  Each call to and() or or() on an individual DynamicPredicateComposition instance enforces
			    nested parenthesis!
			  For example dpc.or(test 1).or(test 2).and(test 3) is equivalent to
			    (test 1 or test 2) and test 3
			  If you want parenthesis to appear around test 2 and test 3 instead (which definitely changes
			    outcome of the test!), you need to use more than one DynamicPredicateComposition instance.
			  dpc1.or(test 1);
			  dpc2.or(test 2).and(test 3);
			  dpc1.build().or(dpc2.build()); --> this is now equivalent to test 1 or (test 2 and test 3)
				*/
				int depth = 0; // can serve as unique ID for the predicates
				Predicate<Void> predicate = new Predicate<Void>()
				{
						@Override
						public boolean test(Void v)
						{
								Log.v(logTag, "Executing the original default predicate");
								return false; // must start as false and composition must start with an OR
						}
				};
				public Predicate<Void> build()
				{
						Log.d(logTag, "Building composite predicate...");
						// TODO: duplicate predicate so we can reuse DynamicPredicateComposition objects
						return predicate;
				}
				private Predicate<Void> generatePredicate(final String left_hand_side, final String comparator, final double right_hand_side)
				{
						Log.v(logTag, String.format("Generating new predicate: %s", comparator));
						return new Predicate<Void>()
						{
								String definition = String.format("%s %s %.0f", left_hand_side, comparator, right_hand_side);
								@Override
								public boolean test(Void v) // the input to this is never used
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
										Log.d(logTag, String.format("Executed a predicate: %s = %s", definition, Boolean.toString(result)));
										return result;
								}
						};
				}
				private Predicate<Void> generatePredicate(final String boolean_state)
				{
						Log.v(logTag, String.format("Generating new boolean only predicate"));
						return new Predicate<Void>()
						{
								@Override
								public boolean test(Void v) // the input to this is never used
								{
										Boolean result = Boolean.class.cast(_serverImpl.getState(boolean_state));
										Log.d(logTag, String.format("Executed a predicate: %s = %s", boolean_state, Boolean.toString(result)));
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
						Log.d(logTag, String.format("Added an AND: %s %s %.0f", left_hand_side, comparator, right_hand_side));
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
						Log.d(logTag, String.format("Added an AND: %s", boolean_state));
						return this;
				}
				public DynamicPredicateComposition or(final String left_hand_side, final String comparator, final double right_hand_side)
				{
						depth++;
						predicate = predicate.or(generatePredicate(left_hand_side, comparator, right_hand_side));
						Log.d(logTag, String.format("Added an OR: %s %s %.0f", left_hand_side, comparator, right_hand_side));
						return this;
				}
				public DynamicPredicateComposition or(final String boolean_state)
				{
						depth++;
						predicate = predicate.or(generatePredicate(boolean_state));
						Log.d(logTag, String.format("Added an OR: %s", boolean_state));
						return this;
				}

				// TODO: useful building utilities
				/*
				List of ideas:
				1) inInterval(left_hand_side, low value, high value) --> low <= value <= high
				2) near(lat, lng, rad) --> current location is within rad meters of (lat,lng) location
				3) inConvexHull(vertices[]) --> current location is inside polygon defined by these
				*/
		}

		private void loadFromFile(String filename)
		{
				final File file = new File(Environment.getExternalStorageDirectory() + "/platypus/" + filename);
				/*
				1) read all lines in the human readable file, put them into a single string
				2) JSONTokener parses human readable string into a JSONObject with all default behaviors
				3) Split up the one large JSONObject and parse each task/behavior
				*/

				Scanner fileScanner;
				try
				{
						fileScanner = new Scanner(file);
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
						return;
				}

				// gather all the default behaviors
				StringBuffer buffer = new StringBuffer();
				JSONObject[] tasks = null;
				if (file.exists())
				{
						while (fileScanner.hasNext())
						{
								buffer.append(fileScanner.nextLine());
						}
				}
				String human_string = buffer.toString();
				JSONTokener tokener = new JSONTokener(human_string);
				JSONObject file_json;
				try
				{
						file_json = (JSONObject)tokener.nextValue();
						Log.v(logTag, file_json.toString(2));
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
						return;
				}

				// parse each behavior and generate tasks
				Iterator<String> file_keys = file_json.keys();
				String key;
				while (file_keys.hasNext())
				{
						key = file_keys.next();
						Log.i(logTag, String.format("Next key: %s", key));
						try
						{
								JSONObject task_json = (JSONObject)file_json.get(key);
								Log.v(logTag, task_json.toString(2));
								createTask(task_json);
						}
						catch (Exception e)
						{
								Log.e(logTag, e.getMessage());
								continue;
						}
				}
		}

		private void createTask(JSONObject definition)
		{
				String key;
				Iterator<String> task_keys = definition.keys();
				try
				{
						while (task_keys.hasNext())
						{
								key = task_keys.next();
								Log.i(logTag, String.format("Next task key: %s", key));
								switch(key)
								{
										case "action":
												break;
										case "trigger":
												break;
										case "interval":
												break;
										case "ends":
												break;
										default:
												break;
								}
						}

						// create new triggered action
						// Need Predicate, string for action,
						//TriggeredAction ta = new TriggeredAction(null, task_map.get("action"), task_map.get("ends"));

						// put the triggered action task into the scheduler queue and store its ScheduledFuture in the HashMap (so we can cancel it later)
				}
				catch (Exception e)
				{
						Log.e(logTag, e.getMessage());
				}
		}

		public AutonomousPredicates(VehicleServerImpl server)
		{
				_serverImpl = server;
				Log.w(logTag, "**** AutonomousPredicates constructor ****");
				loadFromFile("default_behaviors.txt");

				// example
				/*
				JSONObject example_JSONObject = new JSONObject();
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
				*/

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
				try
				{
						Log.i("AP", definition.toString(2));
				}
				catch (Exception e)
				{
						Log.e("AP", e.getMessage());
				}

				// parse the JSON to generated the necessary stuff


				double hz = 2.;
				Double ms_delay = 1./hz*1000.;

				// add triggered actions to the list

				Log.w("AP", "****** NEW EXAMPLE ******");
				DynamicPredicateComposition example_predicate_composition_1 = new DynamicPredicateComposition();
				DynamicPredicateComposition example_predicate_composition_2 = new DynamicPredicateComposition();

				// example_state OR (example_value < 20 AND example_value > 5)
				example_predicate_composition_1.or("example_state");
				example_predicate_composition_2
								.or("example_value", "lt", 20.)
								.and("example_value","gt", 5.);
				TriggeredAction ta = new TriggeredAction
								(
												example_predicate_composition_1.build()
																.or(example_predicate_composition_2.build()),
												"example_action",
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
