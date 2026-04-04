package com.fmr.ec3.oscc.fsesl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "freeswitch")
public class FreeSwitchProperties {

    // Node identity
    private String nodeId = "fs-1";
    private String datacenter = "dc1";
    private String region = "us-east";
    private int maxSessions = 1000;
    private long heartbeatIntervalMs = 5000;

    // ESL connection
    private String eslHost = "localhost";
    private int eslPort = 8021;
    private String eslPassword = "ClueCon";
    private int eslConnectTimeoutMs = 5000;

    // Reconnect backoff
    private long reconnectInitialDelayMs = 1000;
    private long reconnectMaxDelayMs = 60000;
    private double reconnectMultiplier = 2.0;

    // Node type: AUTO, SIP, or MEDIA
    private String nodeType = "AUTO";

    // Detail toggle — gates the expensive "show channels as json"
    private boolean detailedBreakdownEnabled = false;

    // Alarm thresholds
    private double alarmCpuWarning = 70;
    private double alarmCpuCritical = 90;
    private double alarmMemoryWarning = 75;
    private double alarmMemoryCritical = 95;
    private double alarmSessionWarning = 80;
    private double alarmSessionCritical = 95;

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

    public String getEslHost() {
        return eslHost;
    }

    public void setEslHost(String eslHost) {
        this.eslHost = eslHost;
    }

    public int getEslPort() {
        return eslPort;
    }

    public void setEslPort(int eslPort) {
        this.eslPort = eslPort;
    }

    public String getEslPassword() {
        return eslPassword;
    }

    public void setEslPassword(String eslPassword) {
        this.eslPassword = eslPassword;
    }

    public int getEslConnectTimeoutMs() {
        return eslConnectTimeoutMs;
    }

    public void setEslConnectTimeoutMs(int eslConnectTimeoutMs) {
        this.eslConnectTimeoutMs = eslConnectTimeoutMs;
    }

    public long getReconnectInitialDelayMs() {
        return reconnectInitialDelayMs;
    }

    public void setReconnectInitialDelayMs(long reconnectInitialDelayMs) {
        this.reconnectInitialDelayMs = reconnectInitialDelayMs;
    }

    public long getReconnectMaxDelayMs() {
        return reconnectMaxDelayMs;
    }

    public void setReconnectMaxDelayMs(long reconnectMaxDelayMs) {
        this.reconnectMaxDelayMs = reconnectMaxDelayMs;
    }

    public double getReconnectMultiplier() {
        return reconnectMultiplier;
    }

    public void setReconnectMultiplier(double reconnectMultiplier) {
        this.reconnectMultiplier = reconnectMultiplier;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public boolean isDetailedBreakdownEnabled() {
        return detailedBreakdownEnabled;
    }

    public void setDetailedBreakdownEnabled(boolean detailedBreakdownEnabled) {
        this.detailedBreakdownEnabled = detailedBreakdownEnabled;
    }

    public double getAlarmCpuWarning() {
        return alarmCpuWarning;
    }

    public void setAlarmCpuWarning(double alarmCpuWarning) {
        this.alarmCpuWarning = alarmCpuWarning;
    }

    public double getAlarmCpuCritical() {
        return alarmCpuCritical;
    }

    public void setAlarmCpuCritical(double alarmCpuCritical) {
        this.alarmCpuCritical = alarmCpuCritical;
    }

    public double getAlarmMemoryWarning() {
        return alarmMemoryWarning;
    }

    public void setAlarmMemoryWarning(double alarmMemoryWarning) {
        this.alarmMemoryWarning = alarmMemoryWarning;
    }

    public double getAlarmMemoryCritical() {
        return alarmMemoryCritical;
    }

    public void setAlarmMemoryCritical(double alarmMemoryCritical) {
        this.alarmMemoryCritical = alarmMemoryCritical;
    }

    public double getAlarmSessionWarning() {
        return alarmSessionWarning;
    }

    public void setAlarmSessionWarning(double alarmSessionWarning) {
        this.alarmSessionWarning = alarmSessionWarning;
    }

    public double getAlarmSessionCritical() {
        return alarmSessionCritical;
    }

    public void setAlarmSessionCritical(double alarmSessionCritical) {
        this.alarmSessionCritical = alarmSessionCritical;
    }
}
