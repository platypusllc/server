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
		private static double relative_coords_angle;
		private static int median_filter_length = 20;
		private static SharedPreferences mPrefs;
		private static VehicleServerImpl server;

		Decawave(VehicleServerImpl _server, Context context)
		{
				server = _server;
				mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

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
				a0 = new UtmPose(a0_latlng);
				a1 = new UtmPose(a1_latlng);
				a2 = new UtmPose(a2_latlng);
				relative_coords_angle = Math.atan2(a1.pose.getY()-a0.pose.getY(), a1.pose.getX()-a0.pose.getX());
		}

		static void newDecawaveDistances(double[] new_distances) throws Exception
		{
				if (new_distances.length != 3) throw new Exception("Did not receive 3 distances properly");

				// use median filter to estimate relative distances
				double[] filtered_distances = elementWiseMedianFilter(new_distances);

				// 2D trilateration to find relative x, y using filtered distances
				double[] xy = trilateration(filtered_distances);
				double x = xy[0];
				double y = xy[1];
				Log.d("decawave", String.format("relative x = %f, y = %f", x, y));

				// transform the relative x, y into easting and northing respectively using the anchors' UtmPose values
				double drel = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
				double angle_within_relative_coords = Math.atan2(y, x);
				double asum = LineFollowController.normalizeAngle(relative_coords_angle + angle_within_relative_coords);
				double easting = a0.pose.getX() + drel*Math.cos(asum);
				double northing = a0.pose.getY() + drel*Math.sin(asum);

				// update the server's current pose using the estimated easting and northing
				UtmPose pose = new UtmPose(new Pose3D(easting, northing, 0, 0, 0, 0), a0.origin);
				Log.d("decawave", pose.toString());
				server.filter.gpsUpdate(pose, System.currentTimeMillis());
		}

		private static double[] trilateration(double[] d)
		{
				double[] d21 = new double[]{a1.pose.getX()-a0.pose.getX(), a1.pose.getY()-a0.pose.getY()};
				double[] d31 = new double[]{a2.pose.getX()-a0.pose.getX(), a2.pose.getY()-a0.pose.getY()};
				double d21norm = Math.sqrt(d21[0]*d21[0] + d21[1]*d21[1]);
				double[] ex  = new double[]{d21[0]/d21norm, d21[1]/d21norm};
				double i = ex[0]*d31[0] + ex[1]*d31[1];
				double[] d31_min_iex = new double[]{d31[0] - i*ex[0], d31[1] - i*ex[1]};
				double d31_min_iex_norm = Math.sqrt(d31_min_iex[0]*d31_min_iex[0] + d31_min_iex[1]*d31_min_iex[1]);
				double[] ey = new double[]{d31_min_iex[0]/d31_min_iex_norm, d31_min_iex[1]/d31_min_iex_norm};
				double j = ey[0]*d31[0] + ey[1]*d31[1];
				double x = (d[0]*d[0] - d[1]*d[1] + d21norm*d21norm)/(2*d21norm);
				double y = (d[0]*d[0] - d[2]*d[2] + i*i + j*j)/(2*j) - i/j*x;
				double[] result = new double[]{x*ex[0] + y*ey[0], x*ex[1] + y*ey[1]};
				return result;
		}



		private static double[] elementWiseMedianFilter(double[] new_distances)
		{

				// TODO: keep track of last median result. If new value has jumped, throw away new value

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
				Double[] d1s = d1_history.toArray(new Double[0]);
				Double[] d2s = d2_history.toArray(new Double[0]);
				Double[] d3s = d3_history.toArray(new Double[0]);
				Arrays.sort(d1s);
				Arrays.sort(d2s);
				Arrays.sort(d3s);
				double m1,m2,m3;

				if (d1s.length % 2 == 0) // even
				{
						m1 = (d1s[d1s.length/2] + d1s[d1s.length/2 - 1])/2;
						m2 = (d2s[d2s.length/2] + d2s[d2s.length/2 - 1])/2;
						m3 = (d3s[d3s.length/2] + d3s[d3s.length/2 - 1])/2;
				}
				else // odd
				{
						m1 = d1s[d1s.length/2];
						m2 = d2s[d2s.length/2];
						m3 = d3s[d3s.length/2];
				}

				// return double[] of filtered d1, d2, d3
				Log.d("decawave", String.format("filtered = %f, %f, %f", m1, m2, m3));
				return new double[]{m1, m2, m3};
		}
}


