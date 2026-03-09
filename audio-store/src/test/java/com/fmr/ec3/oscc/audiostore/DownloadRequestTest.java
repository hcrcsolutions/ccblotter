package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownloadRequestTest {

    @Test
    void buildsWithRequiredFields() {
        DownloadRequest request = DownloadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .build();

        assertThat(request.getSessionId()).isEqualTo("session-1");
        assertThat(request.getAudioId()).isEqualTo("utt-1");
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsNullAudioId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("session-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioId");
    }

    @Test
    void rejectsInvalidAudioId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("session-1")
                .audioId("utt/bad")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }
}
