package com.fmr.ec3.oscc.sipserver.model;

import java.util.concurrent.ConcurrentHashMap;

public class Interaction {

    private final String id;
    private final Direction direction;
    private final long createdAtMs;
    private final ConcurrentHashMap<String, Dialog> dialogs = new ConcurrentHashMap<>();

    /**
     * Current leg B role. Updated atomically via InteractionStore.compute().
     * Volatile so heartbeat reads see the latest value without locking.
     */
    private volatile DialogRole activeLegBRole;

    public Interaction(String id, Direction direction, DialogRole initialLegBRole) {
        if (!initialLegBRole.isLegB()) {
            throw new IllegalArgumentException(
                    "Initial leg B role must be a leg B state, got: " + initialLegBRole);
        }
        this.id = id;
        this.direction = direction;
        this.activeLegBRole = initialLegBRole;
        this.createdAtMs = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public Direction getDirection() {
        return direction;
    }

    public DialogRole getActiveLegBRole() {
        return activeLegBRole;
    }

    public void setActiveLegBRole(DialogRole role) {
        this.activeLegBRole = role;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public ConcurrentHashMap<String, Dialog> getDialogs() {
        return dialogs;
    }

    public void addDialog(Dialog dialog) {
        dialogs.put(dialog.getId(), dialog);
    }

    public Dialog removeDialog(String dialogId) {
        return dialogs.remove(dialogId);
    }
}
