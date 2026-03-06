package com.fmr.ec3.oscc.tts;

/**
 * Vendor-neutral interface for text-to-speech synthesis.
 */
public interface TtsEngine {

    TtsResult synthesize(TtsRequest request);
}
