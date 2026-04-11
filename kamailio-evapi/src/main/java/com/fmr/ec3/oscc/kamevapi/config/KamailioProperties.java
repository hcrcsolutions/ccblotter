package com.fmr.ec3.oscc.kamevapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kamailio")
public class KamailioProperties {

    private String serverId = "kam-1";
    private String evApiHost = "localhost";
    private int evApiPort = 8448;
    private int connectTimeoutMs = 5000;
    private long reconnectInitialDelayMs = 1000;
    private long reconnectMaxDelayMs = 60000;
    private double reconnectMultiplier = 2.0;
    private boolean stateSyncEnabled = true;
    private long stateSyncIntervalMs = 30000;
    private long stateSyncRpcTimeoutMs = 5000;
    private String rpcMethodUsrloc = "ul.dump";
    private String rpcMethodPresence = "presence.list";
    private String rpcMethodDialog = "dlg.list";
    private String datacenter = "dc1";
    private String region = "us-east";
    private int maxSessions = 500;
    private long heartbeatIntervalMs = 5000;
    private double alarmCpuWarning = 70;
    private double alarmCpuCritical = 90;
    private double alarmMemoryWarning = 75;
    private double alarmMemoryCritical = 95;
    private double alarmSessionWarning = 80;
    private double alarmSessionCritical = 95;

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getEvApiHost() {
        return evApiHost;
    }

    public void setEvApiHost(String evApiHost) {
        this.evApiHost = evApiHost;
    }

    public int getEvApiPort() {
        return evApiPort;
    }

    public void setEvApiPort(int evApiPort) {
        this.evApiPort = evApiPort;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
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

    public boolean isStateSyncEnabled() {
        return stateSyncEnabled;
    }

    public void setStateSyncEnabled(boolean stateSyncEnabled) {
        this.stateSyncEnabled = stateSyncEnabled;
    }

    public long getStateSyncIntervalMs() {
        return stateSyncIntervalMs;
    }

    public void setStateSyncIntervalMs(long stateSyncIntervalMs) {
        this.stateSyncIntervalMs = stateSyncIntervalMs;
    }

    public long getStateSyncRpcTimeoutMs() {
        return stateSyncRpcTimeoutMs;
    }

    public void setStateSyncRpcTimeoutMs(long stateSyncRpcTimeoutMs) {
        this.stateSyncRpcTimeoutMs = stateSyncRpcTimeoutMs;
    }

    public String getRpcMethodUsrloc() {
        return rpcMethodUsrloc;
    }

    public void setRpcMethodUsrloc(String rpcMethodUsrloc) {
        this.rpcMethodUsrloc = rpcMethodUsrloc;
    }

    public String getRpcMethodPresence() {
        return rpcMethodPresence;
    }

    public void setRpcMethodPresence(String rpcMethodPresence) {
        this.rpcMethodPresence = rpcMethodPresence;
    }

    public String getRpcMethodDialog() {
        return rpcMethodDialog;
    }

    public void setRpcMethodDialog(String rpcMethodDialog) {
        this.rpcMethodDialog = rpcMethodDialog;
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
