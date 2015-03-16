package edu.cmu.ri.airboat.server;

import edu.cmu.ri.crw.AsyncVehicleServer;
import edu.cmu.ri.crw.FunctionObserver;
import edu.cmu.ri.crw.PoseListener;
import edu.cmu.ri.crw.SensorListener;
import edu.cmu.ri.crw.VehicleServer;
import edu.cmu.ri.crw.WaypointListener;
import edu.cmu.ri.crw.data.SensorData;
import edu.cmu.ri.crw.data.UtmPose;

import com.google.code.microlog4android.LoggerFactory;
import com.madara.KnowledgeBase;
import com.madara.transport.TransportSettings;
import com.madara.transport.TransportType;

/**
 * Vehicle service that exposes a vehicle via MADARA fields.
 */
public class MadaraVehicleService implements WaypointListener, PoseListener, SensorListener {
    private static final com.google.code.microlog4android.Logger logger = LoggerFactory
            .getLogger();

    protected AsyncVehicleServer _server;
    protected KnowledgeBase _knowledge;

    public MadaraVehicleService(VehicleServer server) {
        TransportSettings settings = configureTransport();
        _knowledge = new KnowledgeBase("agent1", settings);

        // Register for server events.
        _server.addPoseListener(this, null);
        _server.addWaypointListener(this, null);
        _server.getNumSensors(new FunctionObserver<Integer>() {
            @Override
            public void completed(Integer numSensors) {
                for (int i = 0; i < numSensors; ++i) {
                    _server.addSensorListener(i, MadaraVehicleService.this, null);
                }
            }

            @Override
            public void failed(FunctionError functionError) {
                logger.warn("Failed to get number of vehicle sensors.");
            }
        });
    }

    protected static TransportSettings configureTransport() {
        //Create transport settings for a multicast transport
        TransportSettings settings = new TransportSettings();
        settings.setHosts(new String[]{"239.255.0.1:4150"});
        settings.setType(TransportType.MULTICAST_TRANSPORT);
        return settings;
    }

    public static void setUtmPose(KnowledgeBase knowledge, String knowledgePath, UtmPose utmPose) {
        knowledge.set(knowledgePath + ".x", utmPose.pose.getX());
        knowledge.set(knowledgePath + ".y", utmPose.pose.getY());
        knowledge.set(knowledgePath + ".z", utmPose.pose.getZ());
        knowledge.set(knowledgePath + ".roll", utmPose.pose.getRotation().toRoll());
        knowledge.set(knowledgePath + ".roll", utmPose.pose.getRotation().toPitch());
        knowledge.set(knowledgePath + ".yaw", utmPose.pose.getRotation().toYaw());
        knowledge.set(knowledgePath + ".zone", utmPose.origin.zone);
        knowledge.set(knowledgePath + ".hemisphere", utmPose.origin.isNorth ? "North": "South");
    }

    @Override
    public void waypointUpdate(final VehicleServer.WaypointState waypointState) {
        _server.getWaypoints(new FunctionObserver<UtmPose[]>() {
            @Override
            public void completed(UtmPose[] utmPoses) {
                _knowledge.set(".waypoints.state", waypointState.name());
                for (int i = 0; i < utmPoses.length; ++i) {
                    setUtmPose(_knowledge, ".waypoints." + i, utmPoses[i]);
                }
            }

            @Override
            public void failed(FunctionError functionError) {
                logger.warn("Failed to retrieve waypoints.");
            }
        });
    }

    @Override
    public void receivedPose(UtmPose utmPose) {
        setUtmPose(_knowledge, ".pose", utmPose);
    }

    @Override
    public void receivedSensor(SensorData sensorData) {
        _knowledge.set(".sensor." + sensorData.channel, sensorData.data);
    }
}
