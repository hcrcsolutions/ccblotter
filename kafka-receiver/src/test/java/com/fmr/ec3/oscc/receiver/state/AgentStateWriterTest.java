package com.fmr.ec3.oscc.receiver.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentStateWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private SetOperations<String, String> setOps;

    private AgentStateWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        writer = new AgentStateWriter(redisTemplate);
    }

    @Test
    void saveAgentUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        writer.saveAgent("AGT-0001", "John Smith", "ONLINE", null);

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateAgentStateReturnsTrueWhenAgentExists() {
        doReturn(1L).when(redisTemplate).execute(
            any(RedisScript.class), anyList(),
            any(), any(), any(), any(), any()
        );

        boolean result = writer.updateAgentState("AGT-0001", "ON_CALL", "call-123");

        assertTrue(result);
        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("agent:AGT-0001", "agents:by-state:ON_CALL")),
            eq("agents:by-state:"),
            eq("ON_CALL"),
            anyString(),
            eq("call-123"),
            eq("AGT-0001")
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateAgentStatePassesEmptyStringForNullCallId() {
        doReturn(1L).when(redisTemplate).execute(
            any(RedisScript.class), anyList(),
            any(), any(), any(), any(), any()
        );

        writer.updateAgentState("AGT-0001", "ONLINE", null);

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("agent:AGT-0001", "agents:by-state:ONLINE")),
            eq("agents:by-state:"),
            eq("ONLINE"),
            anyString(),
            eq(""),
            eq("AGT-0001")
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateAgentStateReturnsFalseWhenAgentMissing() {
        doReturn(0L).when(redisTemplate).execute(
            any(RedisScript.class), anyList(),
            any(), any(), any(), any(), any()
        );

        boolean result = writer.updateAgentState("AGT-9999", "ONLINE", null);

        assertFalse(result);
    }

    @Test
    void updateLastCallInfoWritesAllFieldsAsSingleHmset() {
        when(redisTemplate.hasKey("agent:AGT-0001")).thenReturn(true);
        Instant start = Instant.parse("2024-01-01T10:00:00Z");
        Instant end = Instant.parse("2024-01-01T10:05:00Z");
        writer.updateLastCallInfo("AGT-0001", "(212) 555-0100", start, end, 300);

        verify(hashOps).putAll(eq("agent:AGT-0001"), anyMap());
    }

    @Test
    void updateLastCallInfoSkipsDeletedAgent() {
        when(redisTemplate.hasKey("agent:AGT-0001")).thenReturn(false);
        Instant start = Instant.parse("2024-01-01T10:00:00Z");
        Instant end = Instant.parse("2024-01-01T10:05:00Z");
        writer.updateLastCallInfo("AGT-0001", "(212) 555-0100", start, end, 300);

        verify(hashOps, never()).putAll(anyString(), anyMap());
    }

    @SuppressWarnings("unchecked")
    @Test
    void removeAgentUsesLuaScript() {
        doReturn(1L).when(redisTemplate).execute(
            any(RedisScript.class), anyList(),
            any(), any()
        );

        writer.removeAgent("AGT-0001");

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of("agent:AGT-0001", "agents:all")),
            eq("agents:by-state:"),
            eq("AGT-0001")
        );
    }

    @Test
    void getAgentStateAndCallIdReturnsBothFields() {
        when(hashOps.multiGet("agent:AGT-0001", List.of("state", "currentCallId")))
            .thenReturn(List.of("ON_CALL", "call-123"));

        List<String> result = writer.getAgentStateAndCallId("AGT-0001");

        assertEquals("ON_CALL", result.get(0));
        assertEquals("call-123", result.get(1));
    }

    @Test
    void getAgentStateAndCallIdReturnsNullsWhenMissing() {
        when(hashOps.multiGet("agent:AGT-9999", List.of("state", "currentCallId")))
            .thenReturn(java.util.Arrays.asList(null, null));

        List<String> result = writer.getAgentStateAndCallId("AGT-9999");

        assertNull(result.get(0));
        assertNull(result.get(1));
    }

    @Test
    void agentExistsChecksRedisKey() {
        when(redisTemplate.hasKey("agent:AGT-0001")).thenReturn(true);
        assertTrue(writer.agentExists("AGT-0001"));

        when(redisTemplate.hasKey("agent:AGT-9999")).thenReturn(false);
        assertFalse(writer.agentExists("AGT-9999"));
    }

    @Test
    void getAgentStateReturnsValue() {
        when(hashOps.get("agent:AGT-0001", "state")).thenReturn("ON_CALL");
        assertEquals("ON_CALL", writer.getAgentState("AGT-0001"));
    }

    @Test
    void getAgentStateReturnsNullWhenMissing() {
        when(hashOps.get("agent:AGT-9999", "state")).thenReturn(null);
        assertNull(writer.getAgentState("AGT-9999"));
    }
}
