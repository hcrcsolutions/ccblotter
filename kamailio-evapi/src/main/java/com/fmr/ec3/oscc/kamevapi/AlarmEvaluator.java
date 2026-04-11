package com.fmr.ec3.oscc.kamevapi;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlarmEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluator.class);

    static final String ALARM_CPU = "CPU_HIGH";
    static final String ALARM_MEMORY = "MEMORY_HIGH";
    static final String ALARM_SESSIONS = "SESSIONS_NEAR_CAPACITY";

    private final EventProducer eventProducer;
    private final KamailioProperties props;
    private final Map<String, String> activeAlarms = new ConcurrentHashMap<>();

    public AlarmEvaluator(EventProducer eventProducer, KamailioProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    public void evaluate(String nodeId, double cpuPercent, double memPercent,
                         int activeSessions, int maxSessions) {
        checkThreshold(nodeId, ALARM_CPU, cpuPercent,
                props.getAlarmCpuWarning(), props.getAlarmCpuCritical());

        checkThreshold(nodeId, ALARM_MEMORY, memPercent,
                props.getAlarmMemoryWarning(), props.getAlarmMemoryCritical());

        double sessionPercent = maxSessions > 0
                ? (double) activeSessions / maxSessions * 100.0
                : 0;
        checkThreshold(nodeId, ALARM_SESSIONS, sessionPercent,
                props.getAlarmSessionWarning(), props.getAlarmSessionCritical());
    }

    private void checkThreshold(String nodeId, String alarmType, double actual,
                                double warningThreshold, double criticalThreshold) {
        String newSeverity = determineSeverity(actual, warningThreshold, criticalThreshold);
        String currentSeverity = activeAlarms.get(alarmType);

        if (newSeverity == null) {
            if (currentSeverity != null) {
                activeAlarms.remove(alarmType);
                sendAlarm(nodeId, alarmType, "CLEAR", warningThreshold, actual);
            }
        } else if (!newSeverity.equals(currentSeverity)) {
            activeAlarms.put(alarmType, newSeverity);
            sendAlarm(nodeId, alarmType, newSeverity,
                    "CRITICAL".equals(newSeverity) ? criticalThreshold : warningThreshold,
                    actual);
        }
    }

    private String determineSeverity(double actual, double warning, double critical) {
        if (actual >= critical) {
            return "CRITICAL";
        }
        if (actual >= warning) {
            return "WARNING";
        }
        return null;
    }

    private void sendAlarm(String nodeId, String alarmType, String severity,
                           double threshold, double actual) {
        log.info("Alarm {} {} for {}: actual={} threshold={}",
                alarmType, severity, nodeId, actual, threshold);

        eventProducer.send(
                KafkaTopics.INFRA_LIFECYCLE, nodeId,
                EventType.NODE_ALARM, nodeId, nodeId,
                new NodeAlarmPayload(nodeId, alarmType, severity, threshold, actual)
        );
    }

    Map<String, String> getActiveAlarms() {
        return activeAlarms;
    }
}
