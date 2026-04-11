package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeDeregisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

@Component
public class KamailioNode {

    private static final Logger log = LoggerFactory.getLogger(KamailioNode.class);

    private final EventProducer eventProducer;
    private final KamailioProperties props;
    private final EvApiConnectionManager connectionManager;
    private final AlarmEvaluator alarmEvaluator;
    private final OperatingSystemMXBean osMxBean =
            ManagementFactory.getOperatingSystemMXBean();
    private volatile boolean registered;

    public KamailioNode(EventProducer eventProducer,
                        KamailioProperties props,
                        EvApiConnectionManager connectionManager,
                        AlarmEvaluator alarmEvaluator) {
        this.eventProducer = eventProducer;
        this.props = props;
        this.connectionManager = connectionManager;
        this.alarmEvaluator = alarmEvaluator;
    }

    @PostConstruct
    public void init() {
        connectionManager.addOnConnectedCallback(this::register);
    }

    void register() {
        String nodeId = props.getServerId();

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
                        nodeId, "SIP", hostname, ipAddress,
                        props.getDatacenter(), props.getRegion(),
                        props.getMaxSessions(), null, null)
        );

        registered = true;
        log.info("Kamailio node registered: {} in {}/{}",
                nodeId, props.getDatacenter(), props.getRegion());
    }

    @Scheduled(
            fixedDelayString = "${kamailio.heartbeat-interval-ms:5000}",
            initialDelay = 3000)
    public void sendHeartbeat() {
        if (!registered) {
            return;
        }

        String nodeId = props.getServerId();
        int maxSessions = props.getMaxSessions();

        int activeSessions = 0;
        double cpuPercent = 0;
        double memPercent = getMemoryPercent();

        if (connectionManager.isConnected()) {
            activeSessions = fetchActiveDialogCount();
            cpuPercent = getCpuPercent();
        } else {
            log.debug("EVAPI disconnected — sending degraded heartbeat for {}", nodeId);
        }

        NodeMetricsDto metrics = new NodeMetricsDto(
                cpuPercent, memPercent,
                0, 0, 0, 0, 0
        );

        SessionBreakdownDto breakdown = new SessionBreakdownDto(
                activeSessions, 0, 0, 0, 0, 0
        );

        eventProducer.send(
                KafkaTopics.INFRA_HEARTBEATS, nodeId,
                EventType.NODE_HEARTBEAT, nodeId, nodeId,
                new NodeHeartbeatPayload(nodeId, "SIP", activeSessions, maxSessions,
                        metrics, breakdown, null, null)
        );

        alarmEvaluator.evaluate(nodeId, cpuPercent, memPercent,
                activeSessions, maxSessions);

        log.debug("Heartbeat sent: {} sessions={}/{} cpu={} mem={}",
                nodeId, activeSessions, maxSessions, cpuPercent, memPercent);
    }

    @PreDestroy
    public void deregister() {
        if (!registered) {
            return;
        }
        String nodeId = props.getServerId();
        eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_DEREGISTERED, nodeId, nodeId,
                new NodeDeregisteredPayload(nodeId, "shutdown")
        );
        log.info("Kamailio node deregistered: {}", nodeId);
    }

    int fetchActiveDialogCount() {
        try {
            JsonNode result = connectionManager
                    .sendRequest("dlg.stats_active", null)
                    .get(props.getStateSyncRpcTimeoutMs(), TimeUnit.MILLISECONDS);
            if (result == null) {
                return 0;
            }
            JsonNode count = result.get("active");
            if (count != null && count.isNumber()) {
                return count.asInt();
            }
            if (result.isNumber()) {
                return result.asInt();
            }
            return 0;
        } catch (Exception e) {
            log.debug("Failed to fetch dialog count: {}", e.getMessage());
            return 0;
        }
    }

    double getMemoryPercent() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            long total = sunBean.getTotalMemorySize();
            long free = sunBean.getFreeMemorySize();
            if (total > 0) {
                return Math.round((double) (total - free) / total * 1000.0) / 10.0;
            }
        }
        return 0;
    }

    double getCpuPercent() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double load = sunBean.getCpuLoad();
            if (load >= 0) {
                return Math.round(load * 1000.0) / 10.0;
            }
        }
        return 0;
    }
}
