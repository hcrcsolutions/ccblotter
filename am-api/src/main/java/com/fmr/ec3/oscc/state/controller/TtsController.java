package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.request.SynthesizeRequest;
import com.fmr.ec3.oscc.tts.TtsEngine;
import com.fmr.ec3.oscc.tts.TtsRequest;
import com.fmr.ec3.oscc.tts.TtsResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final int WAV_HEADER_SIZE = 44;
    private static final short BITS_PER_SAMPLE = 16;
    private static final short NUM_CHANNELS = 1;
    private static final int SAMPLE_RATE = 8000;

    private static final List<Map<String, String>> VOICES = List.of(
            Map.of("id", "Joanna", "name", "Joanna", "language", "en-US", "gender", "Female"),
            Map.of("id", "Matthew", "name", "Matthew", "language", "en-US", "gender", "Male"),
            Map.of("id", "Ivy", "name", "Ivy", "language", "en-US", "gender", "Female (child)"),
            Map.of("id", "Kendra", "name", "Kendra", "language", "en-US", "gender", "Female"),
            Map.of("id", "Kimberly", "name", "Kimberly", "language", "en-US", "gender", "Female"),
            Map.of("id", "Salli", "name", "Salli", "language", "en-US", "gender", "Female"),
            Map.of("id", "Joey", "name", "Joey", "language", "en-US", "gender", "Male"),
            Map.of("id", "Stephen", "name", "Stephen", "language", "en-US", "gender", "Male"),
            Map.of("id", "Ruth", "name", "Ruth", "language", "en-US", "gender", "Female"),
            Map.of("id", "Amy", "name", "Amy", "language", "en-GB", "gender", "Female"),
            Map.of("id", "Brian", "name", "Brian", "language", "en-GB", "gender", "Male")
    );

    private final TtsEngine ttsEngine;

    public TtsController(TtsEngine ttsEngine) {
        this.ttsEngine = ttsEngine;
    }

    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesize(@RequestBody SynthesizeRequest request) {
        TtsRequest ttsRequest = TtsRequest.builder(request.getText())
                .voiceId(request.getVoiceId())
                .cacheable(request.isCacheable())
                .build();

        TtsResult result = ttsEngine.synthesize(ttsRequest);
        byte[] wav = wrapInWavHeader(result.getPcmAudio());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.setContentLength(wav.length);

        return ResponseEntity.ok().headers(headers).body(wav);
    }

    @GetMapping("/voices")
    public List<Map<String, String>> listVoices() {
        return VOICES;
    }

    private byte[] wrapInWavHeader(byte[] pcmData) {
        int dataSize = pcmData.length;
        int byteRate = SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8;
        short blockAlign = (short) (NUM_CHANNELS * BITS_PER_SAMPLE / 8);
        byte[] wav = new byte[WAV_HEADER_SIZE + dataSize];

        writeString(wav, 0, "RIFF");
        writeInt(wav, 4, 36 + dataSize);
        writeString(wav, 8, "WAVE");
        writeString(wav, 12, "fmt ");
        writeInt(wav, 16, 16);
        writeShort(wav, 20, (short) 1);
        writeShort(wav, 22, NUM_CHANNELS);
        writeInt(wav, 24, SAMPLE_RATE);
        writeInt(wav, 28, byteRate);
        writeShort(wav, 32, blockAlign);
        writeShort(wav, 34, BITS_PER_SAMPLE);
        writeString(wav, 36, "data");
        writeInt(wav, 40, dataSize);

        System.arraycopy(pcmData, 0, wav, WAV_HEADER_SIZE, dataSize);
        return wav;
    }

    private void writeString(byte[] buf, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            buf[offset + i] = (byte) value.charAt(i);
        }
    }

    private void writeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private void writeShort(byte[] buf, int offset, short value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
