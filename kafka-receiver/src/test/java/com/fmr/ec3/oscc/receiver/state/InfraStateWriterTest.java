package com.fmr.ec3.oscc.receiver.state;

import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfraStateWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private StreamOperations<String, Object, Object> streamOps;
    @Mock private SetOperations<String, String> setOps;

    private InfraStateWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        writer = new InfraStateWriter(redisTemplate);
    }

    private NodeHeartbeatPayload heartbeatPayload() {
        var metrics = new NodeMetricsDto(50.0, 60.0, 10, 2, 0.01, 0.1, 42);
        var breakdown = new SessionBreakdownDto(70, 30, 10, 15, 65, 10);
        return new NodeHeartbeatPayload("sip-1", "SIP", 250, 500, metrics, breakdown, null, null);
    }

    @Test
    void writeHeartbeatUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        writer.writeHeartbeat(heartbeatPayload());

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void writeHeartbeatAddsTrendAndTrims() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        writer.writeHeartbeat(heartbeatPayload());

        verify(streamOps).add(any());
        verify(streamOps).trim("infra:node:sip-1:trends", 60, true);
    }

    @Test
    void updateTopologyVersionWritesTimestamp() {
        writer.updateTopologyVersion();
        verify(valueOps).set(eq("infra:topology:version"), anyString());
    }

    @Test
    void registerNodeUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        var payload = new NodeRegisteredPayload(
            "sip-1", "SIP", "sip-01.dc1.example.com", "10.1.1.10",
            "dc1", "us-east", 500, null, null);

        writer.registerNode(payload);

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void removeNodeStateUsesLuaScript() {
        writer.removeNodeState("sip-1");

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(
                "infra:node:sip-1:info",
                "infra:node:sip-1:heartbeat",
                "infra:node:sip-1:metrics",
                "infra:node:sip-1:sessions",
                "infra:node:sip-1:trends",
                "infra:node:sip-1:alarm",
                "infra:node:sip-1:gateways",
                "infra:node:sip-1:codecs",
                "infra:nodes:all"
            )),
            eq("sip-1"),
            eq("infra:nodes:by-type:")
        );
    }

    @Test
    void writeHeartbeatSkipsNullMetrics() {
        var payload = new NodeHeartbeatPayload("sip-1", "SIP", 250, 500, null,
            new SessionBreakdownDto(70, 30, 10, 15, 65, 10), null, null);

        writer.writeHeartbeat(payload);

        verify(redisTemplate, never()).execute(any(SessionCallback.class));
    }

    @Test
    void writeHeartbeatSkipsNullSessionBreakdown() {
        var payload = new NodeHeartbeatPayload("sip-1", "SIP", 250, 500,
            new NodeMetricsDto(50.0, 60.0, 10, 2, 0.01, 0.1, 42), null, null, null);

        writer.writeHeartbeat(payload);

        verify(redisTemplate, never()).execute(any(SessionCallback.class));
    }

    @Test
    void writeAlarmPersistsToRedis() {
        var payload = new NodeAlarmPayload("sip-2", "CPU_HIGH", "WARNING", 80.0, 92.5);

        writer.writeAlarm(payload);

        verify(hashOps).putAll(eq("infra:node:sip-2:alarm"), anyMap());
    }

    @Test
    void removeNodeStateHandlesMissingNodeType() {
        // Lua script handles missing nodeType internally — just verify it runs
        writer.removeNodeState("sip-1");

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }
}
