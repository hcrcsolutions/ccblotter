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
class QueueStateWriterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private SetOperations<String, String> setOps;

    private QueueStateWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        writer = new QueueStateWriter(redisTemplate);
    }

    @Test
    void addToQueueUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        Instant queuedAt = Instant.parse("2024-01-01T10:00:00Z");
        writer.addToQueue("call-1", "(212) 555-0100", "Sales", 2, queuedAt);

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void removeFromQueueUsesTransaction() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(null);

        writer.removeFromQueue("call-1");

        verify(redisTemplate).execute(any(SessionCallback.class));
    }

    @Test
    void queuedCallExistsChecksRedisKey() {
        when(redisTemplate.hasKey("queue:call:call-1")).thenReturn(true);
        assertTrue(writer.queuedCallExists("call-1"));

        when(redisTemplate.hasKey("queue:call:unknown")).thenReturn(false);
        assertFalse(writer.queuedCallExists("unknown"));
    }
}
