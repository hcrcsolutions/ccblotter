package com.fmr.ec3.oscc.ivr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IvrFlowExceptionTest {

    @Test
    void messageOnly() {
        IvrFlowException ex = new IvrFlowException("something failed");
        assertThat(ex.getMessage()).isEqualTo("something failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        IvrFlowException ex = new IvrFlowException("something failed", cause);
        assertThat(ex.getMessage()).isEqualTo("something failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void isUnchecked() {
        assertThat(new IvrFlowException("test")).isInstanceOf(RuntimeException.class);
    }
}
