package edu.cmu.ri.airboat.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.platypus.crw.data.Utm;
import com.platypus.crw.data.UtmPose;

import org.apache.http.util.EncodingUtils;
import org.jscience.geography.coordinates.LatLong;
import org.jscience.geography.coordinates.UTM;
import org.jscience.geography.coordinates.crs.ReferenceEllipsoid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import robotutils.Pose3D;

/**
 * Created by Nathan Huang on 7/10/14.
 */

public class AirboatOfflineActivity extends Activity {

    private final IBinder _binder = new LocalBinder();
    private AirboatListener myListener = new AirboatListener(){

        @Override

        public void refreshActivity(String text){

            final EditText Path = (EditText)findViewById(R.id.FilePath);
            Path.setText(text);
        }

        public void runOfflineSystem(){
            //make Alarm working for warning
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
            //vibrate to make a warning
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(3000);
            Camera camera = null;
            Camera.Parameters parameters = null;
            camera = Camera.open();
            //flash the flash light for 10 times
            for(int i=0; i<10;i++) {
                parameters = camera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//open
                camera.setParameters(parameters);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);//close
                camera.setParameters(parameters);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            camera.release();
            r.stop();
            ((ApplicationGlobe) getApplicationContext()).setWaypoints(waypoints);
            Intent intent = new Intent(AirboatOfflineActivity.this, AirboatOfflineService.class);
            Log.i(logTag, "Starting offline service.");
            startService(intent);

        }

    };


    private static final String logTag = AirboatOfflineActivity.class.getName();
    final AtomicBoolean gotGPS = new AtomicBoolean(false);
    private UtmPose _homePosition = new UtmPose();
    String[] point;
    private UtmPose waypoints[];
    private AirboatService _airboatService = null;
    // Indicates if we have a valid reference to the airboat service.
    private boolean _isBound = false;
    private  SensorManager sm;
    private int orientation_counter=0;
    boolean isFirst=true;
    float sensorminimum=0;
    float sensormaxium=20;

