package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InfraTopologyBootstrap {

    private static final Logger log = LoggerFactory.getLogger(InfraTopologyBootstrap.class);

    private final EventProducer eventProducer;
    private final SimulationProperties props;

    private static final String[] DATACENTERS = {"dc1", "dc2", "dc3", "dc4"};
    private static final String[] REGIONS = {"us-east", "us-east", "us-west", "us-west"};

    private final List<String> registeredNodeIds = new ArrayList<>();

    public InfraTopologyBootstrap(EventProducer eventProducer, SimulationProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void bootstrap() {
        registerSipServers();
        registerMediaServers();
        log.info("Infrastructure topology bootstrapped: {} SIP + {} MEDIA servers",
            props.getSipServerCount(), props.getMediaServerCount());
    }

    public List<String> getRegisteredNodeIds() {
        return registeredNodeIds;
    }

    private void registerSipServers() {
        for (int i = 1; i <= props.getSipServerCount(); i++) {
            int dcIdx = i - 1;
            String nodeId = "sip-" + i;
            String sourceId = nodeId;

            eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_REGISTERED, sourceId, nodeId,
                new NodeRegisteredPayload(
                    nodeId, "SIP",
                    String.format("sip-%02d.%s.example.com", i, DATACENTERS[dcIdx]),
                    String.format("10.%d.1.10", i),
                    DATACENTERS[dcIdx], REGIONS[dcIdx],
                    500, null, null)
            );
            registeredNodeIds.add(nodeId);
        }
    }

    private void registerMediaServers() {
        for (int i = 1; i <= props.getMediaServerCount(); i++) {
            int dcIdx = (i - 1) / 30;
            if (dcIdx >= DATACENTERS.length) dcIdx = DATACENTERS.length - 1;
            String nodeId = "media-" + i;
            String sourceId = nodeId;

            eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_REGISTERED, sourceId, nodeId,
                new NodeRegisteredPayload(
                    nodeId, "MEDIA",
                    String.format("media-%03d.%s.example.com", i, DATACENTERS[dcIdx]),
                    String.format("10.%d.%d.%d", dcIdx + 1, 2 + (i % 10), 10 + ((i - 1) % 30)),
                    DATACENTERS[dcIdx], REGIONS[dcIdx],
                    100, null, null)
            );
            registeredNodeIds.add(nodeId);
        }
    }
}
