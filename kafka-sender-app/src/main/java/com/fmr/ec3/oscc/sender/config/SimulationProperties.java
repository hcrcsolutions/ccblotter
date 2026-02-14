package com.fmr.ec3.oscc.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    private int agentCount = 400;
    private int loginPercent = 95;
    private long tickIntervalMs = 2000;
    private long heartbeatIntervalMs = 5000;
    private int sipServerCount = 4;
    private int mediaServerCount = 120;

    public int getAgentCount() {
        return agentCount;
    }

    public void setAgentCount(int agentCount) {
        this.agentCount = agentCount;
    }

    public int getLoginPercent() {
        return loginPercent;
    }

    public void setLoginPercent(int loginPercent) {
        this.loginPercent = loginPercent;
    }

    public long getTickIntervalMs() {
        return tickIntervalMs;
    }

    public void setTickIntervalMs(long tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getSipServerCount() {
        return sipServerCount;
    }

    public void setSipServerCount(int sipServerCount) {
        this.sipServerCount = sipServerCount;
    }

    public int getMediaServerCount() {
        return mediaServerCount;
    }

    public void setMediaServerCount(int mediaServerCount) {
        this.mediaServerCount = mediaServerCount;
    }
}
