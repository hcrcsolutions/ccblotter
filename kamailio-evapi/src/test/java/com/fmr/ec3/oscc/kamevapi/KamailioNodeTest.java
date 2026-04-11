package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeDeregisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KamailioNodeTest {

    private EventProducer eventProducer;
    private EvApiConnectionManager connectionManager;
    private AlarmEvaluator alarmEvaluator;
    private KamailioProperties props;
    private KamailioNode node;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        eventProducer = mock(EventProducer.class);
        connectionManager = mock(EvApiConnectionManager.class);
        alarmEvaluator = mock(AlarmEvaluator.class);
        props = new KamailioProperties();
        props.setServerId("kam-1");
        props.setDatacenter("dc1");
        props.setRegion("us-east");
        props.setMaxSessions(500);
        props.setStateSyncRpcTimeoutMs(1000);
        node = new KamailioNode(eventProducer, props, connectionManager, alarmEvaluator);
    }

    @Test
    void registerSendsNodeRegistered() {
        node.register();

        ArgumentCaptor<NodeRegisteredPayload> captor =
                ArgumentCaptor.forClass(NodeRegisteredPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("kam-1"),
                eq(EventType.NODE_REGISTERED), eq("kam-1"), eq("kam-1"),
                captor.capture());

        NodeRegisteredPayload payload = captor.getValue();
        assertEquals("kam-1", payload.nodeId());
        assertEquals("SIP", payload.nodeType());
        assertEquals("dc1", payload.datacenter());
        assertEquals("us-east", payload.region());
        assertEquals(500, payload.maxSessions());
        assertNull(payload.carrierName());
        assertNull(payload.trunkGroup());
    }

    @Test
    void heartbeatSendsNodeHeartbeat() throws Exception {
        when(connectionManager.isConnected()).thenReturn(true);
        JsonNode rpcResult = om.readTree("{\"active\":12}");
        when(connectionManager.sendRequest(eq("dlg.stats_active"), any()))
                .thenReturn(CompletableFuture.completedFuture(rpcResult));

        node.register();
        reset(eventProducer);

        node.sendHeartbeat();

        ArgumentCaptor<NodeHeartbeatPayload> captor =
                ArgumentCaptor.forClass(NodeHeartbeatPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_HEARTBEATS), eq("kam-1"),
                eq(EventType.NODE_HEARTBEAT), eq("kam-1"), eq("kam-1"),
                captor.capture());

        NodeHeartbeatPayload payload = captor.getValue();
        assertEquals("kam-1", payload.nodeId());
        assertEquals("SIP", payload.nodeType());
        assertEquals(12, payload.activeSessions());
        assertEquals(500, payload.maxSessions());
        assertNotNull(payload.metrics());
        assertNotNull(payload.sessionBreakdown());
        assertEquals(12, payload.sessionBreakdown().inboundSessions());
    }

    @Test
    void heartbeatSkippedBeforeRegistration() {
        node.sendHeartbeat();

        verifyNoInteractions(eventProducer);
    }

    @Test
    void degradedHeartbeatWhenDisconnected() {
        when(connectionManager.isConnected()).thenReturn(false);

        node.register();
        reset(eventProducer);

        node.sendHeartbeat();

        ArgumentCaptor<NodeHeartbeatPayload> captor =
                ArgumentCaptor.forClass(NodeHeartbeatPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_HEARTBEATS), eq("kam-1"),
                eq(EventType.NODE_HEARTBEAT), eq("kam-1"), eq("kam-1"),
                captor.capture());

        NodeHeartbeatPayload payload = captor.getValue();
        assertEquals(0, payload.activeSessions());
    }

    @Test
    void heartbeatInvokesAlarmEvaluator() {
        when(connectionManager.isConnected()).thenReturn(false);

        node.register();
        node.sendHeartbeat();

        verify(alarmEvaluator).evaluate(
                eq("kam-1"), anyDouble(), anyDouble(), eq(0), eq(500));
    }

    @Test
    void deregisterSendsNodeDeregistered() {
        node.register();
        reset(eventProducer);

        node.deregister();

        ArgumentCaptor<NodeDeregisteredPayload> captor =
                ArgumentCaptor.forClass(NodeDeregisteredPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.INFRA_LIFECYCLE), eq("kam-1"),
                eq(EventType.NODE_DEREGISTERED), eq("kam-1"), eq("kam-1"),
                captor.capture());

        assertEquals("kam-1", captor.getValue().nodeId());
        assertEquals("shutdown", captor.getValue().reason());
    }

    @Test
    void deregisterSkippedBeforeRegistration() {
        node.deregister();

        verifyNoInteractions(eventProducer);
    }

    @Test
    void fetchActiveDialogCountParsesResult() throws Exception {
        when(connectionManager.isConnected()).thenReturn(true);
        JsonNode rpcResult = om.readTree("{\"active\":42}");
        when(connectionManager.sendRequest(eq("dlg.stats_active"), any()))
                .thenReturn(CompletableFuture.completedFuture(rpcResult));

        int count = node.fetchActiveDialogCount();
        assertEquals(42, count);
    }

    @Test
    void fetchActiveDialogCountReturnsZeroOnError() {
        when(connectionManager.isConnected()).thenReturn(true);
        CompletableFuture<JsonNode> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("timeout"));
        when(connectionManager.sendRequest(eq("dlg.stats_active"), any()))
                .thenReturn(failed);

        int count = node.fetchActiveDialogCount();
        assertEquals(0, count);
    }

    @Test
    void fetchActiveDialogCountReturnsZeroOnNullResult() {
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.sendRequest(eq("dlg.stats_active"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        int count = node.fetchActiveDialogCount();
        assertEquals(0, count);
    }

    @Test
    void initRegistersConnectedCallback() {
        node.init();

        verify(connectionManager).addOnConnectedCallback(any());
    }
}
