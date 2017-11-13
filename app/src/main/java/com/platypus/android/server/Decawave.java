package com.platypus.android.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.platypus.crw.data.Pose3D;
import com.platypus.crw.data.UtmPose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by jason on 11/3/17.
 * IMPORTANT: ANCHOR 1 MUST BE THE X AXIS, AND THE ANCHORS MUST FORM A RIGHT-HAND COORDINATE SYSTEM
 */


class Decawave
{
		private static List<Double> d1_history = new ArrayList<>();
		private static List<Double> d2_history = new ArrayList<>();
		private static List<Double> d3_history = new ArrayList<>();
		private static UtmPose a0;
		private static UtmPose a1;
		private static UtmPose a2;
		private static double[] d21; // relative coordinate pure x
		private static double[] d31; // relative coordinate pure y
		private static double[] median_histories = new double[3];
		private static double relative_coords_angle;
		private static int median_filter_length = 20;
		private double REJECTION_DISTANCE = 5.0; // meters change in a single step, beyond which we throw out the measurement
		private VehicleServerImpl server;
		private ScheduledThreadPoolExecutor pool; 
		String logTag = "decawave";

		Decawave(VehicleServerImpl _server, Context context)
		{
				server = _server;
				SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

				// read in size of median filter and anchor lat/lng locations from preferences
				median_filter_length = Integer.valueOf(mPrefs.getString("pref_decawave_anchor_median_filter_length", "20"));
				String a0_latlng_string = mPrefs.getString("pref_decawave_anchor_0_latlng", "0.0,0.0");
				String a1_latlng_string = mPrefs.getString("pref_decawave_anchor_1_latlng", "0.0,0.0");
				String a2_latlng_string = mPrefs.getString("pref_decawave_anchor_2_latlng", "0.0,0.0");
				String[] a0_chunks = a0_latlng_string.split(",");
				String[] a1_chunks = a1_latlng_string.split(",");
				String[] a2_chunks = a2_latlng_string.split(",");
				for (int i = 0; i < 2; i++)
				{
						a0_chunks[i] = a0_chunks[i].trim();
						a1_chunks[i] = a1_chunks[i].trim();
						a2_chunks[i] = a2_chunks[i].trim();
				}
				double[] a0_latlng = new double[]{Double.valueOf(a0_chunks[0]), Double.valueOf(a0_chunks[1])};
				double[] a1_latlng = new double[]{Double.valueOf(a1_chunks[0]), Double.valueOf(a1_chunks[1])};
				double[] a2_latlng = new double[]{Double.valueOf(a2_chunks[0]), Double.valueOf(a2_chunks[1])};
				/*
				double[] a0_latlng = new double[]{45.4033802, 10.9993635}; // origin
				double[] a1_latlng = new double[]{45.4035170, 10.9992842}; // +x
				double[] a2_latlng = new double[]{45.4033402, 10.9992327}; // +y
				*/
				a0 = new UtmPose(a0_latlng);
				a1 = new UtmPose(a1_latlng);
				a2 = new UtmPose(a2_latlng);
				d21 = new double[]{Math.sqrt(Math.pow(a1.pose.getX()-a0.pose.getX(), 2) + Math.pow(a1.pose.getY()-a0.pose.getY(), 2)), 0};
				d31 = new double[]{0, Math.sqrt(Math.pow(a2.pose.getX()-a0.pose.getX(), 2) + Math.pow(a2.pose.getY()-a0.pose.getY(), 2))};

				relative_coords_angle = Math.atan2(a1.pose.getY()-a0.pose.getY(), a1.pose.getX()-a0.pose.getX());

				// Uncomment the following to simulate decawave distance signals
				//pool = new ScheduledThreadPoolExecutor(2);
				//pool.scheduleAtFixedRate(new SimulatedDecawave(), 0, 100, TimeUnit.MILLISECONDS);
		}

		void newDecawaveDistances(double[] new_distances) throws Exception
		{
				if (new_distances.length != 3) throw new Exception("Did not receive 3 distances properly");

				// use median filter to estimate relative distances
				double[] filtered_distances = elementWiseMedianFilter(new_distances);

				// 2D trilateration to find relative x, y using filtered distances
				double[] xy = trilateration(filtered_distances);
				double x = xy[0];
				double y = xy[1];
				Log.d(logTag, String.format("relative x = %f, y = %f", x, y));

				// transform the relative x, y into easting and northing respectively using the anchors' UtmPose values
				double drel = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
				double angle_within_relative_coords = Math.atan2(y, x);
				double asum = LineFollowController.normalizeAngle(relative_coords_angle + angle_within_relative_coords);
				double easting = a0.pose.getX() + drel*Math.cos(asum);
				double northing = a0.pose.getY() + drel*Math.sin(asum);

				// update the server's current pose using the estimated easting and northing
				UtmPose pose = new UtmPose(new Pose3D(easting, northing, 0, 0, 0, 0), a0.origin);
				Log.d(logTag, pose.toString());
				server.filter.gpsUpdate(pose, System.currentTimeMillis());
		}

