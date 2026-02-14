package com.fmr.ec3.oscc.sipserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sip-server")
public class SipServerProperties {

    private String nodeId = "sip-1";
    private String datacenter = "dc1";
    private String region = "us-east";
    private int maxSessions = 500;
    private long heartbeatIntervalMs = 5000;
    private long callSimIntervalMs = 3000;

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

    public long getCallSimIntervalMs() {
        return callSimIntervalMs;
    }

    public void setCallSimIntervalMs(long callSimIntervalMs) {
        this.callSimIntervalMs = callSimIntervalMs;
    }
}
