package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfraTopologyBootstrapTest {

    @Mock
    private EventProducer eventProducer;

    private SimulationProperties props;

    @BeforeEach
    void setUp() {
        props = new SimulationProperties();
        props.setSipServerCount(4);
        props.setMediaServerCount(120);
    }

    @Test
    void bootstrapRegistersAllNodes() {
        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();

        // 4 SIP + 120 MEDIA = 124 NODE_REGISTERED events
        verify(eventProducer, times(124)).send(
            eq(KafkaTopics.INFRA_LIFECYCLE), anyString(),
            eq(EventType.NODE_REGISTERED), anyString(), anyString(),
            any(NodeRegisteredPayload.class));
    }

    @Test
    void bootstrapTracksRegisteredNodeIds() {
        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();

        List<String> nodeIds = bootstrap.getRegisteredNodeIds();
        assertEquals(124, nodeIds.size());
        assertTrue(nodeIds.contains("sip-1"));
        assertTrue(nodeIds.contains("sip-4"));
        assertTrue(nodeIds.contains("media-1"));
        assertTrue(nodeIds.contains("media-120"));
    }

    @Test
    void sipServersHaveCorrectProperties() {
        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.INFRA_LIFECYCLE), eq("sip-1"),
            eq(EventType.NODE_REGISTERED), anyString(), anyString(),
            payloadCaptor.capture());

        NodeRegisteredPayload sip1 = (NodeRegisteredPayload) payloadCaptor.getValue();
        assertEquals("sip-1", sip1.nodeId());
        assertEquals("SIP", sip1.nodeType());
        assertEquals(500, sip1.maxSessions());
        assertEquals("dc1", sip1.datacenter());
        assertEquals("us-east", sip1.region());
    }

    @Test
    void mediaServersHaveCorrectProperties() {
        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.INFRA_LIFECYCLE), eq("media-1"),
            eq(EventType.NODE_REGISTERED), anyString(), anyString(),
            payloadCaptor.capture());

        NodeRegisteredPayload media1 = (NodeRegisteredPayload) payloadCaptor.getValue();
        assertEquals("media-1", media1.nodeId());
        assertEquals("MEDIA", media1.nodeType());
        assertEquals(100, media1.maxSessions());
    }

    @Test
    void smallTopologyWorks() {
        props.setSipServerCount(1);
        props.setMediaServerCount(2);

        InfraTopologyBootstrap bootstrap = new InfraTopologyBootstrap(eventProducer, props);
        bootstrap.bootstrap();

        assertEquals(3, bootstrap.getRegisteredNodeIds().size());
        verify(eventProducer, times(3)).send(
            eq(KafkaTopics.INFRA_LIFECYCLE), anyString(),
            eq(EventType.NODE_REGISTERED), anyString(), anyString(), any());
    }
}
