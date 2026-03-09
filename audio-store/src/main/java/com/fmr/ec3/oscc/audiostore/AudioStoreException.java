package com.fmr.ec3.oscc.audiostore;

/**
 * Unchecked exception for audio store failures.
 */
public class AudioStoreException extends RuntimeException {

    public AudioStoreException(String message) {
        super(message);
    }

    public AudioStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
