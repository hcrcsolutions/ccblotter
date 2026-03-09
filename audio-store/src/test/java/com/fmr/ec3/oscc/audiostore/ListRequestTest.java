package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListRequestTest {

    @Test
    void buildsWithDefaults() {
        ListRequest request = ListRequest.builder()
                .sessionId("session-1")
                .build();

        assertThat(request.getSessionId()).isEqualTo("session-1");
        assertThat(request.getMaxResults()).isEqualTo(1000);
    }

    @Test
    void customMaxResults() {
        ListRequest request = ListRequest.builder()
                .sessionId("session-1")
                .maxResults(50)
                .build();

        assertThat(request.getMaxResults()).isEqualTo(50);
    }

    @Test
    void rejectsNullSessionId() {
        assertThatThrownBy(() -> ListRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void rejectsZeroMaxResults() {
        assertThatThrownBy(() -> ListRequest.builder()
                .sessionId("session-1")
                .maxResults(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults");
    }

    @Test
    void rejectsNegativeMaxResults() {
        assertThatThrownBy(() -> ListRequest.builder()
                .sessionId("session-1")
                .maxResults(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults");
    }

    @Test
    void rejectsOverThousandMaxResults() {
        assertThatThrownBy(() -> ListRequest.builder()
                .sessionId("session-1")
                .maxResults(1001)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults");
    }
}
