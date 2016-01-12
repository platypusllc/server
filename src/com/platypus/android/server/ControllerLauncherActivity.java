package com.platypus.android.server;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * Launcher that sets reference to USB peripheral when it connects.
 * <p/>
 * This is required because Android does not allow USB connection events to be
 * received by services directly. Instead, this activity is launched, which does
 * nothing but use the USB connection event to set the accessory reference.
 *
 * @author pkv
 * @see <a href="https://github.com/follower/android-background-service-usb-accessory">Android Background Service - USB accessory</a>
 */
public class ControllerLauncherActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.platypus.android.server.USB_PERMISSION";
    private String TAG = ControllerLauncherActivity.class.getSimpleName();
    /**
     * Wait for the return from a USB permission request.
     *
     * If the request is granted, it updates the accessory reference used by the Platypus Server,
     * if not, it simply ends this launcher activity without updating anything.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // First, check that this permission response is actually for us.
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    // Get the accessory to which we are responding.
                    UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    // Permission was granted, if the device exists: open it.
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (accessory != null) {
                            Controller.getInstance().setConnection(context, accessory);
                            Log.d(TAG, "Set new accessory to: " + accessory.getDescription());
                        } else {
                            // This is weird, we got permission, but to which device?
                            Log.w(TAG, "No device returned.");
                        }
                    }
                    // Permission was not granted, don't open anything.
                    else {
                        Log.d(TAG, "Accessory permission denied.");
                    }

                    // End this activity.
                    finish();
                }
            }
        }
    };

    /**
     * Called when the activity is first created.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data
     *                           it most recently supplied in onSaveInstanceState(Bundle).
     *                           <b>Note: Otherwise it is null.</b>
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Defer to superclass.
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Starting controller launcher.");

        // Register a listener for USB permission events.
        // (This is cleaned up when the launcher is destroyed, but we might need
        // it if we have to search for an accessory.)
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        // Request permission for ANY connected devices.
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbAccessory[] usbAccessoryList = usbManager.getAccessoryList();

        if (usbAccessoryList != null && usbAccessoryList.length > 0) {
            // TODO: only detect Platypus Hardware!
            // At the moment, request permission to use the first accessory.
            // (Only one is supported at a time in Android.)
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(usbAccessoryList[0], permissionIntent);
        } else {
            Log.d(TAG, "Exiting controller launcher: No devices found.");
            Toast.makeText(this, "No Platypus devices found.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        // Unregister the receiver for USB permission responses.
        unregisterReceiver(mUsbReceiver);

        // Defer to superclass.
        Log.d(TAG, "Exiting controller launcher.");
        super.onDestroy();
    }
}
