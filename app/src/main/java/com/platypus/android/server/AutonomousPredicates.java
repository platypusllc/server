package com.platypus.android.server;

import android.support.v4.widget.ViewDragHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import java.util.concurrent.ScheduledThreadPoolExecutor;


/**
 * Created by jason on 8/4/17.
 *
 * https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html
 * https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html
 * https://developer.android.com/studio/preview/install-preview.html
 * http://www.java2s.com/Tutorials/Java_Lambda/java.util.function/BooleanSupplier/BooleanSupplier_example.htm
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
						if (_test.getAsBoolean()) _action.accept(null);
						if (!_isPermanent)
						{
								triggered_actions_map.get(_id).cancel(true);
								triggered_actions_map.remove(_id);
						}
				}
		}

		public AutonomousPredicates(VehicleServerImpl server)
		{
				_serverImpl = server;
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
		}

		void displayActions()
		{
				// display some kind of summary of the current trigger definitions in the debug activity
		}


		/*
		public static <X, R> void processElements
						(Iterable<X> source_elements,
						 Predicate<X> tester,
						 Function<X, R> retriever,
						 Consumer<R> performer)
		{
				for (X element : source_elements) { // iterate over input elements
						if (tester.test(element)) { // apply the predicate test
								R data = retriever.apply(element); // create data from input element
								performer.accept(data); // consume the data in some way
						}
				}
		}


		processElements(
		roster,  // collection of input elements
    p -> p.getGender() == Person.Sex.MALE  // boolean evaluation
        && p.getAge() >= 18
        && p.getAge() <= 25,
    p -> p.getEmailAddress(),  // create data from input element
    email -> System.out.println(email)  // consume the data
    );
		 */

}
