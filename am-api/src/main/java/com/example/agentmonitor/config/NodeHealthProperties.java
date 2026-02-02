package com.example.agentmonitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.node")
public class NodeHealthProperties {

    /**
     * How often nodes should send heartbeat (seconds).
     */
    private int heartbeatIntervalSeconds = 30;

    /**
     * Mark node as UNHEALTHY if no heartbeat received within this time (seconds).
     */
    private int unhealthyThresholdSeconds = 60;

    /**
     * Remove node from topology if no heartbeat received within this time (seconds).
     */
    private int removalThresholdSeconds = 300;

    /**
     * How often the health watchdog runs (seconds).
     */
    private int watchdogIntervalSeconds = 15;

    /**
     * Whether to automatically remove dead nodes.
     */
    private boolean autoRemoveEnabled = false;
}
