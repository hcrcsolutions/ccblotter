package com.fmr.ec3.oscc.receiver.handler;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.infra.NodeDeregisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.receiver.state.InfraStateWriter;
import com.fmr.ec3.oscc.receiver.websocket.WebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles infrastructure lifecycle and heartbeat events from both SIP servers and Media servers.
 * Lifecycle topic: ccblotter.infra.lifecycle, Heartbeat topic: ccblotter.infra.heartbeats.
 * All infra events are partitioned by nodeId.
 *
 * <h3>kafka-sender integration: SIP server and Media server → event mapping</h3>
 *
 * <pre>
 * ┌──────────────────────────────────────┬────────────────────┬─────────────┬───────────────────────┐
 * │ Server Trigger                      │ Event Type         │ Topic       │ Source                │
 * ├──────────────────────────────────────┼────────────────────┼─────────────┼───────────────────────┤
 * │ Process startup, ports bound        │ NODE_REGISTERED    │ lifecycle   │ SIP server, Media svr │
 * │ SIGTERM / graceful drain            │ NODE_DEREGISTERED  │ lifecycle   │ SIP server, Media svr │
 * │ Resource threshold crossed          │ NODE_ALARM         │ lifecycle   │ SIP server, Media svr │
 * │ Periodic timer (every 5s)           │ NODE_HEARTBEAT     │ heartbeats  │ SIP server, Media svr │
 * └──────────────────────────────────────┴────────────────────┴─────────────┴───────────────────────┘
 * </pre>
 *
 * <h4>NODE_REGISTERED — send once at startup after successful initialization</h4>
 * <ul>
 *   <li>SIP server: after binding SIP port (5060/5061) and joining the ACD cluster.
 *       nodeType="SIP", maxSessions = configured max concurrent dialogs.
 *       If the SIP server has trunk connectivity, include carrierName and trunkGroup.</li>
 *   <li>Media server: after binding RTP port range and registering with the media controller.
 *       nodeType="MEDIA", maxSessions = max concurrent RTP streams / 2.</li>
 *   <li>Fields: nodeId (unique, e.g. "sip-01", "media-042"), hostname, ipAddress,
 *       datacenter, region — from server config or environment variables.</li>
 * </ul>
 *
 * <h4>NODE_DEREGISTERED — send on graceful shutdown or unrecoverable failure</h4>
 * <ul>
 *   <li>Hook into JVM shutdown (Runtime.addShutdownHook or @PreDestroy) to send before exit.</li>
 *   <li>reason: "shutdown" for SIGTERM, "drain" for controlled drain,
 *       "health_check_failure" for self-detected failure.</li>
 * </ul>
 *
 * <h4>NODE_ALARM — send when resource monitoring detects threshold breach</h4>
 * <ul>
 *   <li>alarmType: "CPU_HIGH", "MEMORY_HIGH", "SESSIONS_NEAR_CAPACITY", "MOS_LOW",
 *       "PACKET_LOSS_HIGH", "LATENCY_HIGH".</li>
 *   <li>severity: "WARNING" (approaching limit) or "CRITICAL" (at/exceeding limit).</li>
 *   <li>thresholdValue: the configured threshold, actualValue: the measured value.</li>
 * </ul>
 *
 * <h4>NODE_HEARTBEAT — send on a fixed interval (5s recommended, configurable)</h4>
 * <ul>
 *   <li>SIP server metrics: cpu/memory from OS stats, activeSessions = current SIP dialog count,
 *       latency/jitter from SIP OPTIONS ping to peers, mosScore from RTP quality reports (RTCP-XR).
 *       Session breakdown: inbound/outbound from dialog direction, ivr/queue/agent/onHold
 *       from ACD state tracking.</li>
 *   <li>Media server metrics: cpu/memory from OS stats, activeSessions = current RTP stream pairs,
 *       latency/jitter/packetLoss from RTP stack stats (RTCP SR/RR), mosScore computed from
 *       R-factor. Session breakdown: count streams by associated call type from the media
 *       controller's session table.</li>
 *   <li>Heartbeat has a 90s TTL in Redis — if a server stops sending heartbeats,
 *       the key expires and the node appears offline.</li>
 * </ul>
 */
@Component
public class InfraEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InfraEventHandler.class);

    private final InfraStateWriter infraWriter;
    private final WebSocketBroadcaster broadcaster;

    public InfraEventHandler(InfraStateWriter infraWriter,
                              WebSocketBroadcaster broadcaster) {
        this.infraWriter = infraWriter;
        this.broadcaster = broadcaster;
    }

    public void handleHeartbeat(EventEnvelope<?> envelope) {
        NodeHeartbeatPayload p = (NodeHeartbeatPayload) envelope.payload();
        infraWriter.writeHeartbeat(p);
        broadcaster.broadcastInfrastructure();
    }

    public void handleLifecycle(EventEnvelope<?> envelope) {
        switch (envelope.eventType()) {
            case EventType.NODE_REGISTERED -> handleNodeRegistered(envelope);
            case EventType.NODE_DEREGISTERED -> handleNodeDeregistered(envelope);
            case EventType.NODE_ALARM -> handleNodeAlarm(envelope);
            default -> log.warn("Unknown infra lifecycle event type: {}", envelope.eventType());
        }
    }

    private void handleNodeRegistered(EventEnvelope<?> envelope) {
        NodeRegisteredPayload p = (NodeRegisteredPayload) envelope.payload();
        infraWriter.registerNode(p);
        broadcaster.broadcastInfrastructure();
    }

    private void handleNodeDeregistered(EventEnvelope<?> envelope) {
        NodeDeregisteredPayload p = (NodeDeregisteredPayload) envelope.payload();
        infraWriter.removeNodeState(p.nodeId(), null);
        infraWriter.updateTopologyVersion();
        broadcaster.broadcastInfrastructure();
        log.info("Node {} deregistered: {}", p.nodeId(), p.reason());
    }

    private void handleNodeAlarm(EventEnvelope<?> envelope) {
        log.warn("Node alarm: {}", envelope.payload());
    }
}
