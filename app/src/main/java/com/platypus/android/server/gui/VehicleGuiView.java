package com.platypus.android.server.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.platypus.crw.AsyncVehicleServer;

/**
 * Generic base class for Views that interact with the VehicleServer.
 *
 * @author pkv
 */
public class VehicleGuiView extends LinearLayout {
    protected AsyncVehicleServer mServer = null;
    protected final Object mServerLock = new Object();

    public VehicleGuiView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AsyncVehicleServer getVehicleServer() {
        synchronized (mServerLock) {
            return mServer;
        }
    }

    public synchronized void setVehicleServer(AsyncVehicleServer server) {
        synchronized (mServerLock) {
            mServer = server;
        }
    }
}
