import com.platypus.crw.udp.UdpVehicleService;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VehicleService {
    protected AtomicBoolean isRunning = new AtomicBoolean(false);

    private VehicleLogger mLogger;

    private Controller mController;
    private VehicleServerImpl _vehicleServerImpl;
    private UdpVehicleService _udpService;

    private final Object mUdpLock = new Object();

    public static Logger logger = Logger.getLogger(VehicleService.class.getName());

    public void send(JSONObject jsonObject) throws IOException
    {
        //Log.d(TAG, "VehicleService.send() called...");
        logger.log(Level.parse("DEBUG"),"VehicleService.send() called...");
        if (mController != null)
        {
            try
            {
                if (mController.isConnected())
                {
                    //Log.d(TAG, "    sending this JSON: ");
                    //Log.d(TAG, jsonObject.toString());
                    logger.log(Level.parse("DEBUG"),"\t sending this JSON: ");
                    logger.log(Level.parse("DEBUG"),jsonObject.toString());
                    mController.send(jsonObject);
                }
                else
                {
                    //Log.w(TAG, "    mController is NOT connected");
                    logger.log(Level.WARNING,"\t mController is not connected");
                }
            }
            catch (IOException e)
            {
                //Log.w(TAG, "Failed to send command.", e);
                logger.log(Level.WARNING,"Failed to send command.",e);
            }
            catch (Controller.ControllerException e)
            {
                logger.log(Level.WARNING,"Failed to send command.",e);
            }
        }
        else
        {
            //Log.w(TAG, "    mController is null");
            logger.log(Level.WARNING,"mController is null");
        }
    }
    private void startOrUpdateUdpServer() {
        // Start up UDP vehicle service in the background
        new Thread(new Runnable() {
            @Override
            public void run() {
//                final SharedPreferences preferences =
//                        PreferenceManager.getDefaultSharedPreferences(VehicleService.this);

                synchronized (mUdpLock) {
                    if (_udpService != null)
                        _udpService.shutdown();

                    try {
                        final int port = Integer.parseInt("11411");
                        _udpService = new UdpVehicleService(port, _vehicleServerImpl);
                        //Log.i(TAG, "UdpVehicleService launched on port " + port + ".");
                        logger.log(Level.INFO,"UdpVehicleService launched on port " + port + ".");
                    } catch (Exception e) {
                        //Log.e(TAG, "UdpVehicleService failed to launch", e);
                        logger.log(Level.parse("ERROR"),"UdpVehicleService failed to launch",e);
                        //stopSelf();
                    }
                }
            }
        }).start();
    }
    public int onStartCommand(int flags, int startId) {

        // Ensure that we do not reinitialize if not necessary.
        if (isRunning.get()) {
            logger.log(Level.WARNING, "Attempted to start while running");
            //Log.w(TAG, "Attempted to start while running.");
            return 1;
        }

        // start tracing to "/sdcard/trace_crw.trace"
        // Debug.startMethodTracing("trace_crw");

        // Create a new vehicle log file for this service.
        if (mLogger != null)
            mLogger.close();
        mLogger = new VehicleLogger();

        _vehicleServerImpl = new VehicleServerImpl( mLogger, mController);

        startOrUpdateUdpServer();

        int[] axes = {0, 5};
        for (int axis : axes) {
            double[] gains = _vehicleServerImpl.getGains(axis);
            _vehicleServerImpl.setGains(axis, gains);
        }

        isRunning.set(true);

        logger.log(Level.INFO,"VehicleService started.");
        return 1;
    }




    }
