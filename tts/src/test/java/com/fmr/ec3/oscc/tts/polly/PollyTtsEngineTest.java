package com.fmr.ec3.oscc.tts.polly;

import com.fmr.ec3.oscc.tts.TtsException;
import com.fmr.ec3.oscc.tts.TtsRequest;
import com.fmr.ec3.oscc.tts.TtsResult;
import com.fmr.ec3.oscc.tts.cache.InMemoryTtsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class PollyTtsEngineTest {

    private PollyClient pollyClient;
    private PollyTtsConfig defaultConfig;

    @BeforeEach
    void setUp() {
        pollyClient = mock(PollyClient.class);
        defaultConfig = PollyTtsConfig.builder().build();
    }

    private void mockPollyResponse(byte[] audio) {
        SynthesizeSpeechResponse speechResponse = SynthesizeSpeechResponse.builder().build();
        ResponseBytes<SynthesizeSpeechResponse> responseBytes = ResponseBytes.fromByteArray(speechResponse, audio);
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class), any(ResponseTransformer.class)))
                .thenReturn(responseBytes);
    }

    @Test
    void synthesizesPlainText() {
        byte[] audio = new byte[16000]; // 1 second at 8kHz 16-bit
        mockPollyResponse(audio);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        TtsResult result = engine.synthesize(TtsRequest.builder("Hello").build());

        assertThat(result.getPcmAudio()).hasSize(16000);
        assertThat(result.isFromCache()).isFalse();

        ArgumentCaptor<SynthesizeSpeechRequest> captor = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(pollyClient).synthesizeSpeech(captor.capture(), any(ResponseTransformer.class));
        assertThat(captor.getValue().textTypeAsString()).isEqualTo("text");
    }

    @Test
    void synthesizesSsml() {
        mockPollyResponse(new byte[8000]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        engine.synthesize(TtsRequest.builder("<speak>Hello</speak>").build());

        ArgumentCaptor<SynthesizeSpeechRequest> captor = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(pollyClient).synthesizeSpeech(captor.capture(), any(ResponseTransformer.class));
        assertThat(captor.getValue().textTypeAsString()).isEqualTo("ssml");
    }

    @Test
    void wrapsBareSsmlFragment() {
        mockPollyResponse(new byte[8000]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        engine.synthesize(TtsRequest.builder(
                "<break time=\"300ms\"/> Thank you for calling.").build());

        ArgumentCaptor<SynthesizeSpeechRequest> captor = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(pollyClient).synthesizeSpeech(captor.capture(), any(ResponseTransformer.class));
        assertThat(captor.getValue().textTypeAsString()).isEqualTo("ssml");
        assertThat(captor.getValue().text())
                .isEqualTo("<speak><break time=\"300ms\"/> Thank you for calling.</speak>");
    }

    @Test
    void usesDefaultVoiceWhenNoneSpecified() {
        mockPollyResponse(new byte[100]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        engine.synthesize(TtsRequest.builder("Hello").build());

        ArgumentCaptor<SynthesizeSpeechRequest> captor = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(pollyClient).synthesizeSpeech(captor.capture(), any(ResponseTransformer.class));
        assertThat(captor.getValue().voiceId().toString()).isEqualTo("Joanna");
    }

    @Test
    void usesOverriddenVoice() {
        mockPollyResponse(new byte[100]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        engine.synthesize(TtsRequest.builder("Hello").voiceId("Matthew").build());

        ArgumentCaptor<SynthesizeSpeechRequest> captor = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(pollyClient).synthesizeSpeech(captor.capture(), any(ResponseTransformer.class));
        assertThat(captor.getValue().voiceId().toString()).isEqualTo("Matthew");
    }

    @Test
    void calculatesCorrectDuration() {
        // 16000 bytes = 8000 samples = 1 second at 8kHz
        mockPollyResponse(new byte[16000]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        TtsResult result = engine.synthesize(TtsRequest.builder("Hello").build());

        assertThat(result.getDurationMs()).isEqualTo(1000L);
    }

    @Test
    void cachingFlowHitsCache() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        PollyTtsConfig config = PollyTtsConfig.builder().cache(cache).build();
        mockPollyResponse(new byte[100]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, config);

        TtsRequest request = TtsRequest.builder("Hello").cacheable(true).build();

        // First call — cache miss
        TtsResult first = engine.synthesize(request);
        assertThat(first.isFromCache()).isFalse();

        // Second call — cache hit
        TtsResult second = engine.synthesize(request);
        assertThat(second.isFromCache()).isTrue();

        // Polly called only once
        verify(pollyClient, times(1)).synthesizeSpeech(
                any(SynthesizeSpeechRequest.class), any(ResponseTransformer.class));
    }

    @Test
    void noCacheWhenNotCacheable() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        PollyTtsConfig config = PollyTtsConfig.builder().cache(cache).build();
        mockPollyResponse(new byte[100]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, config);

        TtsRequest request = TtsRequest.builder("Hello").cacheable(false).build();

        engine.synthesize(request);
        engine.synthesize(request);

        // Polly called twice — caching disabled per request
        verify(pollyClient, times(2)).synthesizeSpeech(
                any(SynthesizeSpeechRequest.class), any(ResponseTransformer.class));
        assertThat(cache.size()).isZero();
    }

    @Test
    void noCacheWhenCacheNotConfigured() {
        mockPollyResponse(new byte[100]);
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        TtsRequest request = TtsRequest.builder("Hello").cacheable(true).build();

        engine.synthesize(request);
        engine.synthesize(request);

        // Polly called twice — no cache configured
        verify(pollyClient, times(2)).synthesizeSpeech(
                any(SynthesizeSpeechRequest.class), any(ResponseTransformer.class));
    }

    @Test
    void rethrowsTtsExceptionUnwrapped() {
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class), any(ResponseTransformer.class)))
                .thenThrow(new TtsException("direct failure"));
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        assertThatThrownBy(() -> engine.synthesize(TtsRequest.builder("Hello").build()))
                .isInstanceOf(TtsException.class)
                .hasMessage("direct failure")
                .hasNoCause();
    }

    @Test
    void wrapsExceptionInTtsException() {
        when(pollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class), any(ResponseTransformer.class)))
                .thenThrow(new RuntimeException("AWS error"));
        PollyTtsEngine engine = new PollyTtsEngine(pollyClient, defaultConfig);

        assertThatThrownBy(() -> engine.synthesize(TtsRequest.builder("Hello").build()))
                .isInstanceOf(TtsException.class)
                .hasMessageContaining("Polly synthesis failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
