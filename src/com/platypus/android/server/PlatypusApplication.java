package com.platypus.android.server;

import android.app.Application;

import com.platypus.crw.data.UtmPose;

/**
 * Created by hnx on 6/23/14.
 */
public class PlatypusApplication extends Application{
    private String failsafe_IPAddress;
    private UtmPose[] waypoints;

    public String getFailsafe_IPAddress() {
        return failsafe_IPAddress;
    }

    public void setFailsafe_IPAddress(String failsafe_IPAddress) {
        this.failsafe_IPAddress = failsafe_IPAddress;
    }

    public UtmPose[] getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(UtmPose[] waypoints) {
        this.waypoints = waypoints;
    }
}
