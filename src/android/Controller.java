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
    private NavigableMap<Integer, CallbackContext> connectionCallbacks = new NavigableMap<>();
    private NavigableMap<Integer, CallbackContext> receiveCallbacks = new NavigableMap<>();

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
}
