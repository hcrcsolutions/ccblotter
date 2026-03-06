package com.fmr.ec3.oscc.sipserver.model;

public class Dialog {

    private final String id;
    private final DialogRole role;
    private final long createdAtMs;

    public Dialog(String id, DialogRole role) {
        this.id = id;
        this.role = role;
        this.createdAtMs = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public DialogRole getRole() {
        return role;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }
}
