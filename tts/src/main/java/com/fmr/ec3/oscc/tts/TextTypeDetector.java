package com.fmr.ec3.oscc.tts;

import java.util.regex.Pattern;

/**
 * Detects whether input text is SSML or plain text, and auto-wraps bare SSML
 * fragments in {@code <speak>} tags.
 */
public final class TextTypeDetector {

    private static final Pattern SSML_TAG_PATTERN = Pattern.compile(
            "<(break|prosody|emphasis|phoneme|say-as|sub|voice|lang|mark)[ />]|<amazon:");

    private TextTypeDetector() {
    }

    /**
     * Detects the text type. Returns {@link TextType#SSML} if the input is wrapped
     * in {@code <speak>} tags or contains bare SSML element tags.
     */
    public static TextType detect(String text) {
        if (text == null || text.isBlank()) {
            return TextType.TEXT;
        }
        String stripped = text.strip();
        if (stripped.startsWith("<speak>") || stripped.startsWith("<speak ")) {
            return TextType.SSML;
        }
        if (SSML_TAG_PATTERN.matcher(stripped).find()) {
            return TextType.SSML;
        }
        return TextType.TEXT;
    }

    /**
     * Returns the text ready for a TTS engine. Bare SSML fragments are wrapped
     * in {@code <speak>} tags. Already-wrapped SSML and plain text pass through unchanged.
     */
    public static String wrapIfNeeded(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (detect(text) != TextType.SSML) {
            return text;
        }
        String stripped = text.strip();
        if (stripped.startsWith("<speak>") || stripped.startsWith("<speak ")) {
            return text;
        }
        return "<speak>" + text + "</speak>";
    }
}
