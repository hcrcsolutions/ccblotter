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

    @Test
    void rejectsBlankSessionId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsBlankAudioId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("session-1")
                .audioId("  ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioId");
    }

    @Test
    void rejectsInvalidSessionId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("sess/bad")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsDotDotSessionId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("..")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDotDotAudioId() {
        assertThatThrownBy(() -> DownloadRequest.builder()
                .sessionId("session-1")
                .audioId("..")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsIdsWithDotsAndDashes() {
        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1.0")
                .audioId("file_name-2.wav")
                .build();

        assertThat(request.getSessionId()).isEqualTo("sess-1.0");
        assertThat(request.getAudioId()).isEqualTo("file_name-2.wav");
    }
}
