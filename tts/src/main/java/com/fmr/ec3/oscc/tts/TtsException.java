package com.fmr.ec3.oscc.tts;

/**
 * Unchecked exception for text-to-speech failures.
 */
public class TtsException extends RuntimeException {

    public TtsException(String message) {
        super(message);
    }

    public TtsException(String message, Throwable cause) {
        super(message, cause);
    }
}
