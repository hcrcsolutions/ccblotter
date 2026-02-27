package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionStartedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrStepCompletedPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IvrSessionSimulatorTest {

    @Mock
    private EventProducer eventProducer;

    private SimulationProperties props;
    private IvrSessionSimulator simulator;

    @BeforeEach
    void setUp() {
        props = new SimulationProperties();
        simulator = new IvrSessionSimulator(eventProducer, props);
    }

    @Test
    void initSeedsFiveSessionsAndSetsRunning() {
        simulator.init();

        assertTrue(simulator.isRunning());
        assertEquals(5, simulator.getActiveSessionCount());
        verify(eventProducer, times(5)).send(
            eq(KafkaTopics.IVR_EVENTS), anyString(),
            eq(EventType.IVR_SESSION_STARTED), anyString(), anyString(),
            any(IvrSessionStartedPayload.class));
    }

    @Test
    void stopPreventsTickExecution() {
        simulator.init();
        reset(eventProducer);

        simulator.stop();
        assertFalse(simulator.isRunning());

        simulator.tick();
        verifyNoInteractions(eventProducer);
    }

    @Test
    void startResumesAfterStop() {
        simulator.init();
        simulator.stop();
        simulator.start();
        assertTrue(simulator.isRunning());
    }

    @Test
    void tickAdvancesSessionsAndProducesStepEvents() {
        simulator.init();
        reset(eventProducer);

        // Run enough ticks for steps to be emitted (steps have delay timers)
        for (int i = 0; i < 50; i++) {
            simulator.tick();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.IVR_EVENTS), anyString(),
            eq(EventType.IVR_STEP_COMPLETED), anyString(), anyString(),
            any(IvrStepCompletedPayload.class));
    }

    @Test
    void captureStepsWithInputHaveAudioUrl() {
        simulator.init();

        // Run enough ticks to generate many step events
        for (int i = 0; i < 100; i++) {
            simulator.tick();
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.IVR_EVENTS), anyString(),
            eq(EventType.IVR_STEP_COMPLETED), anyString(), anyString(),
            payloadCaptor.capture());

        Pattern audioUrlPattern = Pattern.compile("/audio/samples/capture-[1-5]\\.wav");
        boolean foundCaptureWithAudio = false;
        boolean foundNonCaptureWithoutAudio = false;

        for (Object payload : payloadCaptor.getAllValues()) {
            IvrStepCompletedPayload p = (IvrStepCompletedPayload) payload;

            if ("CAPTURE".equals(p.stepType()) && p.input() != null) {
                assertNotNull(p.audioUrl(),
                    "CAPTURE step with input should have audioUrl");
                assertTrue(audioUrlPattern.matcher(p.audioUrl()).matches(),
                    "audioUrl should match pattern: " + p.audioUrl());
                foundCaptureWithAudio = true;
            } else if (!"CAPTURE".equals(p.stepType())) {
                assertNull(p.audioUrl(),
                    "Non-CAPTURE step should have null audioUrl, got: " + p.audioUrl());
                foundNonCaptureWithoutAudio = true;
            }
        }

        assertTrue(foundCaptureWithAudio,
            "Should have found at least one CAPTURE step with audioUrl");
        assertTrue(foundNonCaptureWithoutAudio,
            "Should have found at least one non-CAPTURE step without audioUrl");
    }

    @Test
    void captureTimeoutHasNullAudioUrl() {
        simulator.init();

        // Run many ticks to get timeout events (10% chance per capture)
        for (int i = 0; i < 200; i++) {
            simulator.tick();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.IVR_EVENTS), anyString(),
            eq(EventType.IVR_STEP_COMPLETED), anyString(), anyString(),
            payloadCaptor.capture());

        for (Object payload : payloadCaptor.getAllValues()) {
            IvrStepCompletedPayload p = (IvrStepCompletedPayload) payload;
            if ("CAPTURE".equals(p.stepType()) && "timeout".equals(p.outcome())) {
                assertNull(p.input(), "Timeout capture should have null input");
                assertNull(p.audioUrl(), "Timeout capture should have null audioUrl");
            }
        }
    }

    @Test
    void sessionStartedPayloadContainsRequiredFields() {
        simulator.init();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.IVR_EVENTS), anyString(),
            eq(EventType.IVR_SESSION_STARTED), anyString(), anyString(),
            payloadCaptor.capture());

        for (Object payload : payloadCaptor.getAllValues()) {
            IvrSessionStartedPayload p = (IvrSessionStartedPayload) payload;
            assertNotNull(p.sessionId());
            assertTrue(p.sessionId().startsWith("IVR-"));
            assertNotNull(p.callId());
            assertTrue(p.callId().startsWith("CALL-"));
            assertNotNull(p.originator());
            assertNotNull(p.flowId());
            assertTrue(p.startTimeMs() > 0);
        }
    }
}
