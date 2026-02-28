package com.fmr.ec3.oscc.audiocapture;

/**
 * Unchecked exception for audio capture failures.
 */
public class CaptureException extends RuntimeException {

    public CaptureException(String message) {
        super(message);
    }

    public CaptureException(String message, Throwable cause) {
        super(message, cause);
    }
}
