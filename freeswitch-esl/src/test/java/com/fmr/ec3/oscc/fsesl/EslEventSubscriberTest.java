package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EslEventSubscriberTest {

    private EslConnectionManager connectionManager;
    private EventProducer eventProducer;
    private FreeSwitchProperties props;
    private EslEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        connectionManager = mock(EslConnectionManager.class);
        eventProducer = mock(EventProducer.class);
        props = new FreeSwitchProperties();
        props.setNodeId("fs-1");
        subscriber = new EslEventSubscriber(connectionManager, eventProducer, props);
    }

    @Test
    void logErrorEventEmitsAlarm() {
        EslClient.EslMessage event = new EslClient.EslMessage(
                Map.of("Log-Level", "3"), "switch_core.c:1234 Error in something"
        );

        subscriber.onEslEvent(event);

        ArgumentCaptor<NodeAlarmPayload> captor = ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("fs-1"),
                eq(EventType.NODE_ALARM), eq("fs-1"), eq("fs-1"),
                captor.capture()
        );

        NodeAlarmPayload alarm = captor.getValue();
        assertEquals("LOG_ERROR", alarm.alarmType());
        assertEquals("WARNING", alarm.severity());
        assertEquals("fs-1", alarm.nodeId());
    }

    @Test
    void nonErrorLogLevelIgnored() {
        // Log level 4 = WARNING, should be ignored (we only want ERR=3 and below)
        EslClient.EslMessage event = new EslClient.EslMessage(
                Map.of("Log-Level", "4"), "Some warning"
        );

        subscriber.onEslEvent(event);

        verifyNoInteractions(eventProducer);
    }

    @Test
    void missingLogLevelIgnored() {
        EslClient.EslMessage event = new EslClient.EslMessage(
                Map.of("Event-Name", "LOG"), "Some log"
        );

        subscriber.onEslEvent(event);

        verifyNoInteractions(eventProducer);
    }

    @Test
    void rateLimitCapsAlarmsPerMinute() {
        EslClient.EslMessage event = new EslClient.EslMessage(
                Map.of("Log-Level", "3"), "Error"
        );

        // Send 15 events — only 10 should produce alarms
        for (int i = 0; i < 15; i++) {
            subscriber.onEslEvent(event);
        }

        verify(eventProducer, times(10)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rateLimitResetsAfterWindowExpires() {
        EslClient.EslMessage event = new EslClient.EslMessage(
                Map.of("Log-Level", "3"), "Error"
        );

        // Fill up the window
        for (int i = 0; i < 10; i++) {
            subscriber.onEslEvent(event);
        }
        verify(eventProducer, times(10)).send(any(), any(), any(), any(), any(), any());

        // Reset window by setting start to 2 minutes ago
        subscriber.setWindowStart(Instant.now().minusSeconds(120));

        // Should allow more alarms now
        subscriber.onEslEvent(event);
        verify(eventProducer, times(11)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void subscribeIssuesEventAndLogCommands() {
        subscriber.subscribe();

        verify(connectionManager).sendCommand("event plain LOG");
        verify(connectionManager).sendCommand("log 3");
    }
}
