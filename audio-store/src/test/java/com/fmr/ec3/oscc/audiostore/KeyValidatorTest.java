package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyValidatorTest {

    @Test
    void acceptsValidSegment() {
        assertThatCode(() -> KeyValidator.validate("hello-world_1.0", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAlphanumeric() {
        assertThatCode(() -> KeyValidator.validate("abc123", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> KeyValidator.validate(null, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field is required");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> KeyValidator.validate("  ", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field is required");
    }

    @Test
    void rejectsSlash() {
        assertThatThrownBy(() -> KeyValidator.validate("a/b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsDotDot() {
        assertThatThrownBy(() -> KeyValidator.validate("..", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be '.' or '..'");
    }

    @Test
    void rejectsBackslash() {
        assertThatThrownBy(() -> KeyValidator.validate("a\\b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsSpaces() {
        assertThatThrownBy(() -> KeyValidator.validate("a b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsOverMaxLength() {
        String longString = "a".repeat(257);
        assertThatThrownBy(() -> KeyValidator.validate(longString, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 256");
    }

    @Test
    void acceptsExactlyMaxLength() {
        String maxString = "a".repeat(256);
        assertThatCode(() -> KeyValidator.validate(maxString, "field"))
                .doesNotThrowAnyException();
    }
}
