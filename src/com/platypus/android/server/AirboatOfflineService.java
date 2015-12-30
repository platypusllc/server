package com.platypus.android.server;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import com.platypus.crw.VehicleServer;
import com.platypus.crw.data.UtmPose;

/**
 * Created by Nathan Huang on 7/10/24.
 */

public class AirboatOfflineService extends Service {

    private static final String LOG_TAG = "AirboatOfflineService";
    private static final int SERVICE_ID = 12313;

    private final IBinder _binder = new LocalBinder();

    // Contains a reference to the airboat service, or null if service is not running
    private AirboatService _airboatService = null;

    // Indicates if we have a valid reference to the airboat service.
    private boolean _isBound = false;

    // Thread handler that schedules new connection tests
    private Handler _handler;


    // Public field that indicates if service is started
    public static volatile boolean isRunning = false;
    private UtmPose[] waypoints;



    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        AirboatOfflineService getService() {
            return AirboatOfflineService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(LOG_TAG, "onCreate");

        // Disable strict-mode (TODO: remove this and use handlers)
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        _handler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // Ignore startup requests that don't include an intent
        if (intent == null) {
            Log.e(LOG_TAG, "Started with null intent.");
            return Service.START_STICKY;
        }

        Log.i(LOG_TAG, "onStart");
        doBindService();
        isRunning = true;

        waypoints =((ApplicationGlobe) getApplicationContext()).getWaypoints();



        // This is now a foreground service
        {
            // Set up the icon and ticker text
            int icon = R.mipmap.ic_launcher;
            CharSequence tickerText = "Running normally.";
            long when = System.currentTimeMillis();

            // Set up the actual title and text
            Context context = getApplicationContext();
            CharSequence contentTitle = "Offline Server";
            CharSequence contentText = tickerText;
            Intent notificationIntent = new Intent(this, AirboatActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            // Add a notification to the menu
            Notification notification = new Notification(icon, tickerText, when);
            notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
            startForeground(SERVICE_ID, notification);
        }
        _handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final VehicleServer server = _airboatService.getServer();
                if (server == null) {
                    Toast.makeText(getApplicationContext(),
                            "No Service!!!",
                            Toast.LENGTH_SHORT).show();
                }
                else {

                    Toast.makeText(getApplicationContext(),
                            "Running Offline System!!!",
                            Toast.LENGTH_LONG).show();
                    server.setAutonomous(true);
                    //enable the orientation sensor
                    server.startWaypoints(waypoints, "POINT_AND_SHOOT");
                    Toast.makeText(getApplicationContext(),
                            "Waypoint running!!!",
                            Toast.LENGTH_SHORT).show();
                }

            }
        }, 2000);



        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    private static final String logTag2 = AirboatActivity.class.getName();


    @Override
    public void onDestroy() {
        super.onDestroy();
        if(_airboatService.getServer()!=null){
            _airboatService.getServer().stopWaypoints();
        }
        Log.i(LOG_TAG, "onDestroy");
        doUnbindService();
        isRunning = false;

        _handler = null;

        // Remove service from foreground
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return _binder;
    }


    /**
     * Listener that handles changes in connections to the airboat service 
     */
    private ServiceConnection _connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.
            _airboatService = ((AirboatService.AirboatBinder)service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            _airboatService = null;
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation.
        if (!_isBound) {
            bindService(new Intent(this, AirboatService.class), _connection, Context.BIND_AUTO_CREATE);
            _isBound = true;
        }
    }

    void doUnbindService() {
        // Detach our existing connection.
        if (_isBound) {
            unbindService(_connection);
            _isBound = false;
        }
    }

}
	