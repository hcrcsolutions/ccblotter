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

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallStateWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private SetOperations<String, String> setOps;

    private CallStateWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        writer = new CallStateWriter(redisTemplate);
    }

    @Test
    void saveCallUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        Instant start = Instant.parse("2024-01-01T10:00:00Z");
        writer.saveCall("call-1", "(212) 555-0100", "AGT-0001", "John Smith", start, "TALKING");

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void removeCallUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        writer.removeCall("call-1");

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void updateCallStateWritesToHash() {
        writer.updateCallState("call-1", "ON_HOLD");
        verify(hashOps).put("call:call-1", "state", "ON_HOLD");
    }

    @Test
    void callExistsChecksRedisKey() {
        when(redisTemplate.hasKey("call:call-1")).thenReturn(true);
        assertTrue(writer.callExists("call-1"));

        when(redisTemplate.hasKey("call:unknown")).thenReturn(false);
        assertFalse(writer.callExists("unknown"));
    }

    @Test
    void getCallStartTimeParsesProperly() {
        String timeStr = "2024-01-01T10:00:00Z";
        when(hashOps.get("call:call-1", "startTime")).thenReturn(timeStr);

        Instant result = writer.getCallStartTime("call-1");
        assertEquals(Instant.parse(timeStr), result);
    }

    @Test
    void getCallStartTimeReturnsNullWhenMissing() {
        when(hashOps.get("call:unknown", "startTime")).thenReturn(null);
        assertNull(writer.getCallStartTime("unknown"));
    }
}
