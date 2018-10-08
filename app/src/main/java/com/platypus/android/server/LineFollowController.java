package com.platypus.android.server;

import android.util.Log;

import com.platypus.crw.VehicleController;
import com.platypus.crw.VehicleServer;
import com.platypus.crw.data.Twist;
import com.platypus.crw.data.UtmPose;
import com.platypus.crw.data.Pose3D;

class LineFollowController implements VehicleController {

    private int last_wp_index = -2;
    private Pose3D source_pose;
    private Pose3D destination_pose;
    private Pose3D current_pose;
    private Pose3D original_pose;
    private boolean original_pose_set = false;
    private long station_keep_time_ms = 0;
    private long start_time = 0;
    private long elapsed_time = 0;
    private boolean station_keeping = false;

    private final double LOOKAHEAD_DISTANCE_BASE = 5.0;
    private double lookahead;
    private final double SUFFICIENT_PROXIMITY = 3.0;
    private double heading_error_old = 0.0;
    private double heading_error_accum = 0.0;
    private double[] rudder_pids;
    private double[] thrust_pids;
    private double base_thrust, thrust_coefficient;

    private double x_dest, x_source, x_current, y_dest, y_source, y_current, th_full, th_current;
    private double x_projected, y_projected, x_lookahead, y_lookahead;
    private double dx_current, dx_full, dy_current, dy_full, L_current, L_full, dth;
    private double L_projected, distance_from_ideal_line;
    private double dx_lookahead, dy_lookahead;
    private double heading_desired, heading_current, heading_error;
    private double heading_error_deriv, heading_signal;
    private double thrust_signal, angle_from_projected_to_boat, cross_product;

