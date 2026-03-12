package com.fmr.ec3.oscc.ivr.ai;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable result of natural language understanding intent classification.
 */
public class NluResult {

    private final String intent;
    private final double confidence;
    private final Map<String, String> entities;

    public NluResult(String intent, double confidence, Map<String, String> entities) {
        this.intent = intent;
        this.confidence = confidence;
        this.entities = entities == null
                ? Collections.emptyMap()
                : Map.copyOf(entities);
    }

    public String getIntent() {
        return intent;
    }

    public double getConfidence() {
        return confidence;
    }

    public Map<String, String> getEntities() {
        return entities;
    }
}
