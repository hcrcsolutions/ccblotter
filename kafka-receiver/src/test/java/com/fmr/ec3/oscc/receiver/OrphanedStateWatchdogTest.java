package com.fmr.ec3.oscc.receiver;

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
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrphanedStateWatchdogTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, String> valueOps;

    private OrphanedStateWatchdog watchdog;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ReceiverProperties props = new ReceiverProperties();
        props.setOrphanThresholdMinutes(60);
        watchdog = new OrphanedStateWatchdog(redisTemplate, props);
    }

    private void acquireLock() {
        when(valueOps.setIfAbsent(eq("receiver:watchdog:lock"), anyString(), eq(Duration.ofSeconds(35))))
            .thenReturn(true);
    }

    @Test
    void skipsCheckWhenLockNotAcquired() {
        when(valueOps.setIfAbsent(eq("receiver:watchdog:lock"), anyString(), eq(Duration.ofSeconds(35))))
            .thenReturn(false);

        watchdog.checkOrphanedAgents();

        verify(setOps, never()).members(anyString());
    }

    @Test
    void noOrphansWhenNoOnCallAgents() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of());

        watchdog.checkOrphanedAgents();

        assertEquals(0, watchdog.getOrphanedAgentCount());
    }

    @Test
    void noOrphansWhenNullMembers() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(null);

        watchdog.checkOrphanedAgents();

        assertEquals(0, watchdog.getOrphanedAgentCount());
    }

    @Test
    void detectsOrphanedAgent() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of("AGT-0001"));
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(twoHoursAgo.toString()));

        watchdog.checkOrphanedAgents();

        assertEquals(1, watchdog.getOrphanedAgentCount());
    }

    @Test
    void doesNotFlagRecentOnCallAgent() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of("AGT-0001"));
        Instant fiveMinAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(fiveMinAgo.toString()));

        watchdog.checkOrphanedAgents();

        assertEquals(0, watchdog.getOrphanedAgentCount());
    }

    @Test
    void countsMixedAgentsCorrectly() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of("AGT-0001", "AGT-0002", "AGT-0003"));

        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant fiveMinAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS);

        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(twoHoursAgo.toString(), fiveMinAgo.toString(), threeHoursAgo.toString()));

        watchdog.checkOrphanedAgents();

        assertEquals(2, watchdog.getOrphanedAgentCount());
    }

    @Test
    void handlesNullStateChangedAt() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of("AGT-0001"));
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(java.util.Collections.singletonList(null));

        watchdog.checkOrphanedAgents();

        assertEquals(0, watchdog.getOrphanedAgentCount());
    }

    @Test
    void handlesMalformedTimestamp() {
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of("AGT-0001"));
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of("not-a-timestamp"));

        watchdog.checkOrphanedAgents();

        assertEquals(0, watchdog.getOrphanedAgentCount());
    }

    @Test
    void countResetsOnSubsequentRuns() {
        // First run: 1 orphan
        acquireLock();
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of("AGT-0001"));
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
            .thenReturn(List.of(twoHoursAgo.toString()));

        watchdog.checkOrphanedAgents();
        assertEquals(1, watchdog.getOrphanedAgentCount());

        // Second run: no orphans
        when(setOps.members("agents:by-state:ON_CALL")).thenReturn(Set.of());

        watchdog.checkOrphanedAgents();
        assertEquals(0, watchdog.getOrphanedAgentCount());
    }
}
