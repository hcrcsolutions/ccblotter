package com.fmr.ec3.oscc.tts.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Cache key derived from SHA-256 hash of synthesis parameters.
 */
public final class CacheKey {

    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    });

    private final String hash;

    private CacheKey(String hash) {
        this.hash = hash;
    }

    /**
     * Creates a cache key from the given synthesis parameters.
     * Uses length-prefixed fields to prevent delimiter collision.
     */
    public static CacheKey of(String text, String voiceId, String engine) {
        String input = text.length() + ":" + text + "|"
                + voiceId.length() + ":" + voiceId + "|"
                + engine.length() + ":" + engine;
        byte[] hashBytes = SHA256.get().digest(input.getBytes(StandardCharsets.UTF_8));
        return new CacheKey(HexFormat.of().formatHex(hashBytes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CacheKey cacheKey = (CacheKey) o;
        return Objects.equals(hash, cacheKey.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public String toString() {
        return hash;
    }
}
