package com.platypus.android.server;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Launcher that sets reference to USB peripheral when it connects.
 * <p/>
 * This is required because Android does not allow USB connection events to be
 * received by services directly. Instead, this activity is launched, which does
 * nothing but rebroadcast the USB connection event to other local receivers.
 *
 * @author pkv
 * @see <a href="https://github.com/follower/android-background-service-usb-accessory">Android Background Service - USB accessory</a>
 */
public class ControllerActivity extends Activity {
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(intent.getAction())) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        finish();
    }
}
