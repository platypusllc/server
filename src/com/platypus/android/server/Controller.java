package com.platypus.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Wrapper for interfacing with the Platypus Controller board.
 *
 * This singleton class provides simple JSON-based send and receive functionality to a
 * Platypus controller board.  The class is automatically kept up to date with
 * accessories through the controller launcher activity.
 */
public class Controller {
    private static final String TAG = VehicleService.class.getSimpleName();
    private static final Charset ASCII = Charset.forName("US-ASCII");

    // References to USB accessory device.
    private UsbAccessory mUsbAccessory = null;
    private ParcelFileDescriptor mUsbDescriptor = null;
    private FileInputStream mUsbInputStream = null;
    private FileOutputStream mUsbOutputStream = null;

    private static final Controller mInstance = new Controller();

    public static Controller getInstance() {
        return mInstance;
    }

    private Controller() {


        // Create an intent filter to listen for device disconnections
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        context.registerReceiver(mUsbStatusReceiver, filter);
    }

    /**
     * Sets the controller interface to use the specified USB accessory.
     *
     * @param usbAccessory the USB accessory that should be used to connect to the controller.
     */
    public synchronized void setConnection(Context context, UsbAccessory usbAccessory) {
        // Get a reference to the system USB management service.
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // Connect to control board.
        ParcelFileDescriptor usbDescriptor = mUsbManager.openAccessory(usbAccessory);
		if (usbDescriptor == null) {
			Log.e(TAG, "Failed to open accessory: " + usbAccessory.getDescription());
            return;
		}

        // Clear references to old connection if it exists.
        close();

		// Store references to new usb device reference.
        mUsbAccessory = usbAccessory;
        mUsbDescriptor = usbDescriptor;
        mUsbInputStream = new FileInputStream(usbDescriptor.getFileDescriptor());
        mUsbOutputStream = new FileOutputStream(usbDescriptor.getFileDescriptor());
    }

    /**
     * Close existing device references.
     */
    public synchronized void close() {
        if (mUsbInputStream != null) {
            try {
                mUsbInputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close accessory input stream.");
            }
        }
        mUsbInputStream = null;

        if (mUsbOutputStream != null) {
            try {
                mUsbOutputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close accessory output stream.");
            }
        }
        mUsbOutputStream = null;

        if (mUsbDescriptor != null) {
            try {
                mUsbDescriptor.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close accessory device descriptor.");
            }
        }
        mUsbDescriptor = null;
    }

    /**
     * Returns whether a controller board is currently connected via USB.
     *
     * @return true if a controller board is currently connected
     */
    public synchronized boolean isConnected() {
        return (mUsbDescriptor != null);
    }

    /**
     * Sends a JSON object to the controller board.
     *
     * @throws IOException if there is not a valid connection to a controller board.
     */
    public synchronized void send(JSONObject obj) throws IOException {
        if (mUsbOutputStream == null)
            throw new IOException("Not connected to hardware.");

        mUsbOutputStream.write(obj.toString().getBytes(ASCII));
        mUsbOutputStream.write('\r');
        mUsbOutputStream.write('\n');
        mUsbOutputStream.flush();
    }

    /**
     * Receives a JSON object from the controller board.
     * This method blocks until a valid message is received.
     *
     * @throws IOException if there is not a valid connection to a controller board.
     */
    public JSONObject receive() throws IOException {
        if (mUsbInputStream == null)
            throw new IOException("Not connected to hardware.");

        // Start a loop to receive data from accessory.
        while (true) {
            // Handle this response
            byte[] buffer = new byte[1024];
            int len = usbReader.read(buffer);
            buffer[len] = '\0';
            String line = new String(buffer, 0, len);

            try {
                // TODO: proper threading here
                if (_airboatImpl == null) {
                    return;
                } else {
                    _airboatImpl.onCommand(new JSONObject(line));
                }
            } catch (JSONException e) {
                Log.w(TAG, "Failed to parse response '" + line + "'.", e);
            }
        }


        try {
            usbReader.close();
        } catch (IOException e) {
        }
        throw new IOException("Not connected to hardware.");
    }

    /**
     * Listen for disconnection events for accessory and close connection.
     */
    final BroadcastReceiver mUsbStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Retrieve the device that was just disconnected.
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

            // Close this connection if this accessory matches the one we have open.
            if (mUsbAccessory.equals(accessory))
                close();
        }
    };
}