		private double[] trilateration(double[] d)
		{

				Log.v(logTag, String.format("d21 = %s", Arrays.toString(d21)));
				Log.v(logTag, String.format("d31 = %s", Arrays.toString(d31)));
				double d21norm = Math.sqrt(d21[0]*d21[0] + d21[1]*d21[1]);
				double[] ex  = new double[]{d21[0]/d21norm, d21[1]/d21norm};
				double i = ex[0]*d31[0] + ex[1]*d31[1];
				double[] d31_min_iex = new double[]{d31[0] - i*ex[0], d31[1] - i*ex[1]};
				double d31_min_iex_norm = Math.sqrt(d31_min_iex[0]*d31_min_iex[0] + d31_min_iex[1]*d31_min_iex[1]);
				double[] ey = new double[]{d31_min_iex[0]/d31_min_iex_norm, d31_min_iex[1]/d31_min_iex_norm};
				double j = ey[0]*d31[0] + ey[1]*d31[1];
				double x = (d[0]*d[0] - d[1]*d[1] + d21norm*d21norm)/(2*d21norm);
				double y = (d[0]*d[0] - d[2]*d[2] + i*i + j*j)/(2*j) - i/j*x;
				return new double[]{x*ex[0] + y*ey[0], x*ex[1] + y*ey[1]};
		}

		private double median(Double[] x)
		{
				double[] xx = new double[x.length];
				for (int i = 0; i < x.length; i++)
				{
						xx[i] = x[i];
				}
				return median(xx);
		}
		private double median(double[] x)
		{
				double m;
				Arrays.sort(x);
				if (x.length % 2 == 0)
				{
						m = (x[x.length/2] + x[x.length/2 - 1])/2;
				}
				else
				{
						m = x[x.length/2];
				}
				return m;
		}

		private double[] elementWiseMedianFilter(double[] new_distances)
		{

				// TODO: keep track of last median result. If new value has jumped, throw away new value
				if (median_filter_length <= 0) return new_distances;

				for (int i = 0; i < 3; i++)
				{
						if (new_distances[i] == 0.0)
						{
								Log.w(logTag, String.format("Rejected new distances due to a%d pure zero", i));
								return median_histories;
						}
				}

				/*
				if (d1_history.size() > 5)
				{
						for (int i = 0; i < 3; i++)
						{
								if (Math.abs(new_distances[i] - median_histories[i]) > REJECTION_DISTANCE)
								{
										Log.w(logTag, String.format("Rejected new distances due to a%d large jump", i));
										return median_histories;
								}
						}
				}
				*/

				// push in new value to static lists
				// check size of dX_history objects against median filter length
				// if larger than filter length, remove oldest
				d1_history.add(new_distances[0]);
				d2_history.add(new_distances[1]);
				d3_history.add(new_distances[2]);
				if (d1_history.size() > median_filter_length) d1_history.remove(0);
				if (d2_history.size() > median_filter_length) d2_history.remove(0);
				if (d3_history.size() > median_filter_length) d3_history.remove(0);

				// calculate median of dX_history object
				Double[][] histories = new Double[][]{d1_history.toArray(new Double[0]),
																							d2_history.toArray(new Double[0]),
																							d3_history.toArray(new Double[0])};
				double[] m = new double[3];
				for (int i = 0; i < 3; i++)
				{
						m[i] = median(histories[i]);
						median_histories[i] = m[i];
				}

				// return double[] of filtered d1, d2, d3
				Log.d("decawave", String.format("filtered = %f, %f, %f", m[0], m[1], m[2]));
				return m;
		}

		class SimulatedDecawave implements Runnable
		{
				double x1 = d21[0];
				double y2 = d31[1];
				double x = 0; // relative x
				double y = 0; // relative y
				double d1 = Math.sqrt(x*x + y*y);
				double d2 = Math.sqrt((x1-x)*(x1-x) + y*y);
				double d3 = Math.sqrt(x*x + (y2-y)*(y2-y));
				long t0 = System.currentTimeMillis();
				Random gen = new Random();
				@Override
				public void run()
				{
						x = 5 + 2.5*Math.cos((System.currentTimeMillis()-t0)/1000./2.);
						y = 5 + 2.5*Math.sin((System.currentTimeMillis()-t0)/1000./2.);
						d1 = Math.sqrt(x*x + y*y) + gen.nextFloat()-0.5;
						d2 = Math.sqrt((x1-x)*(x1-x) + y*y) + gen.nextFloat()-0.5;
						d3 = Math.sqrt(x*x + (y2-y)*(y2-y)) + gen.nextFloat()-0.5;
						/*
						if (gen.nextFloat() < 0.25)
						{
								// randomly horrible jump in value
								Log.w(logTag, "RANDOMLY HORRIBLE DISTANCE SIGNAL");
								d2 = 0.0;
						}
						*/
						try
						{
								newDecawaveDistances(new double[]{d1, d2, d3});
						}
						catch (Exception e)
						{

						}
				}
		}
}


