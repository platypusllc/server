package com.platypus.android.server;

import com.google.firebase.database.IgnoreExtraProperties;
import com.platypus.android.server.util.ISO8601Date;

/**
 * Simple POJO data class to represent the starting or stopping of the server.
 */
@IgnoreExtraProperties
public class UsageEvent {
    public String timestamp;
    public Boolean running;

    public UsageEvent() {};

    public UsageEvent(boolean isRunning) {
        running = isRunning;
        timestamp = ISO8601Date.now();
    }
}
