package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlarmEvaluatorTest {

    private EventProducer eventProducer;
    private AlarmEvaluator evaluator;

    @BeforeEach
    void setUp() {
        eventProducer = mock(EventProducer.class);
        FreeSwitchProperties props = new FreeSwitchProperties();
        evaluator = new AlarmEvaluator(eventProducer, props);
    }

    @Test
    void noAlarmWhenBelowThresholds() {
        evaluator.evaluate("fs-1", 50, 60, 100, 1000);

        verifyNoInteractions(eventProducer);
        assertTrue(evaluator.getActiveAlarms().isEmpty());
    }

    @Test
    void cpuWarningAlarmEmitted() {
        evaluator.evaluate("fs-1", 75, 50, 100, 1000);

        ArgumentCaptor<NodeAlarmPayload> captor = ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("fs-1"),
                eq(EventType.NODE_ALARM), eq("fs-1"), eq("fs-1"),
                captor.capture()
        );

        NodeAlarmPayload alarm = captor.getValue();
        assertEquals("CPU_HIGH", alarm.alarmType());
        assertEquals("WARNING", alarm.severity());
        assertEquals(75, alarm.actualValue(), 0.01);
    }

    @Test
    void cpuCriticalAlarmEmitted() {
        evaluator.evaluate("fs-1", 92, 50, 100, 1000);

        ArgumentCaptor<NodeAlarmPayload> captor = ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("fs-1"),
                eq(EventType.NODE_ALARM), eq("fs-1"), eq("fs-1"),
                captor.capture()
        );

        assertEquals("CRITICAL", captor.getValue().severity());
    }

    @Test
    void hysteresisPreventsRepeatAtSameSeverity() {
        // First call — triggers WARNING
        evaluator.evaluate("fs-1", 75, 50, 100, 1000);
        verify(eventProducer, times(1)).send(any(), any(), any(), any(), any(), any());

        // Second call at same severity — no new alarm
        evaluator.evaluate("fs-1", 78, 50, 100, 1000);
        verify(eventProducer, times(1)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void severityEscalationEmitsNewAlarm() {
        // WARNING
        evaluator.evaluate("fs-1", 75, 50, 100, 1000);
        verify(eventProducer, times(1)).send(any(), any(), any(), any(), any(), any());

        // CRITICAL
        evaluator.evaluate("fs-1", 92, 50, 100, 1000);
        verify(eventProducer, times(2)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void alarmClearsWhenBelowThreshold() {
        // Trigger WARNING
        evaluator.evaluate("fs-1", 75, 50, 100, 1000);
        assertFalse(evaluator.getActiveAlarms().isEmpty());

        // Drop below threshold
        evaluator.evaluate("fs-1", 50, 50, 100, 1000);
        assertTrue(evaluator.getActiveAlarms().isEmpty());

        // Verify CLEAR was sent
        ArgumentCaptor<NodeAlarmPayload> captor = ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer, times(2)).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("fs-1"),
                eq(EventType.NODE_ALARM), eq("fs-1"), eq("fs-1"),
                captor.capture()
        );
        assertEquals("CLEAR", captor.getAllValues().get(1).severity());
    }

    @Test
    void memoryAlarmEmitted() {
        evaluator.evaluate("fs-1", 50, 80, 100, 1000);

        ArgumentCaptor<NodeAlarmPayload> captor = ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(any(), any(), any(), any(), any(), captor.capture());
        assertEquals("MEMORY_HIGH", captor.getValue().alarmType());
        assertEquals("WARNING", captor.getValue().severity());
    }

    @Test
    void sessionCapacityAlarmEmitted() {
        evaluator.evaluate("fs-1", 50, 50, 850, 1000);

        ArgumentCaptor<NodeAlarmPayload> captor = ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(any(), any(), any(), any(), any(), captor.capture());
        assertEquals("SESSIONS_NEAR_CAPACITY", captor.getValue().alarmType());
        assertEquals("WARNING", captor.getValue().severity());
    }

    @Test
    void multipleAlarmTypesIndependent() {
        // Both CPU and memory high
        evaluator.evaluate("fs-1", 75, 80, 100, 1000);

        verify(eventProducer, times(2)).send(any(), any(), any(), any(), any(), any());
        assertEquals(2, evaluator.getActiveAlarms().size());
    }
}
