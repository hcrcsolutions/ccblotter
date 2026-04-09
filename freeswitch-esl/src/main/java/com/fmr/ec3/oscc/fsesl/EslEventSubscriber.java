package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EslEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(EslEventSubscriber.class);
    private static final int MAX_ALARMS_PER_MINUTE = 10;

    private final EslConnectionManager connectionManager;
    private final EventProducer eventProducer;
    private final FreeSwitchProperties props;

    private final AtomicInteger alarmCount = new AtomicInteger(0);
    private volatile Instant windowStart = Instant.now();

    public EslEventSubscriber(EslConnectionManager connectionManager,
                              EventProducer eventProducer,
                              FreeSwitchProperties props) {
        this.connectionManager = connectionManager;
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        connectionManager.setEventListener(this::onEslEvent);
        connectionManager.setOnConnectedCallback(this::subscribe);
        if (connectionManager.isConnected()) {
            subscribe();
        }
    }

    void subscribe() {
        connectionManager.sendCommand("event plain LOG");
        connectionManager.sendCommand("log 3");
        log.info("Subscribed to FreeSWITCH LOG events (level ERR)");
    }

    void onEslEvent(EslClient.EslMessage event) {
        String logLevel = event.headers().get("Log-Level");
        if (logLevel == null) {
            return;
        }

        int level;
        try {
            level = Integer.parseInt(logLevel);
        } catch (NumberFormatException e) {
            return;
        }

        // FreeSWITCH log levels: 0=CONSOLE, 1=ALERT, 2=CRIT, 3=ERR
        if (level > 3) {
            return;
        }

        if (!tryAcquireRateLimit()) {
            return;
        }

        String nodeId = props.getNodeId();
        String logBody = event.body();
        if (logBody != null && logBody.length() > 200) {
            logBody = logBody.substring(0, 200);
        }

        log.warn("FreeSWITCH error log (level {}): {}", level, logBody);

        eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_ALARM, nodeId, nodeId,
                new NodeAlarmPayload(nodeId, "LOG_ERROR", "WARNING", 0, 1)
        );
    }

    boolean tryAcquireRateLimit() {
        Instant now = Instant.now();
        Instant windowEnd = windowStart.plusSeconds(60);
        if (now.isAfter(windowEnd)) {
            windowStart = now;
            alarmCount.set(1);
            return true;
        }
        return alarmCount.incrementAndGet() <= MAX_ALARMS_PER_MINUTE;
    }

    // Visible for testing
    AtomicInteger getAlarmCount() {
        return alarmCount;
    }

    void setWindowStart(Instant start) {
        this.windowStart = start;
    }
}