    @Override
    public void update(VehicleServer server, double dt)
    {
        Twist twist = new Twist();
        VehicleServerImpl server_impl = (VehicleServerImpl) server;
        String vehicle_type = server_impl.getVehicleType();

        // Get the position of the vehicle
        UtmPose state = server.getPose();
        current_pose = state.pose;

        if (!original_pose_set)
        {
            original_pose = current_pose.clone();
            original_pose_set = true;
        }


        int current_wp_index = server_impl.getCurrentWaypointIndex();
        if (current_wp_index < 0)
        {
            server.setVelocity(twist);
            return;
        }
        if (last_wp_index != current_wp_index)
        {
            heading_error_accum = 0.0; // reset any integral terms
            last_wp_index = current_wp_index;

            // TODO: set station_keep_time_ms to the requested station keep time
            station_keep_time_ms = server_impl.getCurrentWaypointKeepTime();
            Log.v("AP", String.format("new station keep time = %d ms", station_keep_time_ms));

            double[] destination = server_impl.getCurrentWaypoint();
            if (destination == null)
            {
                server.setVelocity(twist);
                return;
            }
            UtmPose destination_UtmPose = new UtmPose(destination);
            destination_pose = destination_UtmPose.pose;

            if (current_wp_index == 0)
            {
                source_pose = current_pose.clone();
            }
            else
            {
                double[] source = server_impl.getSpecificWaypoint(current_wp_index-1);
                UtmPose source_UtmPose = new UtmPose(source);
                source_pose = source_UtmPose.pose;
            }

            x_dest = destination_pose.getX() - original_pose.getX();
            y_dest = destination_pose.getY() - original_pose.getY();
            x_source = source_pose.getX() - original_pose.getX();
            y_source = source_pose.getY() - original_pose.getY();
            dx_full = x_dest - x_source;
            dy_full = y_dest - y_source;
            th_full = Math.atan2(dy_full, dx_full);
            L_full = Math.sqrt(Math.pow(dx_full, 2.) + Math.pow(dy_full, 2.));
        }

        double distanceSq = planarDistanceSq(current_pose, destination_pose);
        if (distanceSq < SUFFICIENT_PROXIMITY*SUFFICIENT_PROXIMITY)
        {

            // check if there is a station keep time associated with the current waypoint
            // if there is, do NOT increment the index until that amount of time has expired
            if (!station_keeping)
            {
                Log.i("AP", String.format("Starting station keeping for %d ms", station_keep_time_ms));
                station_keeping = true;
                start_time = System.currentTimeMillis();
            }
            else
            {
                elapsed_time = System.currentTimeMillis() - start_time;
                Log.v("AP", String.format("Station keep time left = %d ms",  station_keep_time_ms - elapsed_time));
                if (elapsed_time > station_keep_time_ms)
                {
                    Log.i("AP", "Finished station keeping");
                    station_keeping = false;
                    server_impl.incrementWaypointIndex();
                }
            }
        }
        else
        {
            x_current = current_pose.getX() - original_pose.getX();
            y_current = current_pose.getY() - original_pose.getY();
            heading_current = current_pose.getRotation().toYaw();
            dx_current = x_current - x_source;
            dy_current = y_current - y_source;
            L_current = Math.sqrt(Math.pow(dx_current, 2.) + Math.pow(dy_current, 2.));
            th_current = Math.atan2(dy_current, dx_current);
            dth = normalizeAngle(th_full - th_current);
            L_projected = L_current*Math.cos(dth);
            distance_from_ideal_line = L_current*Math.sin(dth);
            x_projected = x_source + L_projected*Math.cos(th_full);
            y_projected = y_source + L_projected*Math.sin(th_full);
            lookahead = LOOKAHEAD_DISTANCE_BASE*(1. - Math.tanh(0.2*Math.abs(distance_from_ideal_line)));
            x_lookahead = x_projected + lookahead*Math.cos(th_full);
            y_lookahead = y_projected + lookahead*Math.sin(th_full);
            if (L_projected + lookahead > L_full)
            {
                x_lookahead = x_dest;
                y_lookahead = y_dest;
            }
            dx_lookahead = x_lookahead - x_current;
            dy_lookahead = y_lookahead - y_current;
            heading_desired = Math.atan2(dy_lookahead, dx_lookahead);
            heading_error = normalizeAngle(heading_desired - heading_current);

            // PID
            rudder_pids = server_impl.getGains(5);
            heading_error_deriv = (heading_error - heading_error_old)/dt;
            Log.v("gyro", String.format("heading error rate = %.2f  rev./sec", heading_error_deriv/2/Math.PI));
            double[] gyro = server_impl.getGyro(); // gyro[2] is yaw rate
            if (rudder_pids[1] > 0.0)
            {
                heading_error_accum += dt*heading_error;
            }
            heading_error_old = heading_error;

            // we only want derivative action when error is low
            double error_envelope = 1.0 - Math.min(1.0, Math.abs(heading_error/(Math.PI/2.)));
            // error_envelope is small when heading error approaches 90 degrees or more, i.e. derivative term is small
            // error_envelope approaches 1 when heading error approaches 0, so drastic derivative terms can take arresting action

            heading_signal = rudder_pids[0]*heading_error
                    + -1*rudder_pids[2]*gyro[2]*error_envelope;
                    // + rudder_pids[1]*heading_error_accum
                    //+ rudder_pids[2]*heading_error_deriv;

            if (Math.abs(heading_signal) > 1.0)
            {
                heading_signal = Math.copySign(1.0, heading_signal);
            }

            // thrust
            thrust_pids = server_impl.getGains(0);
            base_thrust = thrust_pids[0];
            angle_from_projected_to_boat = Math.atan2(y_projected - y_current,
                    x_projected - x_current);
            cross_product = Math.cos(th_full)*Math.sin(angle_from_projected_to_boat) -
                    Math.cos(angle_from_projected_to_boat)*Math.sin(th_full);
            thrust_coefficient = 1.0;

            // check vehicle type. Modify turning in place behavior.
            if (!vehicle_type.equals("VECTORED"))
            {
                // propboats should turn in place if they are off by more than 45 degrees
                if (Math.abs(heading_error) * 180. / Math.PI > 45.0)
                {
                    thrust_coefficient = 0.0;
                }
            }
            thrust_signal = thrust_coefficient*base_thrust;

            twist.dx(thrust_signal);
            twist.drz(heading_signal);
            server.setVelocity(twist);
        }
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
    public static double planarDistanceSq(Pose3D a, Pose3D b) 
    {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }
  
    public static double normalizeAngle(double angle) 
    {
        while (angle > Math.PI)
            angle -= 2 * Math.PI;
        while (angle < -Math.PI)
            angle += 2 * Math.PI;
        return angle;
    }
}