    final SensorEventListener myAccelerometerListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if(sensorEvent.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                float X_lateral = sensorEvent.values[0];
//                float Y_longitudinal = sensorEvent.values[1];
//                float Z_vertical = sensorEvent.values[2];
                if(isFirst){
                    sensormaxium=X_lateral>=180?X_lateral-180:X_lateral+180;
                    sensorminimum=sensormaxium>=20?sensormaxium-20:sensormaxium;
                    sensormaxium=sensormaxium==sensorminimum?sensormaxium+20:sensormaxium;
                    isFirst=false;
                }
                final TextView debuttext=(TextView)findViewById(R.id.debugtext);
                if (X_lateral<sensormaxium&&X_lateral>sensorminimum) {
                    //When detect a spin, do ringtone and vibrate
                    orientation_counter++;
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    getSystemService(VIBRATOR_SERVICE);
                    vibrator.vibrate(300);
                    Handler handler=new Handler();
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    final Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    r.play();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            r.stop();
                        }
                    },2000);

                    sensormaxium=X_lateral>=180?X_lateral-180:X_lateral+180;
                    sensorminimum=sensormaxium>=20?sensormaxium-20:sensormaxium;
                    sensormaxium=sensormaxium==sensorminimum?sensormaxium+20:sensormaxium;
                }
                debuttext.setText(orientation_counter+"");
                if (orientation_counter>=3) {
                    //Spin for 3 times and launch offline system
                    myListener.runOfflineSystem();
                    sm.unregisterListener(myAccelerometerListener);
                    orientation_counter = 0;
                }


            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        AirboatOfflineActivity getService() {
            return AirboatOfflineActivity.this;
        }
    }


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.offline);
        final EditText Path = (EditText)findViewById(R.id.FilePath);
        final android.os.Handler handler = new android.os.Handler();

        sm = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        Path.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String[] fileType = {"txt"};
                AirboatFileExplorer dlg = new AirboatFileExplorer(AirboatOfflineActivity.this, fileType, myListener);
                dlg.setTitle("Choose file(.txt)");
                dlg.show();
                return false;
            }
        });

        Path.addTextChangedListener(new TextWatcher() {

            final AtomicBoolean _isUpdating = new AtomicBoolean(false);
            final AtomicBoolean _isUpdated = new AtomicBoolean(false);

            final class TextUpdate extends AsyncTask<Void, Void, Integer> {
                private String path;

                @Override
                protected void onPreExecute() {
                    path = Path.getText().toString();
                }

                @Override
                protected Integer doInBackground(Void... urls) {
                    int textBkgnd = 0xAAAA0000;

                    _isUpdated.set(true);
                    _isUpdating.set(true);

                    // Try to open the host name in the text box,
                    // if it succeeds, change color accordingly
                    File file = new File(path);
                    if (path.trim().length() != 0 && file.exists())
                        textBkgnd = 0xAA00AA00;

                    return textBkgnd;
                }

                @Override
                protected void onPostExecute(Integer result) {
                    Path.setBackgroundColor(result);

                    // Immediately reschedule if out of date, otherwise delay
                    if (!_isUpdated.get()) {
                        new TextUpdate().execute((Void[]) null);
                    } else {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                new TextUpdate().execute((Void[]) null);
                            }
                        }, 2000);
                    }

                    // In any case, we are now done updating
                    _isUpdating.set(false);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(final Editable s) {

                _isUpdated.set(false);

                // If an update isn't already running, start one up
                if (!_isUpdating.get()) {
                    new TextUpdate().execute((Void[]) null);
                }
            }
        });
        // Set text boxes to previous values
        Path.setText(Environment.getExternalStorageDirectory().getPath() + "bluetooth/Path1.txt");
        


        final ToggleButton offlineServer = (ToggleButton)findViewById(R.id.OfflineServer);
        offlineServer.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AirboatOfflineActivity.this, AirboatOfflineService.class);
                offlineServer.setEnabled(false);
                if (!offlineServer.isChecked()) {
                    Log.i(logTag, "Stopping failsafe service.");
                    stopService(intent);
                    return;
                }
                /////Check if the .path file is reachable. if not, abort
                String path=Path.getText().toString();
                File file=new File(path);
                if(!file.exists()) return;

                /////Set the current location as the Home location
                final LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Toast.makeText(getApplicationContext(),
                            "GPS must be turned on to set home location.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                gotGPS.set(false);

                final LocationListener ll = new LocationListener() {

                    public void onStatusChanged(String provider, int status, Bundle extras) {}
                    public void onProviderEnabled(String provider) {}
                    public void onProviderDisabled(String provider) {}

                    @Override
                    public void onLocationChanged(Location location) {
                    // Convert from lat/long to UTM coordinates
                        UTM utmLoc = UTM.latLongToUtm(
                                LatLong.valueOf(location.getLatitude(), location.getLongitude(), NonSI.DEGREE_ANGLE),
                                ReferenceEllipsoid.WGS84
                        );
                        _homePosition.pose = new Pose3D(
                                utmLoc.eastingValue(SI.METER),
                                utmLoc.northingValue(SI.METER),
                                location.getAltitude(),
                                0.0, 0.0, 0.0);
                        _homePosition.origin = new Utm(utmLoc.longitudeZone(), utmLoc.latitudeZone() > 'O');
                        AirboatFailsafeService.setHome(_homePosition);

                        // Now that we have the GPS location, stop listening
                        Toast.makeText(getApplicationContext(),
                                "Home location set to: " + utmLoc,
                                Toast.LENGTH_SHORT).show();
                        Log.i(logTag, "Set home to " + utmLoc);
                        locationManager.removeUpdates(this);
                        gotGPS.set(true);
                    }
                };

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ll);

                // Cancel GPS fix after a while
                /////Check if the GPS signal is good. if not, send a warning

                final Handler _handler = new Handler();
                _handler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        if (!gotGPS.get()) {
                            // Cancel GPS lookup after 5 seconds
                            locationManager.removeUpdates(ll);

                            // Report failure to get location
                            //make Alarm working for warning
                            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                            r.play();
                            //vibrate to make a warning
                            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                            getSystemService(VIBRATOR_SERVICE);
                            vibrator.vibrate(3000);
                            Camera camera;
                            Camera.Parameters parameters;
                            camera = Camera.open();
                            //flash the flash light for 10 times
                            for(int i=0; i<10;i++) {
                                parameters = camera.getParameters();
                                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//open
                                camera.setParameters(parameters);
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);//close
                                camera.setParameters(parameters);
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            camera.release();
                            r.stop();
                            Toast.makeText(getApplicationContext(),"Warning: no GPS signal",Toast.LENGTH_SHORT).show();
                        }
                    }
                }, 5000);

                ////Get the path from file(.path)
                FileInputStream fis;

                try {
                    fis = new FileInputStream(file);
                    int length;
                    length = fis.available();
                    byte [] buffer = new byte[length];
                    fis.read(buffer);
                    String res = EncodingUtils.getString(buffer, "UTF-8");
                    point=res.split(";");
                    fis.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                debuttext.setText(point.length+"");

                ///Convert the point into UTM point
                int i,j=0;
                waypoints=new UtmPose[point.length/2+1];
                for(i=0;i<point.length/2;i++){
                    UTM utmLoc = UTM.latLongToUtm(
                            LatLong.valueOf(Double.parseDouble(point[j]), Double.parseDouble(point[j+1]), NonSI.DEGREE_ANGLE),
                            ReferenceEllipsoid.WGS84
                    );
                    j+=2;
                    waypoints[i] = new UtmPose(new Pose3D(utmLoc.eastingValue(SI.METER),utmLoc.northingValue(SI.METER), 172.35, 0, 0, 0), new Utm(17, true));

                    waypoints[i].origin = new Utm(utmLoc.longitudeZone(), utmLoc.latitudeZone() > 'O');

                    Toast.makeText(getApplicationContext(),
                            "Waypoints[" +i+"]:"+waypoints[i].toString(),
                            Toast.LENGTH_SHORT).show();
                }

                final int finalI = i;
                _handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ///Add homePosition to the end of the point list
                        waypoints[finalI]=_homePosition;
                        Toast.makeText(getApplicationContext(),
                                "Waypoints[" + finalI +"]: "+waypoints[finalI].toString(),
                                Toast.LENGTH_SHORT).show();
                        double distance = Math
                                .sqrt((waypoints[0].pose.getX() - _homePosition.pose.getX())
                                        * (waypoints[0].pose.getX() - _homePosition.pose.getX())
                                        + (waypoints[0].pose.getY() - _homePosition.pose.getY())
                                        * (waypoints[0].pose.getY() - _homePosition.pose.getY()));
                        distance=Math.round(distance*1000)/1000;

                        if (distance>500) dialog(distance);
                        else{
                            //enable the orientation sensor
                            sm.registerListener(myAccelerometerListener, sm.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_NORMAL);
                            orientation_counter=0;
                            isFirst=true;
                        }
                    }
                }, 8000);

            }

        });

        // Periodically update status of toggle button
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!AirboatService.isRunning) offlineServer.setChecked(false);
//                offlineServer.setChecked(AirboatService.isRunning);
                offlineServer.setEnabled(true);
                handler.postDelayed(this, 300);
            }
        }, 0);

        final Button debug = (Button)findViewById(R.id.debug);
        debug.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                //immediate start offline system
