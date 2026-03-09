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

    @Test
    void rejectsSingleDot() {
        assertThatThrownBy(() -> KeyValidator.validate(".", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be '.' or '..'");
    }

    @Test
    void rejectsEmptyString() {
        assertThatThrownBy(() -> KeyValidator.validate("", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field is required");
    }

    @Test
    void rejectsSpecialChars() {
        assertThatThrownBy(() -> KeyValidator.validate("a@b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsHashChar() {
        assertThatThrownBy(() -> KeyValidator.validate("a#b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsPercent() {
        assertThatThrownBy(() -> KeyValidator.validate("a%b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsTab() {
        assertThatThrownBy(() -> KeyValidator.validate("a\tb", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsNewline() {
        assertThatThrownBy(() -> KeyValidator.validate("a\nb", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsUnicode() {
        assertThatThrownBy(() -> KeyValidator.validate("abc\u00e9", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsLeadingSlash() {
        assertThatThrownBy(() -> KeyValidator.validate("/abc", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsTrailingSlash() {
        assertThatThrownBy(() -> KeyValidator.validate("abc/", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void acceptsSingleChar() {
        assertThatCode(() -> KeyValidator.validate("a", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsSingleDigit() {
        assertThatCode(() -> KeyValidator.validate("1", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsDashOnly() {
        assertThatCode(() -> KeyValidator.validate("-", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsUnderscoreOnly() {
        assertThatCode(() -> KeyValidator.validate("_", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsDotInMiddle() {
        assertThatCode(() -> KeyValidator.validate("file.wav", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMultipleDots() {
        assertThatCode(() -> KeyValidator.validate("file.name.wav", "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void accepts255Chars() {
        String s = "a".repeat(255);
        assertThatCode(() -> KeyValidator.validate(s, "field"))
                .doesNotThrowAnyException();
    }

    @Test
    void includesFieldNameInNullMessage() {
        assertThatThrownBy(() -> KeyValidator.validate(null, "sessionId"))
                .hasMessageContaining("sessionId is required");
    }

    @Test
    void includesFieldNameInLengthMessage() {
        String longString = "a".repeat(257);
        assertThatThrownBy(() -> KeyValidator.validate(longString, "audioId"))
                .hasMessageContaining("audioId must be at most 256");
    }

    @Test
    void includesFieldNameInInvalidCharsMessage() {
        assertThatThrownBy(() -> KeyValidator.validate("bad value", "audioId"))
                .hasMessageContaining("audioId contains invalid characters");
    }

    @Test
    void rejectsAsterisk() {
        assertThatThrownBy(() -> KeyValidator.validate("a*b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsQuestionMark() {
        assertThatThrownBy(() -> KeyValidator.validate("a?b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsColon() {
        assertThatThrownBy(() -> KeyValidator.validate("a:b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsEqualSign() {
        assertThatThrownBy(() -> KeyValidator.validate("a=b", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }
}
