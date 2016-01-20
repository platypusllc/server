package com.platypus.android.server.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.platypus.crw.AsyncVehicleServer;

/**
 * View that plots an autoscaling output of sensor updates as a graph.
 *
 * @author pkv
 */
public class SensorView extends View {
    AsyncVehicleServer mServer = null;

    public SensorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public synchronized void getVehicleServer(AsyncVehicleServer server) {
        mServer = server;
    }

    public synchronized void setVehicleServer(AsyncVehicleServer server) {
        mServer = server;
    }
}
