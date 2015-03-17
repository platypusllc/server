package edu.cmu.ri.airboat.server;

import com.google.code.microlog4android.LoggerFactory;
import com.madara.KnowledgeBase;
import com.madara.KnowledgeList;
import com.madara.KnowledgeRecord;
import com.madara.Variables;
import com.madara.transport.QoSTransportSettings;
import com.madara.transport.TransportType;
import com.madara.transport.filters.RecordFilter;

import java.nio.charset.Charset;

import edu.cmu.ri.crw.AsyncVehicleServer;
import edu.cmu.ri.crw.FunctionObserver;
import edu.cmu.ri.crw.ImageListener;
import edu.cmu.ri.crw.PoseListener;
import edu.cmu.ri.crw.SensorListener;
import edu.cmu.ri.crw.VehicleServer;
import edu.cmu.ri.crw.WaypointListener;
import edu.cmu.ri.crw.data.SensorData;
import edu.cmu.ri.crw.data.UtmPose;

/**
 * Vehicle service that exposes a vehicle via MADARA fields.
 *
 * This is a lightweight wrapper that monitors the state variables in a MADARA knowledge base and
 * applies those values directly to a VehicleServer when they are changed.  Similarly, vehicle
 * server updates are immediately set as values in this knowledge base.
 */
public class MadaraVehicleService implements WaypointListener, PoseListener, SensorListener, ImageListener {
    private static final com.google.code.microlog4android.Logger logger = LoggerFactory
            .getLogger();
    /**
     * Handle incoming MADARA data by forwarding it to the vehicle server.
     */
    final RecordFilter _receivedMessageFilter = new RecordFilter() {
        /**
         * Listens for incoming records that are potentially commands for the server.
         */
        public KnowledgeRecord filter(KnowledgeList args, Variables variables) {
            KnowledgeRecord record = args.get(0);
            if (args.get(1).toString().contains(".pose")) {

                // Set server pose if we receive a change to the pose variable.
                _server.setPose(getUtmPose(record, ".pose"), null);

            } else if (args.get(1).toString().contains(".waypoint")) {

                // Deserialize a list of waypoints and begin execution.
                int numWaypoints = record.get(".waypoint.length");
                UtmPose waypoints[] = new UtmPose[numWaypoints];
                for (int i = 0; i < waypoints.length; ++i) {
                    waypoints[i] = getUtmPose(record, ".waypoint." + i);
                }
                String controller = record.get("controller");
                _server.startWaypoints(waypoints, null, null);
            }

            return result;
        }
    };

    /** Local reference to vehicle server. */
    protected AsyncVehicleServer _server;

    /** Local reference to MADARA knowledge base. */
    protected KnowledgeBase _knowledge;

    /**
     * Initialize a MADARA VehicleService which interfaces the provided VehicleServer to a MADARA
     * knowledge base with default multicast settings.
     *
     * @param server the VehicleServer that should be interfaced to MADARA
     */
    public MadaraVehicleService(VehicleServer server) {
        QoSTransportSettings settings = configureTransport();
        // TODO: where should we get the name of the vehicle?
        _knowledge = new KnowledgeBase("agent1", settings);

        // Register for server events.
        _server.addPoseListener(this, null);
        _server.addWaypointListener(this, null);
        _server.addImageListener(this, null);
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

        // Register for MADARA data update events.
        settings.addReceiveFilter(KnowledgeType.ALL_TYPES, _receivedMessageFilter);
    }

    /**
     * Set up a standard QOS multicast transport for MADARA to use.
     * @return a simple multicast transport settings object for a knowledge base
     */
    protected static QoSTransportSettings configureTransport() {
        //Create transport settings for a multicast transport
        QoSTransportSettings settings = new QoSTransportSettings();
        settings.setHosts(new String[]{"239.255.0.1:4150"});
        settings.setType(TransportType.MULTICAST_TRANSPORT);
        return settings;
    }

    /**
     * Conversion method that takes a UTMPose and a variable name in MADARA and set the variable in
     * the provided knowledge base to the given UtmPose.
     *
     * @param knowledge a knowledge base that will be updated
     * @param knowledgePath the name of the variable in the knowledge base that should be updated
     * @param utmPose the UTM pose that the knowledge base will be updated with
     */
    public static void setUtmPose(KnowledgeBase knowledge, String knowledgePath, UtmPose utmPose) {
        knowledge.set(knowledgePath + ".x", utmPose.pose.getX());
        knowledge.set(knowledgePath + ".y", utmPose.pose.getY());
        knowledge.set(knowledgePath + ".z", utmPose.pose.getZ());
        knowledge.set(knowledgePath + ".roll", utmPose.pose.getRotation().toRoll());
        knowledge.set(knowledgePath + ".roll", utmPose.pose.getRotation().toPitch());
        knowledge.set(knowledgePath + ".yaw", utmPose.pose.getRotation().toYaw());
        knowledge.set(knowledgePath + ".zone", utmPose.origin.zone);
        knowledge.set(knowledgePath + ".hemisphere", utmPose.origin.isNorth ? "North" : "South");
    }

    /**
     * Conversion method that takes a variable name in a MADARA Knowledge Record and extracts a
     * UtmPose from that variable.
     *
     * @param record the knowledge record containing the UTM pose
     * @param knowledgePath the name of the variable in the knowledge base that contains the pose
     * @return the UTM pose that was stored in the specified variable
     */
    public static UtmPose getUtmPose(KnowledgeRecord record, String knowledgePath) {
        // Convert a knowledge record into a UTM pose.
        // TODO: how does this conversion work?
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    /**
     * Receive changes in waypoint state from the vehicle server and update the MADARA waypoint
     * variables to match.
     *
     * @param waypointState the latest state of vehicle waypoint execution
     */
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

    @Override
    public void receivedImage(byte[] bytes) {
        // TODO: how do you set an IMAGE_JPEG to a variable in the knowledge base?
        _knowledge.set(".image", new String(bytes));
    }
}

