package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadRequestTest {

    @Test
    void buildsWithRequiredFields() {
        byte[] data = {1, 2, 3};
        UploadRequest request = UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .data(data)
                .build();

        assertThat(request.getSessionId()).isEqualTo("session-1");
        assertThat(request.getAudioId()).isEqualTo("utt-1");
        assertThat(request.getData()).isEqualTo(data);
        assertThat(request.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void customContentType() {
        UploadRequest request = UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .data(new byte[]{1})
                .contentType("audio/wav")
                .build();

        assertThat(request.getContentType()).isEqualTo("audio/wav");
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .audioId("utt-1")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsNullAudioId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("session-1")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioId");
    }

    @Test
    void rejectsNullData() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data is required");
    }

    @Test
    void rejectsEmptyData() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .data(new byte[0])
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data is required");
    }

    @Test
    void rejectsInvalidSessionId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("../evil")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsInvalidAudioId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt/bad")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsBlankSessionId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("  ")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsBlankAudioId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("session-1")
                .audioId("")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioId");
    }

    @Test
    void rejectsDotDotSessionId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("..")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDotDotAudioId() {
        assertThatThrownBy(() -> UploadRequest.builder()
                .sessionId("session-1")
                .audioId("..")
                .data(new byte[]{1})
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dataIsNotDefensivelyCopied() {
        byte[] data = {1, 2, 3};
        UploadRequest request = UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .data(data)
                .build();

        assertThat(request.getData()).isSameAs(data);
    }

    @Test
    void acceptsLargeData() {
        byte[] data = new byte[1024 * 1024];
        UploadRequest request = UploadRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .data(data)
                .build();

        assertThat(request.getData()).hasSize(1024 * 1024);
    }

    @Test
    void acceptsSessionIdWithDotsAndDashes() {
        UploadRequest request = UploadRequest.builder()
                .sessionId("session-1.0")
                .audioId("audio_file-2.wav")
                .data(new byte[]{1})
                .build();

        assertThat(request.getSessionId()).isEqualTo("session-1.0");
        assertThat(request.getAudioId()).isEqualTo("audio_file-2.wav");
    }
}
