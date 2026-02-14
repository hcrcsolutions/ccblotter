package com.fmr.ec3.oscc.mediaserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "media-server")
public class MediaServerProperties {

    private String nodeId = "media-1";
    private String datacenter = "dc1";
    private String region = "us-east";
    private int maxSessions = 100;
    private long heartbeatIntervalMs = 5000;
    private long sessionSimIntervalMs = 4000;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getSessionSimIntervalMs() {
        return sessionSimIntervalMs;
    }

    public void setSessionSimIntervalMs(long sessionSimIntervalMs) {
        this.sessionSimIntervalMs = sessionSimIntervalMs;
    }
}
