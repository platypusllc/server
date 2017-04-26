package com.platypus.android.server;

import android.util.Log;

import com.platypus.crw.VehicleController;
import com.platypus.crw.VehicleServer;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.UtmPose;

import java.util.Timer;
import java.util.TimerTask;

import com.platypus.crw.data.Pose3D;

/**
 * A library of available navigation controllers that are accessible through the
 * high-level API.
 * 
 * @author pkv
 * @author kss
 * 
 */
public enum AirboatController {

	POINT_AND_SHOOT(new VehicleController() {
		// variable for monitoring previous destination angle for error calculation 
		private double prev_angle_destination = 0;
		
		// variables for buffer and integration term
		final int BUFFER_SIZE = 100;
		double[] buffer = new double[BUFFER_SIZE];
		int bIndex = 0;
		double bSum = 0;

		@Override
		public void update(VehicleServer server, double dt) {

			Twist twist = new Twist();
			VehicleServerImpl server_impl = (VehicleServerImpl) server;
			
			// Get the position of the vehicle
			UtmPose state = server.getPose();
			Pose3D pose = state.pose;

			// Get the current waypoint, or return if there are none
			UtmPose waypoint_UtmPose = server_impl.getCurrentWaypoint();
			if (waypoint_UtmPose == null)
			{
				server.setVelocity(twist);
				return;
			}
			Pose3D waypoint = waypoint_UtmPose.pose;

			double distanceSq = planarDistanceSq(pose, waypoint);
			Log.d("POINT_AND_SHOOT:", String.format("%s vs. %s --> distanceSq = %.2f", pose.toString(), waypoint.toString(), distanceSq));

			if (distanceSq <= (3*3))
			{
				Log.i("POINT_AND_SHOOT:", "Arrived Waypoint");
				// if reached the target, reset the buffer and previous angle
				bIndex = 0;
				bSum = 0;
				buffer = new double[BUFFER_SIZE];
				prev_angle_destination = 0;
				
				// If we are "at" the destination, de-queue current waypoint

				server_impl.incrementWaypointIndex();
			}
			else
			{
				// ANGLE CONTROL SEGMENT

				// find destination angle between boat and waypoint position
				double angle_destination = angleBetween(pose, waypoint);
				
				// use compass information to get heading of the boat
				double angle_boat = pose.getRotation().toYaw();
				double angle_between = normalizeAngle(angle_destination - angle_boat);
				
				// use gyro information from arduino to get rotation rate of heading
				double[] _gyroReadings = ((VehicleServerImpl) server).getGyro();
				double drz = _gyroReadings[2];
				
				// use previous data to get rate of change of destination angle
				double angle_destination_change = (angle_destination - prev_angle_destination) / dt;
				double error = angle_between;
				bIndex++;
				if (bIndex == BUFFER_SIZE)
					bIndex = 0;
				bSum -= buffer[bIndex];
				bSum += error;
				buffer[bIndex] = error;
				
				// Define PID constants and boundary pos constants
				double[] rudder_pids = server_impl.getGains(5);
				
				double pos = rudder_pids[0]*(angle_between) + rudder_pids[2]*(angle_destination_change - drz) + rudder_pids[1]*bSum;
				
				// Ensure values are within bounds
				if (pos < -1.0)
					pos = -1.0;
				else if (pos > 1.0)
					pos = 1.0;
				
				// THRUST CONTROL SEGMENT
				double[] thrust_pids = server_impl.getGains(0);
				double thrust = 1.0 * thrust_pids[0]; // Use a normalized thrust value of 1.0.
				
				// update twist
				twist.dx(thrust);
				twist.drz(pos);

				// update angle error
				prev_angle_destination = angle_destination;
				// Set the desired velocity
				server.setVelocity(twist);
				
			}
		}
	}),
	/**
	 * This controller simply cuts all power to the boat, letting it drift
	 * freely. It will not attempt to hold position or steer the boat in any
	 * way, and completely ignores the waypoint.
	 */
	STOP(new VehicleController() {
		@Override
		public void update(VehicleServer server, double dt) {
			server.setVelocity(new Twist());

		}
	});

	/**
	 * The controller implementation associated with this library name.
	 */
	public final VehicleController controller;

	/**
	 * Instantiates a library entry with the specified controller.
	 * 
	 * @param controller
	 *            the controller to be used by this entry.
	 */
	AirboatController(VehicleController controller) {
		this.controller = controller;
	}

	/**
	 * Takes an angle and shifts it to be in the range -Pi to Pi.
	 * 
	 * @param angle
	 *            an angle in radians
	 * @return the same angle as given, normalized to the range -Pi to Pi.
	 */
	public static double normalizeAngle(double angle) {
		while (angle > Math.PI)
			angle -= 2 * Math.PI;
		while (angle < -Math.PI)
			angle += 2 * Math.PI;
		return angle;
	}

	/**
	 * Computes the squared XY-planar Euclidean distance between two points.
	 * Using the squared distance is cheaper (it avoid a sqrt), and for constant
	 * comparisons, it makes no difference (just square the constant).
	 * 
	 * @param a
	 *            the first pose
	 * @param b
	 *            the second pose
	 * @return the XY-planar Euclidean distance
	 */
	public static double planarDistanceSq(Pose3D a, Pose3D b) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		return dx * dx + dy * dy;
	}

	/**
	 * Computes a direction vector from a source pose to a destination pose, as
	 * projected onto the XY-plane. Returns an angle representing the direction
	 * in the XY-plane to take if starting at the source pose to reach the
	 * destination pose.
	 * 
	 * @param src
	 *            the source (starting) pose
	 * @param dest
	 *            the destination (final) pose
	 * @return an angle in the XY-plane (around +Z-axis) to get to destination
	 */
	public static double angleBetween(Pose3D src, Pose3D dest) {
		return Math.atan2((dest.getY() - src.getY()),
				(dest.getX() - src.getX()));
	}
}