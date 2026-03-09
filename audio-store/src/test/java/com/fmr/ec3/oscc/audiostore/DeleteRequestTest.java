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
}
