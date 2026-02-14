package com.fmr.ec3.oscc.sender.model;

import java.util.List;

public class SimulatedAgent {

    public enum State { ONLINE, ON_CALL, AWAY, UNAVAILABLE }

    private final String id;
    private final String name;
    private final List<String> skills;
    private State state;
    private String currentCallId;
    private long stateChangedAtMs;

    public SimulatedAgent(String id, String name, List<String> skills, State state) {
        this.id = id;
        this.name = name;
        this.skills = skills;
        this.state = state;
        this.stateChangedAtMs = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getSkills() {
        return skills;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        this.stateChangedAtMs = System.currentTimeMillis();
    }

    public String getCurrentCallId() {
        return currentCallId;
    }

    public void setCurrentCallId(String currentCallId) {
        this.currentCallId = currentCallId;
    }

    public long getStateChangedAtMs() {
        return stateChangedAtMs;
    }
}
