package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KamailioStateSyncTest {

    private EvApiConnectionManager connectionManager;
    private EventProducer eventProducer;
    private KamailioProperties props;
    private KamailioStateSync stateSync;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        connectionManager = mock(EvApiConnectionManager.class);
        eventProducer = mock(EventProducer.class);
        props = new KamailioProperties();
        props.setServerId("kam-1");
        props.setStateSyncRpcTimeoutMs(1000);
        stateSync = new KamailioStateSync(connectionManager, eventProducer, props);
    }

    @Test
    void syncUsrlocEmitsLoggedInPerAor() throws Exception {
        ArrayNode result = om.createArrayNode();
        result.addObject().put("aor", "sip:agent1001@sip.example.com");
        result.addObject().put("aor", "sip:agent1002@sip.example.com");

        when(connectionManager.sendRequest(eq("ul.dump"), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        // Stub other RPCs to avoid NPE
        when(connectionManager.sendRequest(eq("presence.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(om.createArrayNode()));
        when(connectionManager.sendRequest(eq("dlg.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(om.createArrayNode()));

        stateSync.syncUsrloc();

        verify(eventProducer).send(eq(KafkaTopics.SIP_EVENTS), eq("agent1001"),
                eq(EventType.AGENT_LOGGED_IN), eq("kam-1"), eq("agent1001"), any());
        verify(eventProducer).send(eq(KafkaTopics.SIP_EVENTS), eq("agent1002"),
                eq(EventType.AGENT_LOGGED_IN), eq("kam-1"), eq("agent1002"), any());
    }

    @Test
    void syncPresenceEmitsBreakEvents() throws Exception {
        ArrayNode result = om.createArrayNode();
        result.addObject()
                .put("aor", "sip:agent1001@sip.example.com")
                .put("status", "away");
        result.addObject()
                .put("aor", "sip:agent1002@sip.example.com")
                .put("status", "available");

        when(connectionManager.sendRequest(eq("presence.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        stateSync.syncPresence();

        verify(eventProducer).send(eq(KafkaTopics.SIP_EVENTS), eq("agent1001"),
                eq(EventType.AGENT_BREAK_STARTED), eq("kam-1"), eq("agent1001"), any());
        verify(eventProducer).send(eq(KafkaTopics.SIP_EVENTS), eq("agent1002"),
                eq(EventType.AGENT_BREAK_ENDED), eq("kam-1"), eq("agent1002"), any());
    }

    @Test
    void syncDialogsEmitsCallRouted() throws Exception {
        ArrayNode result = om.createArrayNode();
        result.addObject()
                .put("callee", "sip:agent1001@sip.example.com")
                .put("callid", "abc-123")
                .put("caller", "sip:+15551234567@carrier.com");

        when(connectionManager.sendRequest(eq("dlg.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        stateSync.syncDialogs();

        verify(eventProducer).send(eq(KafkaTopics.SIP_EVENTS), eq("agent1001"),
                eq(EventType.CALL_ROUTED_TO_AGENT), eq("kam-1"), eq("agent1001"), any());
    }

    @Test
    void syncAllCallsAllThreeSubsystems() throws Exception {
        ArrayNode empty = om.createArrayNode();
        when(connectionManager.sendRequest(eq("ul.dump"), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));
        when(connectionManager.sendRequest(eq("presence.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));
        when(connectionManager.sendRequest(eq("dlg.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));

        stateSync.syncAll();

        verify(connectionManager).sendRequest(eq("ul.dump"), any());
        verify(connectionManager).sendRequest(eq("presence.list"), any());
        verify(connectionManager).sendRequest(eq("dlg.list"), any());
    }

    @Test
    void rpcTimeoutDoesNotPropagateException() {
        CompletableFuture<JsonNode> slow = new CompletableFuture<>();
        // Never completes — will time out
        when(connectionManager.sendRequest(eq("ul.dump"), any()))
                .thenReturn(slow);

        // Should not throw
        stateSync.syncUsrloc();

        verifyNoInteractions(eventProducer);
    }

    @Test
    void emptyResultArrayEmitsNoEvents() throws Exception {
        ArrayNode empty = om.createArrayNode();
        when(connectionManager.sendRequest(eq("ul.dump"), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));
        when(connectionManager.sendRequest(eq("presence.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));
        when(connectionManager.sendRequest(eq("dlg.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(empty));

        stateSync.syncAll();

        verifyNoInteractions(eventProducer);
    }

    @Test
    void nullResultHandledGracefully() throws Exception {
        when(connectionManager.sendRequest(eq("ul.dump"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(connectionManager.sendRequest(eq("presence.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(connectionManager.sendRequest(eq("dlg.list"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Should not throw
        stateSync.syncAll();

        verifyNoInteractions(eventProducer);
    }
}
