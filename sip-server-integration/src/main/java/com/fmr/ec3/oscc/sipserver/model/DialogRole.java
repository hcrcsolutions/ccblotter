package com.fmr.ec3.oscc.sipserver.model;

public enum DialogRole {

    /** Leg A — the caller side. Stable for the interaction lifetime. */
    CALLER,

    /** Leg B — connected to IVR system. */
    IVR,

    /** Leg B — waiting in queue (music on hold). */
    QUEUE,

    /** Leg B — connected to a live agent. */
    AGENT,

    /** Leg B — agent-initiated hold. */
    ON_HOLD,

    /** Leg C — recording or transcription. */
    RECORDING;

    /**
     * Returns true if this role represents a leg B state
     * (the only roles that affect SessionBreakdown counts).
     */
    public boolean isLegB() {
        return this == IVR || this == QUEUE || this == AGENT || this == ON_HOLD;
    }
}
