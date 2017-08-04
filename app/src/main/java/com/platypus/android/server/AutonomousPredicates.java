package com.platypus.android.server;

import android.support.v4.widget.ViewDragHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
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
		JSONObject example_JSONObject = new JSONObject();
		List<TriggeredAction> triggeredActions = new ArrayList<>();
		ScheduledThreadPoolExecutor poolExecutor = new ScheduledThreadPoolExecutor(10);

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
		}

		static void readDefaultFile()
		{
				// read the default text file and generate a JSONObject with the file contents
		}

		static void parseActionDefinition(JSONObject definition)
		{
				// add triggered actions to the list
		}

		static void displayActions()
		{
				// display some kind of summary of the current trigger definitions in the debug activity
		}

		class TriggeredAction implements Runnable
		{
				BooleanSupplier _test;
				Consumer<VehicleServerImpl> _action;

				public TriggeredAction(BooleanSupplier test, Consumer<VehicleServerImpl> action)
				{
						_test = test;
						_action = action;
				}

				@Override
				public void run()
				{
						if (_test.getAsBoolean()) _action.accept(_serverImpl);
				}
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
