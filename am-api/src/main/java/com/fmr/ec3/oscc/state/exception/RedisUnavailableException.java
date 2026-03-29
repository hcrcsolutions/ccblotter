package com.fmr.ec3.oscc.state.exception;

/**
 * Exception thrown when Redis is unavailable.
 * This is a catastrophic failure - the system cannot function without Redis.
 */
public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(String message) {
        super(message);
    }

    public RedisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
