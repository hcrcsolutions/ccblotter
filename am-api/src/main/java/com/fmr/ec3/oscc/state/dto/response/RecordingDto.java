package com.fmr.ec3.oscc.state.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingDto {

    private String filename;
    private long sizeBytes;
    private double durationSeconds;
    private int sampleRate;
    private int channels;
    private int bitsPerSample;
    private Instant uploadedAt;
    private String url;
}
