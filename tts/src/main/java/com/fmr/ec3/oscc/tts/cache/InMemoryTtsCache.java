package com.fmr.ec3.oscc.tts.cache;

import com.fmr.ec3.oscc.tts.TtsResult;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Bounded in-memory LRU cache for TTS results. Thread-safe via lock-free
 * {@link ConcurrentHashMap} and {@link ConcurrentLinkedDeque}.
 */
public class InMemoryTtsCache implements TtsCache {

    private static final int DEFAULT_MAX_ENTRIES = 100;

    private final ConcurrentHashMap<CacheKey, TtsResult> map = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<CacheKey> accessOrder = new ConcurrentLinkedDeque<>();
    private final int maxEntries;

    public InMemoryTtsCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryTtsCache(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public Optional<TtsResult> get(CacheKey key) {
        TtsResult result = map.get(key);
        if (result == null) {
            return Optional.empty();
        }
        accessOrder.remove(key);
        accessOrder.addLast(key);
        return Optional.of(new TtsResult(result.getPcmAudio(), result.getDurationMs(), true));
    }

    @Override
    public void put(CacheKey key, TtsResult result) {
        if (map.put(key, result) != null) {
            accessOrder.remove(key);
        }
        accessOrder.addLast(key);
        while (map.size() > maxEntries) {
            CacheKey eldest = accessOrder.pollFirst();
            if (eldest == null) {
                break;
            }
            map.remove(eldest);
        }
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
        accessOrder.clear();
    }
}
