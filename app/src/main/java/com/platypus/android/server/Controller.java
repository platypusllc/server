package com.platypus.android.server;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
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
 * This class provides simple JSON-based send and receive functionality to a
 * Platypus controller board.  The class is automatically kept up to date with
 * accessories by listening to USB connection and disconnection Intents.
 */
public class Controller {
    private static final String ACTION_USB_PERMISSION = "com.platypus.android.server.USB_PERMISSION";
    private static final String TAG = VehicleService.class.getSimpleName();
    private static final Charset ASCII = Charset.forName("US-ASCII");
    /**
     * Maximum packet size that can be received from the board.
     */
    private static final int MAX_PACKET_SIZE = 1024;
    private final Context mContext;
    /**
     * Listen for connection events for accessory and request permission to connect to it.
     */
    final BroadcastReceiver mUsbAttachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Retrieve the device that was just connected.
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

            // Request permission to connect to this device.
            // TODO: only detect Platypus Hardware!
            UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(accessory, permissionIntent);
        }
    };
    private final Object mUsbLock = new Object();
    private UsbAccessory mUsbAccessory = null;
    private ParcelFileDescriptor mUsbDescriptor = null;
    private FileInputStream mUsbInputStream = null;
    private FileOutputStream mUsbOutputStream = null;
    /**
     * Listen for disconnection events for accessory and close connection if we were using it.
     */
    final BroadcastReceiver mUsbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Retrieve the device that was just disconnected.
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

            // Close this connection if this accessory matches the one we have open.
            synchronized (mUsbLock) {
                if (accessory.equals(mUsbAccessory))
                    disconnect();
            }
        }
    };
    /**
     * Listen for permission events for a connected device and open connection if we got access.
     */
    final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Ignore unrelated permission responses.
            String action = intent.getAction();
            if (!ACTION_USB_PERMISSION.equals(action))
                return;

            // Get the accessory to which we are responding.
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

            // Ignore the permission response if access was denied.
            if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                return;

            // Connect to the new USB accessory.
            synchronized (mUsbLock) {
                disconnect();
                mUsbAccessory = accessory;
                connect();
            }
        }
    };

    public Controller(Context context) {
        // Store the context of the calling activity or service.
        mContext = context;

        // Register listeners for various USB device events.
        mContext.registerReceiver(mUsbPermissionReceiver,
                new IntentFilter(ACTION_USB_PERMISSION));

        mContext.registerReceiver(mUsbDetachedReceiver,
                new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED));

        LocalBroadcastManager.getInstance(mContext).registerReceiver(mUsbAttachedReceiver,
                new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED));

        // Connect to any existing devices if they are already available.
        searchDevices();
    }

    /**
     * Destroys and cleans up this controller object.
     * After this is called, the controller object cannot be used again.
     */
    public void shutdown() {
        disconnect();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mUsbAttachedReceiver);
        mContext.unregisterReceiver(mUsbDetachedReceiver);
        mContext.unregisterReceiver(mUsbPermissionReceiver);
    }

    /**
     * Searches for existing accessory devices and requests permission to access them.
     */
    protected void searchDevices() {
        // Request permission for ANY connected device.
        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        UsbAccessory[] usbAccessoryList = usbManager.getAccessoryList();

        if (usbAccessoryList != null && usbAccessoryList.length > 0) {
            // TODO: only detect Platypus Hardware!
            // At the moment, request permission to use the first accessory.
            // (Only one is supported at a time in Android.)
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(usbAccessoryList[0], permissionIntent);
        }
    }

    /**
     * Attempt to open the USB accessory.
     */
    public boolean connect() {
        synchronized (mUsbLock) {
            // If nothing is connected, don't connect.
            if (mUsbAccessory == null) {
                Log.e(TAG, "Failed to connect, no accessory available.");
                return false;
            }

            // Get a reference to the system USB management service.
            UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

            // Connect to control board.
            ParcelFileDescriptor usbDescriptor = usbManager.openAccessory(mUsbAccessory);
            if (usbDescriptor == null) {
                Log.e(TAG, "Failed to open accessory: " + mUsbAccessory.getDescription());
                return false;
            }

            // Make a connection to the USB descriptor.
            mUsbDescriptor = usbDescriptor;
            mUsbInputStream = new FileInputStream(usbDescriptor.getFileDescriptor());
            mUsbOutputStream = new FileOutputStream(usbDescriptor.getFileDescriptor());

            Log.i(TAG, "Opened " + mUsbAccessory);
            return true;
        }
    }

    /**
     * Close existing USB accessory.
     */
    protected void disconnect() {
        synchronized (mUsbLock) {
            // Clear input stream if it exists.
            if (mUsbInputStream != null) {
                try {
                    mUsbInputStream.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close accessory input stream.");
                }
            }
            mUsbInputStream = null;

            // Clear output stream if it exists.
            if (mUsbOutputStream != null) {
                try {
                    mUsbOutputStream.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close accessory output stream.");
                }
            }
            mUsbOutputStream = null;

            // Clear descriptor reference after input and output are cleared.
            if (mUsbDescriptor != null) {
                try {
                    mUsbDescriptor.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close accessory device descriptor.");
                }
            }
            mUsbDescriptor = null;

            // Clear old accessory references.
            Log.i(TAG, "Closed " + mUsbAccessory);
            mUsbAccessory = null;
        }
    }

    /**
     * Returns whether a controller board is currently connected via USB.
     *
     * @return true if a controller board is currently connected
     */
    public boolean isConnected() {
        synchronized (mUsbLock) {
            return (mUsbDescriptor != null);
        }
    }

    /**
     * Sends a JSON object to the controller board.
     *
     * @throws IOException if there is not a valid connection to a controller board.
     */
    public void send(JSONObject obj) throws IOException {
        // Construct message string as single byte array.
        byte[] message = (obj + "\r\n").getBytes(ASCII);

        synchronized (mUsbLock) {
            if (mUsbOutputStream == null)
                throw new ConnectionException("Not connected to hardware.");

            try {
                mUsbOutputStream.write(message);
                mUsbOutputStream.flush();
            } catch (IOException e) {
                disconnect();
                throw e;
            }
        }
    }

    /**
     * Receives a JSON object from the controller board.
     * This method blocks until a valid message is received.
     *
     * @throws IOException if there is not a valid connection to a controller board.
     */
    public JSONObject receive() throws IOException, ControllerException {
        // Allocate a buffer for the data packet.
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        int len;

        // Try to read a line from the device.
        // If the stream is not open, just wait longer.
        synchronized (mUsbLock) {
            if (mUsbInputStream == null)
                throw new ConnectionException("Not connected to hardware.");
            try {
                len = mUsbInputStream.read(buffer);
            } catch (IOException e) {
                disconnect();
                throw e;
            }
        }

        // Terminate and convert the buffers to an ASCII string.
        buffer[len] = '\0';
        String line = new String(buffer, 0, len, ASCII);

        // Turn the line into a JSON object and return it.
        // If the line is malformed, wait for the next line.
        try {
            JSONObject response = new JSONObject(line);
            if (response.has("error")) {
                throw new ControllerException(response.getString("error"),
                        response.optString("args"));
            }
            return response;
        } catch (JSONException e) {
            throw new IOException("Failed to parse response '" + line + "'.", e);
        }
    }

    /**
     * Exception used to denote an error returned by the controller itself.
     */
    public class ControllerException extends Exception {
        public final String mArgs;

        ControllerException(String message, String args) {
            super(args.isEmpty() ? message : message + ": " + args);
            mArgs = args;
        }
    }

    /**
     * Exception caused by an action that requires hardware being called when disconnected.
     */
    public class ConnectionException extends IOException {
        ConnectionException(String message) {
            super(message);
        }
    }
}