//                ((ApplicationGlobe) getApplicationContext()).warning(2000);
                Intent intent;
                /////Check if the .path file is reachable. if not, abort
                String path=Path.getText().toString();
                File file=new File(path);
                if(!file.exists()) return;

                ////Get the path from file(.path)
                FileInputStream fis;

                try {
                    fis = new FileInputStream(file);
                    int length;
                    length = fis.available();
                    byte [] buffer = new byte[length];
                    fis.read(buffer);
                    String res = EncodingUtils.getString(buffer, "UTF-8");
                    point=res.split(";");
                    fis.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                debuttext.setText(point.length+"");

                ///Convert the point into UTM point
                int i,j=0;
                waypoints=new UtmPose[point.length/2+1];
                for(i=0;i<point.length/2;i++){
                    UTM utmLoc = UTM.latLongToUtm(
                            LatLong.valueOf(Double.parseDouble(point[j]), Double.parseDouble(point[j+1]), NonSI.DEGREE_ANGLE),
                            ReferenceEllipsoid.WGS84
                    );
                    j+=2;
                    waypoints[i] = new UtmPose(new Pose3D(utmLoc.eastingValue(SI.METER),utmLoc.northingValue(SI.METER), 172.35, 0, 0, 0), new Utm(17, true));

                    waypoints[i].origin = new Utm(utmLoc.longitudeZone(), utmLoc.latitudeZone() > 'O');

                    Toast.makeText(getApplicationContext(),
                            "Waypoints[" +i+"]:"+waypoints[i].toString(),
                            Toast.LENGTH_SHORT).show();
                }
                ((ApplicationGlobe) getApplicationContext()).setWaypoints(waypoints);
                intent = new Intent(AirboatOfflineActivity.this, AirboatOfflineService.class);
                Log.i(logTag, "Starting offline service.");
                startService(intent);
                startActivity(new Intent(AirboatOfflineActivity.this, AirboatControlActivity.class));
                offlineServer.setEnabled(true);
            }
        });


    }

    public void onResume(){

        super.onResume();


    }
    public void onPause(){
//        sm.unregisterListener(myAccelerometerListener);
//        orientation_counter=0;
        super.onPause();
    }


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
    protected void dialog(double distance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("The distance between current location and the first point is "+distance+"m\nAre you sure to continue?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //enable the orientation sensor
                        sm.registerListener(myAccelerometerListener, sm.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_NORMAL);
                        orientation_counter=0;
                        isFirst=true;
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        ToggleButton offlineServer = (ToggleButton)findViewById(R.id.OfflineServer);
                        offlineServer.setChecked(false);
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

}

