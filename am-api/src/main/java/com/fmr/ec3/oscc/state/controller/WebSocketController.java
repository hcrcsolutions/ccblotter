package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.model.SystemStatus;
import com.fmr.ec3.oscc.state.service.RedisHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket controller for real-time updates.
 *
 * Handles:
 * - Client connection/disconnection
 * - Initial state push on connection
 * - Heartbeat requests
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final RedisHealthService healthService;
    private final SimpMessagingTemplate messagingTemplate;

    // Track connected sessions
    private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    /**
     * Handle client connection.
     * Broadcasts current state to the new client.
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        activeSessions.put(sessionId, System.currentTimeMillis());
        int count = connectionCount.incrementAndGet();
        log.info("WebSocket client connected: {} (total: {})", sessionId, count);

        // Send initial state to all clients (including the new one)
        broadcastAll();
    }

    /**
     * Handle client disconnection.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        activeSessions.remove(sessionId);
        int count = connectionCount.decrementAndGet();
        log.info("WebSocket client disconnected: {} (total: {})", sessionId, count);
    }

    /**
     * Handle heartbeat/ping requests from clients.
     */
    @MessageMapping("/ping")
    @SendToUser("/queue/pong")
    public String handlePing() {
        return "pong";
    }

    /**
     * Handle refresh request from clients.
     * Forces a full state broadcast.
     */
    @MessageMapping("/refresh")
    public void handleRefresh() {
        log.debug("Refresh requested");
        broadcastAll();
    }

    /**
     * Broadcast all state to connected clients.
     */
    private void broadcastAll() {
        try {
            // First check system health
            SystemStatus status = healthService.checkAndGetStatus();
            messagingTemplate.convertAndSend("/topic/system", status);

            if (!status.isRedisConnected()) {
                log.warn("Redis unavailable - not broadcasting data");
            }
        } catch (Exception e) {
            log.error("Error during broadcast", e);
            // Broadcast error status
            SystemStatus errorStatus = SystemStatus.builder()
                    .redisConnected(false)
                    .errorMessage(e.getMessage())
                    .build();
            messagingTemplate.convertAndSend("/topic/system", errorStatus);
        }
    }

    /**
     * Get the number of connected clients.
     */
    public int getConnectionCount() {
        return connectionCount.get();
    }
}
