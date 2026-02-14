package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfraHeartbeatSimulatorTest {

    @Mock
    private EventProducer eventProducer;

    @Test
    void sendHeartbeatsForAllRegisteredNodes() {
        SimulationProperties props = new SimulationProperties();
        props.setSipServerCount(2);
        props.setMediaServerCount(3);

        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();
        reset(eventProducer);

        InfraHeartbeatSimulator heartbeat = new InfraHeartbeatSimulator(eventProducer, bootstrap);
        heartbeat.sendHeartbeats();

        // 2 SIP + 3 MEDIA = 5 heartbeat events
        verify(eventProducer, times(5)).send(
            eq(KafkaTopics.INFRA_HEARTBEATS), anyString(),
            eq(EventType.NODE_HEARTBEAT), anyString(), anyString(),
            any(NodeHeartbeatPayload.class));
    }

    @Test
    void heartbeatPayloadHasValidMetrics() {
        SimulationProperties props = new SimulationProperties();
        props.setSipServerCount(1);
        props.setMediaServerCount(0);

        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();
        reset(eventProducer);

        InfraHeartbeatSimulator heartbeat = new InfraHeartbeatSimulator(eventProducer, bootstrap);
        heartbeat.sendHeartbeats();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer).send(
            eq(KafkaTopics.INFRA_HEARTBEATS), eq("sip-1"),
            eq(EventType.NODE_HEARTBEAT), anyString(), anyString(),
            payloadCaptor.capture());

        NodeHeartbeatPayload p = (NodeHeartbeatPayload) payloadCaptor.getValue();
        assertEquals("sip-1", p.nodeId());
        assertEquals("SIP", p.nodeType());
        assertEquals(500, p.maxSessions());
        assertTrue(p.activeSessions() >= 0 && p.activeSessions() <= 500);

        assertNotNull(p.metrics());
        assertTrue(p.metrics().cpuPercent() >= 0 && p.metrics().cpuPercent() <= 100);
        assertTrue(p.metrics().memoryPercent() >= 0 && p.metrics().memoryPercent() <= 100);
        assertTrue(p.metrics().latencyMs() >= 1);
        assertTrue(p.metrics().jitterMs() >= 1);
        assertTrue(p.metrics().mosScore() >= 10);

        assertNotNull(p.sessionBreakdown());
    }
}
