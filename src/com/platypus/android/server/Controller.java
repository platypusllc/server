package com.platypus.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Wrapper for interfacing with the Platypus Controller board.
 * <p/>
 * This singleton class provides simple JSON-based send and receive functionality to a
 * Platypus controller board.  The class is automatically kept up to date with
 * accessories through the controller launcher activity.
 */
public class Controller {
    private static final String TAG = VehicleService.class.getSimpleName();
    private static final Charset ASCII = Charset.forName("US-ASCII");

    /**
     * Maximum packet size that can be received.
     */
    private static final int MAX_PACKET_SIZE = 1024;
    private static final Controller mInstance = new Controller();
    // References to USB accessory device.
    private Context mUsbContext = null;
    private UsbAccessory mUsbAccessory = null;
    private ParcelFileDescriptor mUsbDescriptor = null;
    private FileInputStream mUsbInputStream = null;
    private FileOutputStream mUsbOutputStream = null;
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

    private Controller() {
    }

    public static Controller getInstance() {
        return mInstance;
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
        ParcelFileDescriptor usbDescriptor = usbManager.openAccessory(usbAccessory);
        if (usbDescriptor == null) {
            Log.e(TAG, "Failed to open accessory: " + usbAccessory.getDescription());
            return;
        }

        // Clear references to old connection if it exists.
        close();

        // Create an intent filter to listen for device disconnections
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        context.registerReceiver(mUsbStatusReceiver, filter);

        // Store references to new usb device reference.
        mUsbContext = context;
        mUsbAccessory = usbAccessory;
        mUsbDescriptor = usbDescriptor;
        mUsbInputStream = new FileInputStream(usbDescriptor.getFileDescriptor());
        mUsbOutputStream = new FileOutputStream(usbDescriptor.getFileDescriptor());
    }

    /**
     * Close existing device references.
     */
    public synchronized void close() {
        if (mUsbContext != null) {
            mUsbContext.unregisterReceiver(mUsbStatusReceiver);
        }
        mUsbContext = null;

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
        // Allocate a buffer for the data packet.
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        int len;

        // Try to read a line from the device.
        // If the stream is not open, just wait longer.
        synchronized (this) {
            if (mUsbInputStream == null)
                throw new IOException("Not connected to hardware.");
            len = mUsbInputStream.read(buffer);
        }

        // Terminate and convert the buffers to an ASCII string.
        buffer[len] = '\0';
        String line = new String(buffer, 0, len, ASCII);

        // Turn the line into a JSON object and return it.
        // If the line is malformed, wait for the next line.
        try {
            return new JSONObject(line);
        } catch (JSONException e) {
            throw new IOException("Failed to parse response '" + line + "'.", e);
        }
    }
}
