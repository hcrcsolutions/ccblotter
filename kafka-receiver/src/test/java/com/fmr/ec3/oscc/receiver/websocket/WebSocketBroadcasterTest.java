package com.fmr.ec3.oscc.receiver.websocket;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketBroadcasterTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;
    @Mock private HashOperations<String, Object, Object> hashOps;

    private WebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);

        ReceiverProperties props = new ReceiverProperties();
        props.setWebsocketThrottleMs(500);
        // Synchronous executor for deterministic tests
        broadcaster = new WebSocketBroadcaster(messagingTemplate, redisTemplate,
            props, Runnable::run);
    }

    @Test
    void broadcastAgentsUsesPipelineAndSendsToTopic() {
        when(setOps.members("agents:all")).thenReturn(Set.of("AGT-0001"));
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(Map.of("id", "AGT-0001", "state", "ONLINE")));

        broadcaster.broadcastAgents();

        verify(redisTemplate).executePipelined(any(SessionCallback.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/agents"), anyList());
    }

    @Test
    void broadcastAgentsThrottles() {
        when(setOps.members("agents:all")).thenReturn(Set.of());

        broadcaster.broadcastAgents();
        broadcaster.broadcastAgents(); // Should be throttled

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/agents"), anyList());
    }

    @Test
    void differentTopicsAreThrottledIndependently() {
        when(setOps.members(anyString())).thenReturn(Set.of());

        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();  // Different topic, should NOT be throttled

        verify(messagingTemplate).convertAndSend(eq("/topic/agents"), anyList());
        verify(messagingTemplate).convertAndSend(eq("/topic/calls"), anyList());
    }

    @Test
    void broadcastSummaryUsesPipelineForCounts() {
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(50L, 200L, 30L, 20L));

        broadcaster.broadcastSummary();

        verify(redisTemplate).executePipelined(any(SessionCallback.class));

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/summary"), captor.capture());

        Map<String, Object> summary = captor.getValue();
        assertEquals(50, summary.get("online"));
        assertEquals(200, summary.get("onCall"));
        assertEquals(30, summary.get("away"));
        assertEquals(20, summary.get("unavailable"));
        assertEquals(300, summary.get("total"));
    }

    @Test
    void broadcastQueueIncludesStats() {
        when(setOps.members("queue:calls")).thenReturn(Set.of("q-1"));
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(Map.of("id", "q-1")));

        broadcaster.broadcastQueue();

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/queue"), captor.capture());

        Map<String, Object> payload = captor.getValue();
        assertNotNull(payload.get("calls"));
        assertNotNull(payload.get("stats"));
    }

    @Test
    void broadcastInfrastructureFetchesInfoMetricsSessions() {
        when(setOps.members("infra:nodes:all")).thenReturn(Set.of("sip-1"));
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(
                Map.of("nodeId", "sip-1", "nodeType", "SIP"),  // info
                Map.of("cpu", "45.0", "memory", "60.0"),        // metrics
                Map.of("active", "120", "inbound", "80")        // sessions
            ));

        broadcaster.broadcastInfrastructure();

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/infrastructure"), captor.capture());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = captor.getValue();
        assertEquals(1, nodes.size());
        assertEquals("sip-1", nodes.get(0).get("nodeId"));
        assertNotNull(nodes.get(0).get("metrics"));
        assertNotNull(nodes.get(0).get("sessions"));
    }

    @Test
    void broadcastInfrastructureSendsEmptyListWhenNoNodes() {
        when(setOps.members("infra:nodes:all")).thenReturn(Set.of());

        broadcaster.broadcastInfrastructure();

        verify(messagingTemplate).convertAndSend(eq("/topic/infrastructure"), anyList());
    }

    @Test
    void broadcastAgentsHandlesNullMembers() {
        when(setOps.members("agents:all")).thenReturn(null);

        broadcaster.broadcastAgents();

        verify(messagingTemplate).convertAndSend(eq("/topic/agents"), anyList());
    }

    @Test
    void broadcastAgentsSkipsEmptyHashes() {
        when(setOps.members("agents:all")).thenReturn(Set.of("AGT-0001", "AGT-0002"));
        // Pipeline returns two maps: one with data, one empty (deleted between SMEMBERS and HGETALL)
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(Map.of("id", "AGT-0001"), Map.of()));

        broadcaster.broadcastAgents();

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/agents"), captor.capture());
        assertEquals(1, captor.getValue().size());
    }
}
