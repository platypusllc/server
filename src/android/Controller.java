package com.platypus.controller;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Set;
import java.util.HashSet;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * API sketch:
 * getVersion:
 * isConnected:
 * addEventListener("connection", [...]):
 * removeEventListener("connection", [...]):
 * send:
 * addEventListener("receive", [...]):
 * removeEventListener("receive", [...]):
 */

/**
 * This class connects to any available Platypus Controllers and provides a
 * JSON stream interface to the controller.
 */
public class Controller extends CordovaPlugin {
    private static final String TAG = Controller.class.getName();
    private static final String ACTION_USB_PERMISSION = "com.platpus.controller.USB_PERMISSION";
    private static final String USB_ACCESSORY_MANUFACTURER = "Platypus";
    private static final String USB_ACCESSORY_MODEL = "Controller";
    
    // Reference to USB accessory
    private final Object mUsbLock = new Object();
    private UsbManager mUsbManager;
    private PrintWriter mUsbWriter;
    private UsbAccessory mUsbAccessory;
    private ParcelFileDescriptor mUsbDescriptor;

    // Callback lists for connection and receive events.
    private final Set<CallbackContext> connectionCallbacks = new HashSet();
    private final Set<CallbackContext> receiveCallbacks = new HashSet();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        // Retrieve the USB manager for Android accessory devices.
        mUsbManager = (UsbManager)cordova.getActivity().getSystemService(Context.USB_SERVICE);

        // Create a filter that listens for permission to be granted on accessory devices.
        IntentFilter permission_filter = new IntentFilter(ACTION_USB_PERMISSION); 
        cordova.getActivity().registerReceiver(mUsbReceiver, permission_filter);

        // Create a filter that listens for accessories to be attached.
        IntentFilter attached_filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        cordova.getActivity().registerReceiver(mUsbReceiver, attached_filter);

