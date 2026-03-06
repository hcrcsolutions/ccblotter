package com.example.agentmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tts")
public class TtsProperties {

    private String defaultVoiceId = "Joanna";
    private String engine = "neural";
    private int sampleRate = 8000;
    private boolean cacheEnabled = true;
    private int cacheMaxEntries = 100;
    private String awsRegion = "us-east-1";

    public String getDefaultVoiceId() {
        return defaultVoiceId;
    }

    public void setDefaultVoiceId(String defaultVoiceId) {
        this.defaultVoiceId = defaultVoiceId;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }
}
