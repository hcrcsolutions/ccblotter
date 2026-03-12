package com.fmr.ec3.oscc.ivr;

/**
 * Unchecked exception for IVR flow failures.
 */
public class IvrFlowException extends RuntimeException {

    public IvrFlowException(String message) {
        super(message);
    }

    public IvrFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
