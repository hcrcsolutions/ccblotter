package com.fmr.ec3.oscc.ivr.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionContextTest {

    @Test
    void buildMinimalContext() {
        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .build();

        assertThat(ctx.getCallId()).isEqualTo("call-1");
        assertThat(ctx.getOriginator()).isNull();
        assertThat(ctx.getVariables()).isEmpty();
        assertThat(ctx.getStepCount()).isZero();
        assertThat(ctx.getSessionStartTime()).isNull();
    }

    @Test
    void buildFullContext() {
        Instant now = Instant.now();
        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .originator("+15551234567")
                .variables(Map.of("lang", "en"))
                .stepCount(5)
                .sessionStartTime(now)
                .build();

        assertThat(ctx.getOriginator()).isEqualTo("+15551234567");
        assertThat(ctx.getVariables()).containsEntry("lang", "en");
        assertThat(ctx.getStepCount()).isEqualTo(5);
        assertThat(ctx.getSessionStartTime()).isEqualTo(now);
    }

    @Test
    void callIdIsRequired() {
        assertThatThrownBy(() -> SessionContext.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callId is required");
    }

    @Test
    void variablesAreImmutable() {
        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .variables(Map.of("key", "value"))
                .build();

        assertThatThrownBy(() -> ctx.getVariables().put("extra", "nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
