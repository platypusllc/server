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
    private final double LOOKAHEAD_DISTANCE = 5.0;
    private final double SUFFICIENT_PROXIMITY = 3.0;
    private double heading_error_old = 0.0;
    private double heading_error_accum = 0.0;
    double[] rudder_pids;
    double[] thrust_pids;
    double base_thrust, thrust_coefficient;

    private double x_dest, x_source, x_current, y_dest, y_source, y_current, th_full, th_current;
    private double x_projected, y_projected, x_lookahead, y_lookahead;
    private double dx_current, dx_full, dy_current, dy_full, L_current, L_full, dth;
    private double projected_length, distance_from_ideal_line;
    private double dx_lookahead, dy_lookahead;
    private double heading_desired, heading_current, heading_error;
    private double heading_error_deriv, heading_signal;
    private double thrust_signal, angle_from_projected_to_boat, cross_product;


    @Override
    public void update(VehicleServer server, double dt)
    {
        Twist twist = new Twist();
        VehicleServerImpl server_impl = (VehicleServerImpl) server;

        // Get the position of the vehicle
        UtmPose state = server.getPose();
        current_pose = state.pose;

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

            UtmPose destination_UtmPose = server_impl.getCurrentWaypoint();
            if (destination_UtmPose == null)
            {
                server.setVelocity(twist);
                return;
            }
            destination_pose = destination_UtmPose.pose;

            if (current_wp_index == 0)
            {
                source_pose = current_pose.clone();
            }
            else
            {
                UtmPose source_UtmPose = server_impl.getSpecificWaypoint(current_wp_index-1);
                source_pose = source_UtmPose.pose;
            }

            x_dest = destination_pose.getX();
            y_dest = destination_pose.getY();
            x_source = source_pose.getX();
            y_source = source_pose.getY();
        }

        double distanceSq = planarDistanceSq(current_pose, destination_pose);
        if (distanceSq < SUFFICIENT_PROXIMITY*SUFFICIENT_PROXIMITY)
        {
            server_impl.incrementWaypointIndex();
        }
        else
        {
            x_current = current_pose.getX();
            y_current = current_pose.getY();
            heading_current = current_pose.getRotation().toYaw();

            dx_full = x_dest - x_source;
            dx_current = x_dest - x_current;
            dy_full = y_dest - y_source;
            dy_current = y_dest - y_current;
            L_full = Math.sqrt(Math.pow(dx_full, 2.) + Math.pow(dy_full, 2.));
            L_current = Math.sqrt(Math.pow(dx_current, 2.) + Math.pow(dy_current, 2.));
            th_full = Math.atan2(dy_full, dx_full);
            th_current = Math.atan2(dy_current, dx_current);
            dth = Math.abs(AirboatController.minAngleBetween(th_full - th_current));
            projected_length = L_current*Math.cos(dth);
            distance_from_ideal_line = L_current*Math.sin(dth);
            x_projected = x_source + L_current*Math.cos(th_full);
            y_projected = y_source + L_current*Math.sin(th_full);
            x_lookahead = x_projected + LOOKAHEAD_DISTANCE*Math.cos(th_full);
            y_lookahead = y_projected + LOOKAHEAD_DISTANCE*Math.sin(th_full);
            if (L_current + LOOKAHEAD_DISTANCE > L_full)
            {
                x_lookahead = x_dest;
                y_lookahead = y_dest;
            }
            dx_lookahead = x_lookahead - x_current;
            dy_lookahead = y_lookahead - y_current;
            heading_desired = Math.atan2(dy_lookahead, dx_lookahead);
            heading_error = minAngleBetween(heading_current - heading_desired);

            // PID
            rudder_pids = server_impl.getGains(5);
            heading_error_deriv = (heading_error - heading_error_old)/dt;
            if (rudder_pids[1] > 0.0)
            {
                heading_error_accum += dt*heading_error;
            }
            heading_error_old = heading_error;
            heading_signal = rudder_pids[0]*heading_error
                    + rudder_pids[1]*heading_error_accum
                    + rudder_pids[2]*heading_error_deriv;

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
            if (distance_from_ideal_line > SUFFICIENT_PROXIMITY)
            {
                if (cross_product < 0. && minAngleBetween(th_full - heading_current) < 0.)
                {
                    thrust_coefficient = 0.0;
                }
                if (cross_product > 0. && minAngleBetween(th_full - heading_current) < 0.)
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
    public static double planarDistanceSq(Pose3D a, Pose3D b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    public static double minAngleBetween(double algebraic_difference)
    {
        if (Math.abs(algebraic_difference - 2*Math.PI) < Math.abs(algebraic_difference))
        {
            return algebraic_difference - 2*Math.PI;
        }
        if (Math.abs(algebraic_difference + 2*Math.PI) < Math.abs(algebraic_difference))
        {
            return algebraic_difference + 2*Math.PI;
        }
        return algebraic_difference;
    }
}