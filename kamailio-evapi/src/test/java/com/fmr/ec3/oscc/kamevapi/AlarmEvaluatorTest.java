package com.fmr.ec3.oscc.kamevapi;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
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
        KamailioProperties props = new KamailioProperties();
        evaluator = new AlarmEvaluator(eventProducer, props);
    }

    @Test
    void noAlarmWhenBelowThresholds() {
        evaluator.evaluate("kam-1", 50, 60, 100, 500);

        verifyNoInteractions(eventProducer);
        assertTrue(evaluator.getActiveAlarms().isEmpty());
    }

    @Test
    void cpuWarningAlarmEmitted() {
        evaluator.evaluate("kam-1", 75, 50, 100, 500);

        ArgumentCaptor<NodeAlarmPayload> captor =
                ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("kam-1"),
                eq(EventType.NODE_ALARM), eq("kam-1"), eq("kam-1"),
                captor.capture());

        NodeAlarmPayload alarm = captor.getValue();
        assertEquals("CPU_HIGH", alarm.alarmType());
        assertEquals("WARNING", alarm.severity());
        assertEquals(75, alarm.actualValue(), 0.01);
    }

    @Test
    void cpuCriticalAlarmEmitted() {
        evaluator.evaluate("kam-1", 92, 50, 100, 500);

        ArgumentCaptor<NodeAlarmPayload> captor =
                ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("kam-1"),
                eq(EventType.NODE_ALARM), eq("kam-1"), eq("kam-1"),
                captor.capture());

        assertEquals("CRITICAL", captor.getValue().severity());
    }

    @Test
    void hysteresisPreventsRepeatAtSameSeverity() {
        evaluator.evaluate("kam-1", 75, 50, 100, 500);
        verify(eventProducer, times(1)).send(any(), any(), any(), any(), any(), any());

        evaluator.evaluate("kam-1", 78, 50, 100, 500);
        verify(eventProducer, times(1)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void severityEscalationEmitsNewAlarm() {
        evaluator.evaluate("kam-1", 75, 50, 100, 500);
        verify(eventProducer, times(1)).send(any(), any(), any(), any(), any(), any());

        evaluator.evaluate("kam-1", 92, 50, 100, 500);
        verify(eventProducer, times(2)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void alarmClearsWhenBelowThreshold() {
        evaluator.evaluate("kam-1", 75, 50, 100, 500);
        assertFalse(evaluator.getActiveAlarms().isEmpty());

        evaluator.evaluate("kam-1", 50, 50, 100, 500);
        assertTrue(evaluator.getActiveAlarms().isEmpty());

        ArgumentCaptor<NodeAlarmPayload> captor =
                ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer, times(2)).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("kam-1"),
                eq(EventType.NODE_ALARM), eq("kam-1"), eq("kam-1"),
                captor.capture());
        assertEquals("CLEAR", captor.getAllValues().get(1).severity());
    }

    @Test
    void memoryAlarmEmitted() {
        evaluator.evaluate("kam-1", 50, 80, 100, 500);

        ArgumentCaptor<NodeAlarmPayload> captor =
                ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(any(), any(), any(), any(), any(), captor.capture());
        assertEquals("MEMORY_HIGH", captor.getValue().alarmType());
        assertEquals("WARNING", captor.getValue().severity());
    }

    @Test
    void sessionCapacityAlarmEmitted() {
        evaluator.evaluate("kam-1", 50, 50, 425, 500);

        ArgumentCaptor<NodeAlarmPayload> captor =
                ArgumentCaptor.forClass(NodeAlarmPayload.class);
        verify(eventProducer).send(any(), any(), any(), any(), any(), captor.capture());
        assertEquals("SESSIONS_NEAR_CAPACITY", captor.getValue().alarmType());
        assertEquals("WARNING", captor.getValue().severity());
    }

    @Test
    void multipleAlarmTypesIndependent() {
        evaluator.evaluate("kam-1", 75, 80, 100, 500);

        verify(eventProducer, times(2)).send(any(), any(), any(), any(), any(), any());
        assertEquals(2, evaluator.getActiveAlarms().size());
    }
}
