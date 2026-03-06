package com.fmr.ec3.oscc.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTypeDetectorTest {

    @Test
    void detectsSpeakTag() {
        assertThat(TextTypeDetector.detect("<speak>Hello</speak>")).isEqualTo(TextType.SSML);
    }

    @Test
    void detectsSpeakTagWithAttributes() {
        assertThat(TextTypeDetector.detect("<speak lang=\"en-US\">Hello</speak>"))
                .isEqualTo(TextType.SSML);
    }

    @Test
    void detectsSpeakTagWithLeadingWhitespace() {
        assertThat(TextTypeDetector.detect("  \n <speak>Hello</speak>")).isEqualTo(TextType.SSML);
    }

    @Test
    void detectsPlainText() {
        assertThat(TextTypeDetector.detect("Hello world")).isEqualTo(TextType.TEXT);
    }

    @Test
    void detectsNull() {
        assertThat(TextTypeDetector.detect(null)).isEqualTo(TextType.TEXT);
    }

    @Test
    void detectsBlank() {
        assertThat(TextTypeDetector.detect("   ")).isEqualTo(TextType.TEXT);
    }

    @Test
    void doesNotDetectPartialSpeakTag() {
        assertThat(TextTypeDetector.detect("<speaker>Hello</speaker>")).isEqualTo(TextType.TEXT);
    }

    @Test
    void detectsBareBreakTag() {
        assertThat(TextTypeDetector.detect("<break/> Hello")).isEqualTo(TextType.SSML);
    }

    @Test
    void detectsBareProsodyTag() {
        assertThat(TextTypeDetector.detect("<prosody rate=\"slow\">Hello</prosody>"))
                .isEqualTo(TextType.SSML);
    }

    @Test
    void detectsBareEmphasisTag() {
        assertThat(TextTypeDetector.detect("<emphasis level=\"strong\">Hello</emphasis>"))
                .isEqualTo(TextType.SSML);
    }

    @Test
    void detectsBareSayAsTag() {
        assertThat(TextTypeDetector.detect("Call <say-as interpret-as=\"telephone\">555-1234</say-as>"))
                .isEqualTo(TextType.SSML);
    }

    @Test
    void detectsBareAmazonTag() {
        assertThat(TextTypeDetector.detect("<amazon:effect name=\"whispered\">Hello</amazon:effect>"))
                .isEqualTo(TextType.SSML);
    }

    @Test
    void detectsEmbeddedSsmlInMiddle() {
        assertThat(TextTypeDetector.detect(
                "Thank you <break time=\"300ms\"/> for calling.")).isEqualTo(TextType.SSML);
    }

    // wrapIfNeeded tests

    @Test
    void wrapIfNeededWrapsBareSsml() {
        String input = "<break/> Thank you for calling.";
        assertThat(TextTypeDetector.wrapIfNeeded(input))
                .isEqualTo("<speak><break/> Thank you for calling.</speak>");
    }

    @Test
    void wrapIfNeededPassesThroughAlreadyWrappedSsml() {
        String input = "<speak>Hello</speak>";
        assertThat(TextTypeDetector.wrapIfNeeded(input)).isEqualTo(input);
    }

    @Test
    void wrapIfNeededPassesThroughPlainText() {
        String input = "Hello world";
        assertThat(TextTypeDetector.wrapIfNeeded(input)).isEqualTo(input);
    }

    @Test
    void wrapIfNeededPassesThroughNull() {
        assertThat(TextTypeDetector.wrapIfNeeded(null)).isNull();
    }

    @Test
    void wrapIfNeededPassesThroughBlank() {
        assertThat(TextTypeDetector.wrapIfNeeded("   ")).isEqualTo("   ");
    }

    @Test
    void wrapIfNeededPassesThroughSpeakWithAttributes() {
        String input = "<speak lang=\"en-US\">Hello</speak>";
        assertThat(TextTypeDetector.wrapIfNeeded(input)).isEqualTo(input);
    }

    @Test
    void wrapIfNeededWrapsEmbeddedSsml() {
        String input = "Thank you <break time=\"300ms\"/> for calling.";
        assertThat(TextTypeDetector.wrapIfNeeded(input))
                .isEqualTo("<speak>Thank you <break time=\"300ms\"/> for calling.</speak>");
    }
}
