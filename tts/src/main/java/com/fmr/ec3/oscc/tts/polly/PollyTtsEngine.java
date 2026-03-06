package com.fmr.ec3.oscc.tts.polly;

import com.fmr.ec3.oscc.tts.TextType;
import com.fmr.ec3.oscc.tts.TextTypeDetector;
import com.fmr.ec3.oscc.tts.TtsEngine;
import com.fmr.ec3.oscc.tts.TtsException;
import com.fmr.ec3.oscc.tts.TtsRequest;
import com.fmr.ec3.oscc.tts.TtsResult;
import com.fmr.ec3.oscc.tts.cache.CacheKey;
import com.fmr.ec3.oscc.tts.cache.TtsCache;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;

import java.util.Optional;

/**
 * TTS engine backed by Amazon Polly. Produces 8kHz 16-bit signed PCM mono audio.
 */
@Slf4j
public class PollyTtsEngine implements TtsEngine {

    private final PollyClient pollyClient;
    private final PollyTtsConfig config;

    public PollyTtsEngine(PollyClient pollyClient, PollyTtsConfig config) {
        this.pollyClient = pollyClient;
        this.config = config;
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        String voiceId = request.getVoiceId() != null ? request.getVoiceId() : config.getDefaultVoiceId();
        TtsCache cache = config.getCache();

        if (request.isCacheable() && cache != null) {
            CacheKey key = CacheKey.of(request.getText(), voiceId, config.getEngine());
            Optional<TtsResult> cached = cache.get(key);
            if (cached.isPresent()) {
                log.debug("Cache hit for voice={}", voiceId);
                return cached.get();
            }
            TtsResult result = callPolly(request.getText(), voiceId);
            cache.put(key, result);
            return result;
        }

        return callPolly(request.getText(), voiceId);
    }

    private TtsResult callPolly(String text, String voiceId) {
        try {
            String prepared = TextTypeDetector.wrapIfNeeded(text);
            TextType detectedType = TextTypeDetector.detect(prepared);
            String pollyTextType = detectedType == TextType.SSML ? "ssml" : "text";

            SynthesizeSpeechRequest pollyRequest = SynthesizeSpeechRequest.builder()
                    .text(prepared)
                    .voiceId(voiceId)
                    .engine(Engine.fromValue(config.getEngine()))
                    .outputFormat(OutputFormat.PCM)
                    .sampleRate(String.valueOf(config.getSampleRate()))
                    .textType(pollyTextType)
                    .build();

            ResponseBytes<SynthesizeSpeechResponse> response = pollyClient.synthesizeSpeech(
                    pollyRequest, ResponseTransformer.toBytes());

            byte[] pcmAudio = response.asByteArray();
            long durationMs = (pcmAudio.length / 2) * 1000L / config.getSampleRate();

            log.debug("Synthesized {} bytes, {}ms, voice={}", pcmAudio.length, durationMs, voiceId);
            return new TtsResult(pcmAudio, durationMs, false);
        } catch (TtsException e) {
            throw e;
        } catch (Exception e) {
            throw new TtsException("Polly synthesis failed", e);
        }
    }
}
