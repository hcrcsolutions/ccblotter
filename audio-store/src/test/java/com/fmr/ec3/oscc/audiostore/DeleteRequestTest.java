package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeleteRequestTest {

    @Test
    void buildsWithRequiredFields() {
        DeleteRequest request = DeleteRequest.builder()
                .sessionId("session-1")
                .audioId("utt-1")
                .build();

        assertThat(request.getSessionId()).isEqualTo("session-1");
        assertThat(request.getAudioId()).isEqualTo("utt-1");
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsNullAudioId() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .sessionId("session-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioId");
    }

    @Test
    void rejectsBlankSessionId() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .sessionId("  ")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsBlankAudioId() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .sessionId("session-1")
                .audioId("")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioId");
    }

    @Test
    void rejectsInvalidSessionId() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .sessionId("../evil")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsInvalidAudioId() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .sessionId("session-1")
                .audioId("utt/bad")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsDotDotIds() {
        assertThatThrownBy(() -> DeleteRequest.builder()
                .sessionId("..")
                .audioId("utt-1")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsIdsWithDotsAndDashes() {
        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1.0")
                .audioId("file_name-2.wav")
                .build();

        assertThat(request.getSessionId()).isEqualTo("sess-1.0");
        assertThat(request.getAudioId()).isEqualTo("file_name-2.wav");
    }
}
