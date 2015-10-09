package edu.cmu.ri.airboat.server;

import android.app.Application;
import android.hardware.Camera;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

import edu.cmu.ri.crw.data.UtmPose;

/**
 * Created by hnx on 6/23/14.
 */
public class ApplicationGlobe extends Application{
    private String failsafe_IPAddress;
    private UtmPose[] waypoints;

    public String getFailsafe_IPAddress() {
        return failsafe_IPAddress;
    }

    public void setFailsafe_IPAddress(String failsafe_IPAddress) {
        this.failsafe_IPAddress = failsafe_IPAddress;
    }

    public UtmPose[] getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(UtmPose[] waypoints) {
        this.waypoints = waypoints;
    }

    public void warning(int delay) {
        android.os.Handler handler = null;
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {

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
                for (int i = 0; i < 10; i++) {
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
            }
        }, delay);
    }
}
