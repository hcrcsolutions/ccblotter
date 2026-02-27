package com.fmr.ec3.oscc.receiver.handler;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionEndedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionStartedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrStepCompletedPayload;
import com.fmr.ec3.oscc.receiver.state.IvrStateWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IvrEventHandlerTest {

    @Mock
    private IvrStateWriter ivrWriter;

    private IvrEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IvrEventHandler(ivrWriter);
    }

    private <T> EventEnvelope<T> envelope(String eventType, T payload) {
        return new EventEnvelope<>("evt-1", 1L, "media-sim-1", "corr-1", "key-1",
            eventType, System.currentTimeMillis(), 1, payload);
    }

    @Nested
    class SessionStartedTests {

        @Test
        void createsNewSession() {
            long startMs = 1700000000000L;
            var payload = new IvrSessionStartedPayload(
                "IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu", startMs, "media-sim-1");

            when(ivrWriter.sessionExists("IVR-00001")).thenReturn(false);

            handler.handle(envelope(EventType.IVR_SESSION_STARTED, payload));

            verify(ivrWriter).createSession(
                "IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu",
                Instant.ofEpochMilli(startMs));
        }

        @Test
        void overwritesExistingSession() {
            long startMs = 1700000000000L;
            var payload = new IvrSessionStartedPayload(
                "IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu", startMs, "media-sim-1");

            when(ivrWriter.sessionExists("IVR-00001")).thenReturn(true);

            handler.handle(envelope(EventType.IVR_SESSION_STARTED, payload));

            verify(ivrWriter).createSession(
                "IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu",
                Instant.ofEpochMilli(startMs));
        }
    }

    @Nested
    class StepCompletedTests {

        @Test
        void passesAllFieldsIncludingAudioUrl() {
            long startMs = 1700000000000L;
            long endMs = 1700000003000L;
            var payload = new IvrStepCompletedPayload(
                "IVR-00001-S2", "IVR-00001", "CAPTURE", "Enter your PIN",
                "Please enter your PIN.", "1234", "success",
                startMs, endMs, 150, 0, null,
                "/audio/samples/capture-3.wav", "media-sim-1");

            when(ivrWriter.sessionExists("IVR-00001")).thenReturn(true);

            handler.handle(envelope(EventType.IVR_STEP_COMPLETED, payload));

            verify(ivrWriter).addStep(
                "IVR-00001", "IVR-00001-S2", "CAPTURE", "Enter your PIN",
                "Please enter your PIN.", "1234", "success",
                Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs),
                150, 0, null, "/audio/samples/capture-3.wav");
        }

        @Test
        void passesNullAudioUrlForNonCaptureStep() {
            long startMs = 1700000000000L;
            long endMs = 1700000002000L;
            var payload = new IvrStepCompletedPayload(
                "IVR-00001-S1", "IVR-00001", "PLAY", "Welcome Greeting",
                "Welcome to our service.", null, "success",
                startMs, endMs, 80, 0, null, null, "media-sim-1");

            when(ivrWriter.sessionExists("IVR-00001")).thenReturn(true);

            handler.handle(envelope(EventType.IVR_STEP_COMPLETED, payload));

            verify(ivrWriter).addStep(
                "IVR-00001", "IVR-00001-S1", "PLAY", "Welcome Greeting",
                "Welcome to our service.", null, "success",
                Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs),
                80, 0, null, null);
        }

        @Test
        void ignoresStepForUnknownSession() {
            var payload = new IvrStepCompletedPayload(
                "IVR-99999-S1", "IVR-99999", "PLAY", "Welcome",
                "Hello.", null, "success",
                1700000000000L, 1700000002000L, 80, 0, null, null, "media-sim-1");

            when(ivrWriter.sessionExists("IVR-99999")).thenReturn(false);

            handler.handle(envelope(EventType.IVR_STEP_COMPLETED, payload));

            verify(ivrWriter, never()).addStep(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyString(), any(), any(),
                anyLong(), anyInt(), any(), any());
        }
    }

    @Nested
    class SessionEndedTests {

        @Test
        void endsExistingSession() {
            long endMs = 1700000060000L;
            var payload = new IvrSessionEndedPayload(
                "IVR-00001", "CALL-abc", "COMPLETED", endMs, 5, true, "media-sim-1");

            when(ivrWriter.sessionExists("IVR-00001")).thenReturn(true);

            handler.handle(envelope(EventType.IVR_SESSION_ENDED, payload));

            verify(ivrWriter).endSession(
                "IVR-00001", "COMPLETED", Instant.ofEpochMilli(endMs), 5, true);
        }

        @Test
        void ignoresEndForUnknownSession() {
            var payload = new IvrSessionEndedPayload(
                "IVR-99999", "CALL-xyz", "ABANDONED", 1700000060000L, 2, false, "media-sim-1");

            when(ivrWriter.sessionExists("IVR-99999")).thenReturn(false);

            handler.handle(envelope(EventType.IVR_SESSION_ENDED, payload));

            verify(ivrWriter, never()).endSession(
                anyString(), anyString(), any(), anyInt(), anyBoolean());
        }
    }

    @Test
    void unknownEventTypeDoesNotThrow() {
        var payload = new IvrSessionStartedPayload(
            "IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu",
            1700000000000L, "media-sim-1");

        handler.handle(envelope("UNKNOWN_EVENT", payload));

        verifyNoInteractions(ivrWriter);
    }
}
