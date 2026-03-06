package com.fmr.ec3.oscc.tts.cache;

import com.fmr.ec3.oscc.tts.TtsResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTtsCacheTest {

    @Test
    void putAndGet() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");
        TtsResult result = new TtsResult(new byte[]{1, 2}, 100L, false);

        cache.put(key, result);
        Optional<TtsResult> cached = cache.get(key);

        assertThat(cached).isPresent();
        assertThat(cached.get().getPcmAudio()).isEqualTo(new byte[]{1, 2});
        assertThat(cached.get().getDurationMs()).isEqualTo(100L);
    }

    @Test
    void getMissingReturnsEmpty() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void hitReturnsTrueForFromCache() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        cache.put(key, new TtsResult(new byte[]{1}, 50L, false));
        TtsResult cached = cache.get(key).orElseThrow();

        assertThat(cached.isFromCache()).isTrue();
    }

    @Test
    void lruEviction() {
        InMemoryTtsCache cache = new InMemoryTtsCache(2);

        CacheKey key1 = CacheKey.of("one", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("two", "Joanna", "neural");
        CacheKey key3 = CacheKey.of("three", "Joanna", "neural");

        cache.put(key1, new TtsResult(new byte[]{1}, 10L, false));
        cache.put(key2, new TtsResult(new byte[]{2}, 20L, false));
        cache.put(key3, new TtsResult(new byte[]{3}, 30L, false));

        assertThat(cache.get(key1)).isEmpty();
        assertThat(cache.get(key2)).isPresent();
        assertThat(cache.get(key3)).isPresent();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void lruAccessOrderPreservesRecentlyUsed() {
        InMemoryTtsCache cache = new InMemoryTtsCache(2);

        CacheKey key1 = CacheKey.of("one", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("two", "Joanna", "neural");
        CacheKey key3 = CacheKey.of("three", "Joanna", "neural");

        cache.put(key1, new TtsResult(new byte[]{1}, 10L, false));
        cache.put(key2, new TtsResult(new byte[]{2}, 20L, false));
        // Access key1 to make it recently used
        cache.get(key1);
        // Insert key3 — should evict key2 (least recently used)
        cache.put(key3, new TtsResult(new byte[]{3}, 30L, false));

        assertThat(cache.get(key1)).isPresent();
        assertThat(cache.get(key2)).isEmpty();
        assertThat(cache.get(key3)).isPresent();
    }

    @Test
    void putOverwritesExistingEntry() {
        InMemoryTtsCache cache = new InMemoryTtsCache(2);

        CacheKey key1 = CacheKey.of("one", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("two", "Joanna", "neural");
        CacheKey key3 = CacheKey.of("three", "Joanna", "neural");

        cache.put(key1, new TtsResult(new byte[]{1}, 10L, false));
        cache.put(key2, new TtsResult(new byte[]{2}, 20L, false));
        // Overwrite key1 — should promote it to most-recently-used
        cache.put(key1, new TtsResult(new byte[]{10}, 100L, false));
        // Insert key3 — should evict key2 (least recently used), not key1
        cache.put(key3, new TtsResult(new byte[]{3}, 30L, false));

        assertThat(cache.get(key1)).isPresent();
        assertThat(cache.get(key1).get().getPcmAudio()).isEqualTo(new byte[]{10});
        assertThat(cache.get(key2)).isEmpty();
        assertThat(cache.get(key3)).isPresent();
    }

    @Test
    void clearRemovesAll() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        cache.put(key, new TtsResult(new byte[]{1}, 10L, false));
        cache.clear();

        assertThat(cache.size()).isZero();
        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void invalidMaxEntriesThrows() {
        assertThatThrownBy(() -> new InMemoryTtsCache(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEntries");
    }

    @Test
    void concurrentAccess() throws InterruptedException {
        InMemoryTtsCache cache = new InMemoryTtsCache(50);
        int threadCount = 10;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        CacheKey key = CacheKey.of("text-" + threadId + "-" + i, "Joanna", "neural");
                        cache.put(key, new TtsResult(new byte[]{(byte) i}, i, false));
                        cache.get(key);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertThat(errors.get()).isZero();
    }
}
