package com.fmr.ec3.oscc.tts.cache;

import com.fmr.ec3.oscc.tts.TtsResult;

import java.util.Optional;

/**
 * Cache for text-to-speech results.
 */
public interface TtsCache {

    Optional<TtsResult> get(CacheKey key);

    void put(CacheKey key, TtsResult result);
}
