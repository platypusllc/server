package com.platypus.controller;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

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
 * This class echoes a string called from JavaScript.
 */
public class Controller extends CordovaPlugin {
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final String USB_ACCESSORY_MANUFACTURER = "Platypus";
    private static final String USB_ACCESSORY_MODEL = "Controller";
    
    // Reference to USB accessory
    private final Object mUsbLock = new Object();
    private UsbManager mUsbManager;
    private UsbAccessory mUsbAccessory;
    private ParcelFileDescriptor mUsbDescriptor;

    // Callback lists for connection and receive events.
    private NavigableMap<Integer, CallbackContext> connectionCallbacks = new NavigableMap<>();
    private NavigableMap<Integer, CallbackContext> receiveCallbacks = new NavigableMap<>();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        // Retrieve the USB manager for Android accessory devices.
        mUsbManager = (UsbManager)cordova.getContext().getSystemService(Context.USB_SERVICE);

        // Create a filter that listens for permission to be granted on accessory devices.
        IntentFilter permission_filter = new IntentFilter(ACTION_USB_PERMISSION); 
        registerReceiver(mUsbReceiver, permission_filter);

        // Create a filter that listens for accessories to be attached.
        IntentFilter attached_filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        registerReceiver(mUsbReceiver, attached_filter);

        // Attempt to connect to any already available accessories.
        UsbAccessory[] accessoryList = manager.getAcccessoryList();
        for (UsbAccessory accessory : accessoryList) {

            // Check if this accessory is a Platypus Controller.
            if (!accessory.getManufacturer().equals(USB_ACCESSORY_MANUFACTURER))
                continue;
            if (!accessory.getModel().equals(USB_ACCESSORY_MODEL))
                continue;

            // Request permission to connect to a matching accessory.
            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            mUsbManager.requestPermission(accessory, mPermissionIntent);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("isConnected")) {
            this.isConnected(callbackContext);
            return true;
        } else if {
            JSONObject payload = args.getJSONObject(0);
            this.send(message, callbackContext);
            return true;
        }
        return false;
    }

    private void addEventListener(String eventName, CallbackContext callbackContext) {
        int index;

        // Add callback as next entry in the appropriate connection callback structure.
        if (eventName.equals("connection")) {
            Map.Entry<Integer, CallbackContext> lastEntry = connectionCallbacks.lastEntry();
            index = lastEntry == null ? 0 : lastEntry.getKey() + 1;
            connectionCallbacks.add(index, callbackContext);
        } else if (eventName.equals("receive")) {
            Map.Entry<Integer, CallbackContext> lastEntry = receiveCallbacks.lastEntry();
            index = lastEntry == null ? 0 : lastEntry.getKey() + 1;
            receiveCallbacks.add(index, callbackContext);
        } else {
            // If the type is unknown, return error and stop processing here.
            callbackContext.error("Unsupported event type '" + eventName + "' specified.");
            return;
        }

        // Don't return any result now, since status results will be sent when events come in from broadcast receiver
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, index);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }

    private void removeEventListener(String eventName, int index, CallbackContext callbackContext) {
        CallbackContext listenerContext;

        // Attempt to retrieve a corresponding callback from the appropriate callback structure.
        if (eventName.equals("connection")) {
            listenerContext = connectionCallbacks.remove(index);
        } else if (eventName.equals("receive")) {
            listenerContext = receiveCallbacks.remove(index);
        } else {
            // If the type is unknown, return error and stop processing here.
            callbackContext.error("Unsupported event type '" + eventName + "' specified.");
            return;
        }

        // If the callback exists, mark it as no longer persistent and call it
        // one last time, then report success to the original caller.
        // TODO: do we need to call it one last time?
        if (listenerContext == null) {
            callbackContext.error("Listener [" + index + "] did not exist for event '" eventName + "'.");
        } else {
            listenerContext.setKeepCallback(false);
            listenerContext.success();
            callbackContext.success();
        }
    }

    private void isConnected(CallbackContext callbackContext) {
        callbackContext(false);
    }

    private void getVersion(CallbackContext callbackContext) {
        callbackContext([0, 0, 1]);
    }

    private void send(JSONObject payload, CallbackContext callbackContext) {
        // TODO: implement this correctly.
        if (message != null && message.toString().length() > 0) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    /**
     * Handle JSON data received from USB accessory.
     */
    private void onReceive(JSONObject object) {
        // TODO: send this to the receive callbacks.
    }

    /**
     * On connection of a new accessory, open the descriptor.
     */
    private void onUsbConnection(UsbAccessory usbAccessory) {
        mUsbAccessory = usbAccessory;
        mUsbDescriptor = mUsbManager.openAccessory(mUsbAccessory);

        // If the accessory fails to connect, terminate service.
        if (mUsbDescriptor == null) {
            Log.e(TAG, "Failed to open accessory.");
            return;
        }

        // Create readers and writers for output over USB
        mUsbWriter = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(mUsbDescriptor.getFileDescriptor())));
        new Thread(new UsbReceiveHandler(mUsbDescriptor));
    }

    /**
     * Class that waits for input from the USB controller and dispatches it to
     * the onReceive handler.
     */
    class UsbReceiveHandler implements Runnable {
        InputStream mUsbReader;

        public UsbReceiveHandler(UsbAccessory mUsbDescriptor) {
            mUsbReader = new BufferedReader(new InputStreamReader(
                FileInputStream(mUsbDescriptor.getFileDescriptor()), "US-ASCII"));
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
                synchronized(mUsbLock) {
                    if (mUsbAccessory.equals(accessory)) {
                        try {
                            mUsbDescriptor.close();
                            Log.i(TAG, "Closed USB accessory.");
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to close USB accessory cleanly.", e);
                        }

                        mUsbDescriptor = null;
                        mUsbAccessory = null;
                    }
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                // If we received permission to connect to a device, try opening it.
                synchronized(mUsbLock) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        onUsbConnection(accessory);
                    }
                    else {
                        Log.w(TAG, "Permission denied for USB accessory " + accessory);
                    }
                }
            }
        }
    };
}