        // Attempt to connect to any already available accessories.
        UsbAccessory[] accessoryList = mUsbManager.getAccessoryList();
        for (UsbAccessory accessory : accessoryList) {

            // Check if this accessory is a Platypus Controller.
            if (!accessory.getManufacturer().equals(USB_ACCESSORY_MANUFACTURER))
                continue;
            if (!accessory.getModel().equals(USB_ACCESSORY_MODEL))
                continue;

            // Request permission to connect to a matching accessory.
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    cordova.getActivity(), 0, new Intent(ACTION_USB_PERMISSION), 0);
            mUsbManager.requestPermission(accessory, permissionIntent);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("getVersion".equals(action)) {
            this.getVersion(callbackContext);
            return true;
        } else if ("isConnected".equals(action)) {
            this.isConnected(callbackContext);
            return true;
        } else if ("addEventListener".equals(action)) {
            String eventName = args.getString(0);
            addEventListener(eventName, callbackContext);
            return true;
        } else if ("removeEventListener".equals(action)) {
            String eventName = args.getString(0);
            removeEventListener(eventName, callbackContext);
            return true;
        } else if ("send".equals(action)) {
            JSONObject message = args.getJSONObject(0);
            this.send(message, callbackContext);
            return true;
        } else {
            return false;
        }
    }

    private void addEventListener(String eventName, CallbackContext callbackContext) {

        // Add callback as next entry in the appropriate connection callback structure.
        if ("connection".equals(eventName)) {
            synchronized(connectionCallbacks) {
                connectionCallbacks.add(callbackContext);
            }
        } else if ("receive".equals(eventName)) {
            synchronized(receiveCallbacks) {
                receiveCallbacks.add(callbackContext);
            }
        } else {
            // If the type is unknown, return error and stop processing here.
            callbackContext.error("Unsupported event type '" + eventName + "' specified.");
            return;
        }

        // Status results will be sent when events come in from broadcast receiver.
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }

    private void removeEventListener(String eventName, CallbackContext callbackContext) {
        CallbackContext listenerContext;

        // TODO: How do we unregister callbacks?
        
        // Attempt to retrieve a corresponding callback from the appropriate callback structure.
        if ("connection".equals(eventName)) {
            synchronized(connectionCallbacks) {
                connectionCallbacks.remove(callbackContext);
                listenerContext = callbackContext;
            }
        } else if ("receive".equals(eventName)) {
            synchronized(receiveCallbacks) {
                receiveCallbacks.remove(callbackContext);
                listenerContext = callbackContext;
            }
        } else {
            // If the type is unknown, return error and stop processing here.
            callbackContext.error("Unsupported event type '" + eventName + "' specified.");
            return;
        }

        // If the callback exists, mark it as no longer persistent and call it
        // one last time, then report success to the original caller.
        // TODO: do we need to call it one last time?
        if (listenerContext == null) {
            callbackContext.error("Listener did not exist for event '" + eventName + "'.");
        } else {
            // Make a final call to the callback function.
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(false);
            listenerContext.sendPluginResult(pluginResult);
            
            // Return success to the removeEventListener callee.
            callbackContext.success();
        }
    }

    private void isConnected(CallbackContext callbackContext) {
        synchronized(mUsbLock) {
            final boolean isConnected = (mUsbAccessory != null);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, isConnected));
        }
    }

    private void getVersion(CallbackContext callbackContext) {
        // TODO: get this version from the device instead of faking it.
        JSONArray array = new JSONArray();
        array.put(0);
        array.put(0);
        array.put(1);
        callbackContext.success(array);
    }

    private void send(JSONObject message, CallbackContext callbackContext) {
        if (message == null) {
            callbackContext.error("Expected one non-empty argument.");
            return;
        }
        
        synchronized(mUsbLock) {
            mUsbWriter.println(message.toString());
            callbackContext.success(message);
        }
    }

    /**
     * Handle JSON data received from USB accessory.
     */
    private void onReceive(JSONObject object) {
        synchronized(receiveCallbacks) {
            for (CallbackContext listenerContext : receiveCallbacks) {
                // Send message to each receive callback function.
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, object);
                pluginResult.setKeepCallback(true);
                listenerContext.sendPluginResult(pluginResult);
            }
        }
    }

    /**
     * On connection of a new accessory, open the descriptor.
     */
    private void onUsbConnection(UsbAccessory usbAccessory) {
        synchronized(mUsbLock) {
            mUsbAccessory = usbAccessory;
            mUsbDescriptor = mUsbManager.openAccessory(mUsbAccessory);

            // If the accessory fails to connect, terminate service.
            if (mUsbDescriptor == null) {
                // TODO: make a helper logging function.
                webView.loadUrl("javascript:console.error('Failed to open accessory.');");
                Log.e(TAG, "Failed to open accessory.");
                return;
            }

            // Create readers and writers for output over USB.
            mUsbWriter = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(mUsbDescriptor.getFileDescriptor())));
            new Thread(new UsbReceiveHandler(mUsbDescriptor)).start();
        }

        // Report that a device is now connected.
        synchronized(connectionCallbacks) {
            for (CallbackContext listenerContext : connectionCallbacks) {
                // Send message to each receive callback function.
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, true);
                pluginResult.setKeepCallback(true);
                listenerContext.sendPluginResult(pluginResult);
            }
        }
    }

    /**
     * On disconnection of a USB accessory, close the descriptor.
     */
    private void onUsbDisconnection(UsbAccessory accessory) {
        synchronized(mUsbLock) {
            // If this is not our current accessory, ignore.
            if (!mUsbAccessory.equals(accessory)) {
                return;
            }
            
            try {
                mUsbDescriptor.close();
                // TODO: make a helper logging function.
                webView.loadUrl("javascript:console.log('Closed USB accessory.');");
                Log.i(TAG, "Closed USB accessory.");
            } catch (IOException e) {
                // TODO: make a helper logging function.
                webView.loadUrl("javascript:console.warn('Failed to close USB accessory cleanly.');");
                Log.w(TAG, "Failed to close USB accessory cleanly.", e);
            }

            mUsbDescriptor = null;
            mUsbAccessory = null;
        }
        
        // Report that no device is connected.
        synchronized(connectionCallbacks) {
            for (CallbackContext listenerContext : connectionCallbacks) {
                // Send message to each receive callback function.
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, false);
                pluginResult.setKeepCallback(true);
                listenerContext.sendPluginResult(pluginResult);
            }
        }
    }
    
    /**
     * Class that waits for input from the USB controller and dispatches it to
     * the onReceive handler.
     */
    protected class UsbReceiveHandler implements Runnable {
        BufferedReader mUsbReader;

        public UsbReceiveHandler(ParcelFileDescriptor usbDescriptor) {
            // TODO: be specific about the charset?
            mUsbReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(usbDescriptor.getFileDescriptor())));
        }

        @Override
        public void run() {
            // Start a loop to receive data from accessory.
            try {
                while (true) {
                    // Try to interpret each line as a JSON object and send it
                    // to the onReceive handler.
                    String line = mUsbReader.readLine();
                    try {
                        JSONObject data = new JSONObject(line);
                        onReceive(data);
                    } catch (JSONException e) {
                        // TODO: make a helper logging function.
                        webView.loadUrl("javascript:console.warn('Invalid controller input JSON.');");
                        Log.w(TAG, "Invalid controller input JSON: '" + line + "'.", e);
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "USB connection interrupted.", e);
            }

            try {
                mUsbReader.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close USB input stream cleanly.", e);
            }
        }
    }

    /**
     * Listen for disconnection events for accessory and close connection.
     */
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Get the action and the USB device that was affected.
            String action = intent.getAction(); 
            UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(
                UsbManager.EXTRA_ACCESSORY);

            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                // Handle disconnection on a currently connected accessory.
                onUsbDisconnection(accessory);
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                // If we received permission to connect to a device, try opening it.
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    onUsbConnection(accessory);
                } else {
                    // TODO: make a helper logging function.
                    webView.loadUrl("javascript:console.warn('Permission denied for USB accessory.');");
                    Log.w(TAG, "Permission denied for USB accessory " + accessory);
                }
            }
        }
    };
}
