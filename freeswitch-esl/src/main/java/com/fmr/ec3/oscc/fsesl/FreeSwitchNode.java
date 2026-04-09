package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.CodecUsageDto;
import com.fmr.ec3.oscc.common.payload.infra.GatewayStatusDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeDeregisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

@Component
public class FreeSwitchNode {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchNode.class);

    private final EventProducer eventProducer;
    private final FreeSwitchProperties props;
    private final FreeSwitchProbe probe;
    private final EslConnectionManager connectionManager;
    private final AlarmEvaluator alarmEvaluator;
    private String resolvedNodeType;

    public FreeSwitchNode(EventProducer eventProducer,
                          FreeSwitchProperties props,
                          FreeSwitchProbe probe,
                          EslConnectionManager connectionManager,
                          AlarmEvaluator alarmEvaluator) {
        this.eventProducer = eventProducer;
        this.props = props;
        this.probe = probe;
        this.connectionManager = connectionManager;
        this.alarmEvaluator = alarmEvaluator;
    }

    @PostConstruct
    public void register() {
        String nodeId = props.getNodeId();
        resolvedNodeType = probe.detectNodeType();

        String hostname;
        String ipAddress;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            hostname = localHost.getHostName();
            ipAddress = localHost.getHostAddress();
        } catch (UnknownHostException e) {
            hostname = nodeId + "." + props.getDatacenter() + ".local";
            ipAddress = "127.0.0.1";
            log.warn("Could not resolve local hostname, using fallback: {}", hostname);
        }

        eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_REGISTERED, nodeId, nodeId,
                new NodeRegisteredPayload(
                        nodeId, resolvedNodeType, hostname, ipAddress,
                        props.getDatacenter(), props.getRegion(),
                        props.getMaxSessions(), null, null)
        );

        log.info("FreeSWITCH node registered: {} type={} in {}/{}",
                nodeId, resolvedNodeType, props.getDatacenter(), props.getRegion());
    }

    @Scheduled(fixedDelayString = "${freeswitch.heartbeat-interval-ms:5000}", initialDelay = 3000)
    public void sendHeartbeat() {
        String nodeId = props.getNodeId();
        int maxSessions = props.getMaxSessions();

        int activeSessions = 0;
        double cpuPercent = 0;
        double memPercent = probe.getMemoryPercent();
        int channelCount = 0;
        int callCount = 0;
        List<GatewayStatusDto> gateways = List.of();
        List<CodecUsageDto> codecUsage = null;

        if (connectionManager.isConnected()) {
            Optional<FreeSwitchProbe.StatusResult> statusOpt = probe.fetchStatus();
            if (statusOpt.isPresent()) {
                FreeSwitchProbe.StatusResult status = statusOpt.get();
                activeSessions = status.currentSessions();
                cpuPercent = Math.round((100.0 - status.idleCpu()) * 10.0) / 10.0;
                if (status.maxSessions() > 0) {
                    maxSessions = status.maxSessions();
                }
            }
            channelCount = probe.fetchChannelCount();
            callCount = probe.fetchCallCount();
            if (channelCount >= 0) {
                activeSessions = channelCount;
            }
            gateways = probe.fetchGatewayStatuses();
            if (props.isDetailedBreakdownEnabled()) {
                codecUsage = probe.fetchCodecUsage();
            }
        } else {
            log.debug("ESL disconnected — sending degraded heartbeat for {}", nodeId);
        }

        NodeMetricsDto metrics = new NodeMetricsDto(
                cpuPercent, memPercent,
                0, 0, 0, 0, 0
        );

        SessionBreakdownDto breakdown = new SessionBreakdownDto(
                callCount >= 0 ? callCount : 0,
                0, 0, 0, 0, 0
        );

        eventProducer.send(
                KafkaTopics.INFRA_HEARTBEATS, nodeId,
                EventType.NODE_HEARTBEAT, nodeId, nodeId,
                new NodeHeartbeatPayload(nodeId, resolvedNodeType, activeSessions, maxSessions,
                        metrics, breakdown, gateways, codecUsage)
        );

        alarmEvaluator.evaluate(nodeId, cpuPercent, memPercent, activeSessions, maxSessions);

        log.debug("Heartbeat sent: {} sessions={}/{} cpu={} mem={}",
                nodeId, activeSessions, maxSessions, cpuPercent, memPercent);
    }

    @PreDestroy
    public void deregister() {
        String nodeId = props.getNodeId();
        eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_DEREGISTERED, nodeId, nodeId,
                new NodeDeregisteredPayload(nodeId, "shutdown")
        );
        log.info("FreeSWITCH node deregistered: {}", nodeId);
    }
}
