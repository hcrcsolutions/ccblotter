package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.model.SystemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for monitoring Redis health and broadcasting status.
 *
 * Implements fail-closed behavior: if Redis is unavailable,
 * the system broadcasts an error status and refuses to serve data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisHealthService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    private volatile String lastError = null;

    /**
     * Check Redis health every 5 seconds.
     * Broadcasts system status when health state changes.
     */
    @Scheduled(fixedRate = 5000)
    public void checkHealth() {
        boolean wasHealthy = isHealthy.get();
        boolean nowHealthy = performHealthCheck();

        if (wasHealthy != nowHealthy) {
            log.info("Redis health changed: {} -> {}", wasHealthy, nowHealthy);
            broadcastStatus();
        }
    }

    private boolean performHealthCheck() {
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            connection.close();

            if ("PONG".equals(pong)) {
                isHealthy.set(true);
                lastError = null;
                return true;
            } else {
                isHealthy.set(false);
                lastError = "Unexpected ping response: " + pong;
                return false;
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            isHealthy.set(false);
            lastError = e.getMessage();
            return false;
        }
    }

    /**
     * Get current system status.
     */
    public SystemStatus getStatus() {
        return SystemStatus.builder()
                .redisConnected(isHealthy.get())
                .lastUpdated(Instant.now())
                .errorMessage(lastError)
                .build();
    }

    /**
     * Check if Redis is currently healthy.
     * Services should check this before attempting operations.
     */
    public boolean isRedisHealthy() {
        return isHealthy.get();
    }

    /**
     * Broadcast current system status to all connected clients.
     */
    public void broadcastStatus() {
        SystemStatus status = getStatus();
        log.debug("Broadcasting system status: {}", status);
        messagingTemplate.convertAndSend("/topic/system", status);
    }

    /**
     * Force a health check and return the result.
     * Used during initial connection.
     */
    public SystemStatus checkAndGetStatus() {
        performHealthCheck();
        return getStatus();
    }
}
