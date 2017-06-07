package com.platypus.android.server;

import org.jscience.geography.coordinates.UTM;
import javax.measure.unit.SI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Created by jason on 6/7/17.
 */

public class Crumb
{
		// Instance fields
		long index;
		UTM location;
		double g = 0.; // initialize with zero
		double h; // run-time initialize e.g. set to the distance to the goal (if using A*)
		List<Long> successors = new ArrayList<>();
		long parent;

		// Instance methods
		public Crumb(long _index, UTM _location)
		{
				index = _index;
				location = _location;
		}
		public void setG(double _g) { g= _g; }
		public double getG() { return g; }
		public void setParent(long _parent) { parent = _parent; }
		public long getParent() { return parent; }
		public double getCost() { return g + h; }
		public long getIndex() { return index; }
		public UTM getLocation() { return location; }
		public List<Long> getSuccessors() { return successors; }

		// Static fields
		final static double MAX_NEIGHBOR_DISTANCE = 10;
		public static Map<Long, Crumb> crumbs_by_index = new HashMap<>();
		public static Map<Long, Double> distance_to_goal = new HashMap<>();
		public static Map<Long, Map<Long, Double>> pairwise_distances = new HashMap<>();
		public static Map<Long, List<Long>> neighbors = new HashMap<>();

		// Static methods
		public static double distanceBetweenUTM(UTM location_i, UTM location_j)
		{
				return Math.sqrt(
								location_i.eastingValue(SI.METER)*location_j.eastingValue(SI.METER) +
												location_i.northingValue(SI.METER)*location_j.northingValue(SI.METER)
				);
		}

		public static double distanceBetweenCrumbs(long index_i, long index_j)
		{
				UTM location_i = crumbs_by_index.get(index_i).getLocation();
				UTM location_j = crumbs_by_index.get(index_j).getLocation();
				return distanceBetweenUTM(location_i, location_j);
		}

		public static long newCrumb(UTM _location)
		{
				// initialize objects
				long new_index = crumbs_by_index.size();
				Crumb new_crumb = new Crumb(new_index, _location);
				crumbs_by_index.put(new_index, new_crumb);
				pairwise_distances.put(new_index, new HashMap<Long, Double>());
				neighbors.put(new_index, new ArrayList<Long>());

				// calculate pairwise distances and neighbors
				for (Map.Entry<Long, Crumb> entry_i : crumbs_by_index.entrySet())
				{
						for (Map.Entry<Long, Crumb> entry_j : crumbs_by_index.entrySet())
						{
								long index_i = entry_i.getKey();
								long index_j = entry_j.getKey();

								// if a Crumb is being compared to itself
								// OR
								// if a calculation was previously performed for pair (i,j)
								if (index_i == index_j || pairwise_distances.get(index_i).containsKey(index_j))
								{
										continue; // don't perform the calculations
								}

								double pairwise_distance = distanceBetweenCrumbs(index_i, index_j);
								pairwise_distances.get(index_i).put(index_j, pairwise_distance);
								if (pairwise_distance <= MAX_NEIGHBOR_DISTANCE)
								{
										neighbors.get(index_i).add(index_j);
								}
						}
				}
				return new_index;
		}

		public static List<Long> straightHome(UTM start, UTM goal)
		{
				// Simple: go straight home from the start
				List<Long> path_sequence = new ArrayList<>();
				long start_index = newCrumb(start);
				long goal_index = newCrumb(goal);
				path_sequence.add(start_index);
				path_sequence.add(goal_index);
				return path_sequence;
		}

		public static List<Long> aStar(UTM start, UTM goal)
		{
				List<Long> path_sequence = new ArrayList<>();
				long start_index = newCrumb(start);
				long goal_index = newCrumb(goal);

				// make sure start is reachable (i.e. it has at least one neighbor)

				// make sure goal is reachable (i.e. it has at least one neighbor)

				// for each crumb, calculate distance to goal, fill in distance_to_goal Map

				// TODO: FILL IN CODE

				return path_sequence;
		}
}
