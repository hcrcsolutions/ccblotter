package com.fmr.ec3.oscc.receiver;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import com.fmr.ec3.oscc.receiver.state.RedisKeySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects agents stuck ON_CALL for longer than the configured threshold
 * with no recent events. Logs WARNING + publishes metric.
 * Does NOT auto-fix by default.
 * Uses Redis-based leader election so only one pod runs the check.
 */
public class OrphanedStateWatchdog {

    private static final Logger log = LoggerFactory.getLogger(OrphanedStateWatchdog.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(35);

    private final StringRedisTemplate redisTemplate;
    private final int thresholdMinutes;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicLong orphanedAgentCount = new AtomicLong(0);

    public OrphanedStateWatchdog(StringRedisTemplate redisTemplate,
                                  ReceiverProperties props) {
        this.redisTemplate = redisTemplate;
        this.thresholdMinutes = props.getOrphanThresholdMinutes();
    }

    @Scheduled(fixedDelayString = "${receiver.orphan-check-interval-ms:30000}", initialDelay = 60000)
    public void checkOrphanedAgents() {
        // Leader election: only one pod should run the watchdog
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(RedisKeySchema.WATCHDOG_LOCK, instanceId, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        Set<String> onCallAgentIds = redisTemplate.opsForSet()
            .members(RedisKeySchema.agentsByState("ON_CALL"));

        if (onCallAgentIds == null || onCallAgentIds.isEmpty()) {
            orphanedAgentCount.set(0);
            return;
        }

        Instant threshold = Instant.now().minus(Duration.ofMinutes(thresholdMinutes));
        long orphaned = 0;

        // Pipeline all HGET calls in a single round-trip (12.5K → 1 RT at peak)
        List<String> agentIdList = new ArrayList<>(onCallAgentIds);
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String agentId : agentIdList) {
                    operations.opsForHash().get(RedisKeySchema.agentKey(agentId), "stateChangedAt");
                }
                return null;
            }
        });

        for (int i = 0; i < agentIdList.size(); i++) {
            Object stateChangedAt = results.get(i);
            if (stateChangedAt != null) {
                try {
                    Instant changedAt = Instant.parse(stateChangedAt.toString());
                    if (changedAt.isBefore(threshold)) {
                        orphaned++;
                        log.warn("Agent {} has been ON_CALL since {} (>{} min) - possible orphaned state",
                            agentIdList.get(i), changedAt, thresholdMinutes);
                    }
                } catch (Exception e) {
                    log.debug("Could not parse stateChangedAt for agent {}: {}",
                        agentIdList.get(i), e.getMessage());
                }
            }
        }

        orphanedAgentCount.set(orphaned);
        if (orphaned > 0) {
            log.warn("Orphaned state watchdog: {} agents stuck ON_CALL > {} minutes", orphaned, thresholdMinutes);
        }
    }

    public long getOrphanedAgentCount() {
        return orphanedAgentCount.get();
    }
}
