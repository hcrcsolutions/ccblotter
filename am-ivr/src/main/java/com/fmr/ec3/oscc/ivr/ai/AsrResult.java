package com.fmr.ec3.oscc.ivr.ai;

/**
 * Immutable result of automatic speech recognition.
 */
public class AsrResult {

    private final String transcript;
    private final double confidence;

    public AsrResult(String transcript, double confidence) {
        this.transcript = transcript;
        this.confidence = confidence;
    }

    public String getTranscript() {
        return transcript;
    }

    public double getConfidence() {
        return confidence;
    }
}
