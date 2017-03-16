package com.platypus.android.server;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Launcher that propagates a connection event to USB peripheral when it is detected.
 * <p/>
 * This is required because Android does not allow USB connection events to be
 * received by services directly. Instead, this activity is launched, which does
 * nothing but rebroadcast the USB connection event to other local receivers.
 *
 * @author pkv
 * @see <a href="https://github.com/follower/android-background-service-usb-accessory">Android Background Service - USB accessory</a>
 */
public class ControllerDetectionActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();

        // Forward this intent to other receivers in this application.
        Intent intent = getIntent();
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        finish();
    }
}
