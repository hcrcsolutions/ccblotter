package com.fmr.ec3.oscc.ivr.ai;

/**
 * Interface for automatic speech recognition engines.
 */
public interface AsrEngine {

    /**
     * Transcribes audio data to text.
     *
     * @param audioData base64-encoded audio data
     * @param language  BCP-47 language tag (e.g. "en-US")
     * @return the transcription result with confidence score
     */
    AsrResult transcribe(String audioData, String language);
}
