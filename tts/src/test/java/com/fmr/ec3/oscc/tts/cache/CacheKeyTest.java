package com.fmr.ec3.oscc.tts.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyTest {

    @Test
    void deterministic() {
        CacheKey key1 = CacheKey.of("hello", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("hello", "Joanna", "neural");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    void differentTextProducesDifferentKey() {
        CacheKey key1 = CacheKey.of("hello", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("world", "Joanna", "neural");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void differentVoiceProducesDifferentKey() {
        CacheKey key1 = CacheKey.of("hello", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("hello", "Matthew", "neural");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void differentEngineProducesDifferentKey() {
        CacheKey key1 = CacheKey.of("hello", "Joanna", "neural");
        CacheKey key2 = CacheKey.of("hello", "Joanna", "standard");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void delimiterCollisionPrevented() {
        // "a|b" + "c" vs "a" + "b|c" should produce different keys
        CacheKey key1 = CacheKey.of("a|b", "c", "neural");
        CacheKey key2 = CacheKey.of("a", "b|c", "neural");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void equalsSameReference() {
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        assertThat(key.equals(key)).isTrue();
    }

    @Test
    void equalsNull() {
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        assertThat(key.equals(null)).isFalse();
    }

    @Test
    void equalsDifferentType() {
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        assertThat(key.equals("not a cache key")).isFalse();
    }

    @Test
    void toStringReturnsHash() {
        CacheKey key = CacheKey.of("hello", "Joanna", "neural");

        assertThat(key.toString()).matches("[0-9a-f]{64}");
    }
}
