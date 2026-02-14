package com.fmr.ec3.oscc.receiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "receiver")
public class ReceiverProperties {

    private long websocketThrottleMs = 500;
    private long orphanCheckIntervalMs = 30000;
    private int orphanThresholdMinutes = 60;
    private int dedupCacheSize = 10000;

    public long getWebsocketThrottleMs() { return websocketThrottleMs; }
    public void setWebsocketThrottleMs(long websocketThrottleMs) { this.websocketThrottleMs = websocketThrottleMs; }
    public long getOrphanCheckIntervalMs() { return orphanCheckIntervalMs; }
    public void setOrphanCheckIntervalMs(long orphanCheckIntervalMs) { this.orphanCheckIntervalMs = orphanCheckIntervalMs; }
    public int getOrphanThresholdMinutes() { return orphanThresholdMinutes; }
    public void setOrphanThresholdMinutes(int orphanThresholdMinutes) { this.orphanThresholdMinutes = orphanThresholdMinutes; }
    public int getDedupCacheSize() { return dedupCacheSize; }
    public void setDedupCacheSize(int dedupCacheSize) { this.dedupCacheSize = dedupCacheSize; }
}
