package com.fmr.ec3.oscc.audiostore;

import java.util.regex.Pattern;

/**
 * Validates S3 key segments to prevent path traversal and injection attacks.
 */
public final class KeyValidator {

    private static final Pattern VALID_KEY_SEGMENT = Pattern.compile("[a-zA-Z0-9._-]+");
    private static final int MAX_LENGTH = 256;

    private KeyValidator() {
    }

    public static void validate(String segment, String fieldName) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (segment.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be at most " + MAX_LENGTH + " characters");
        }
        if (!VALID_KEY_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters: " + segment);
        }
        if (".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException(fieldName + " must not be '.' or '..'");
        }
    }
}
