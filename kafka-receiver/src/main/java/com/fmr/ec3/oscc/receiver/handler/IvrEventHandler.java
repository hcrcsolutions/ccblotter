package com.fmr.ec3.oscc.receiver.handler;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionEndedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionStartedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrStepCompletedPayload;
import com.fmr.ec3.oscc.receiver.state.IvrStateWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Handles IVR session events from media servers.
 * Topic: ccblotter.ivr.events (partitioned by sessionId).
 *
 * <h3>Event flow</h3>
 * <pre>
 * ┌──────────────────────────┬──────────────────────┬───────────────┐
 * │ Trigger                  │ Event Type           │ Partition Key │
 * ├──────────────────────────┼──────────────────────┼───────────────┤
 * │ Caller enters IVR flow   │ IVR_SESSION_STARTED  │ sessionId     │
 * │ Step finishes executing  │ IVR_STEP_COMPLETED   │ sessionId     │
 * │ Session ends             │ IVR_SESSION_ENDED    │ sessionId     │
 * └──────────────────────────┴──────────────────────┴───────────────┘
 * </pre>
 *
 * All events for the same session are serialized on the same partition,
 * ensuring steps arrive in order and session start always precedes end.
 */
public class IvrEventHandler {

    private static final Logger log = LoggerFactory.getLogger(IvrEventHandler.class);

    private final IvrStateWriter ivrWriter;

    public IvrEventHandler(IvrStateWriter ivrWriter) {
        this.ivrWriter = ivrWriter;
    }

    public void handle(EventEnvelope<?> envelope) {
        switch (envelope.eventType()) {
            case EventType.IVR_SESSION_STARTED -> handleSessionStarted(envelope);
            case EventType.IVR_STEP_COMPLETED -> handleStepCompleted(envelope);
            case EventType.IVR_SESSION_ENDED -> handleSessionEnded(envelope);
            default -> log.warn("Unknown IVR event type: {}", envelope.eventType());
        }
    }

    private void handleSessionStarted(EventEnvelope<?> envelope) {
        IvrSessionStartedPayload p = (IvrSessionStartedPayload) envelope.payload();

        if (ivrWriter.sessionExists(p.sessionId())) {
            log.warn("IVR_SESSION_STARTED for already-existing session {} - overwriting", p.sessionId());
        }

        ivrWriter.createSession(
            p.sessionId(),
            p.callId(),
            p.originator(),
            p.flowId(),
            Instant.ofEpochMilli(p.startTimeMs())
        );
    }

    private void handleStepCompleted(EventEnvelope<?> envelope) {
        IvrStepCompletedPayload p = (IvrStepCompletedPayload) envelope.payload();

        if (!ivrWriter.sessionExists(p.sessionId())) {
            log.warn("IVR_STEP_COMPLETED for unknown session {} - ignoring", p.sessionId());
            return;
        }

        ivrWriter.addStep(
            p.sessionId(),
            p.stepId(),
            p.stepType(),
            p.stepName(),
            p.prompt(),
            p.input(),
            p.outcome(),
            Instant.ofEpochMilli(p.startTimeMs()),
            Instant.ofEpochMilli(p.endTimeMs()),
            p.latencyMs(),
            p.retryAttempt(),
            p.error(),
            p.audioUrl()
        );
    }

    private void handleSessionEnded(EventEnvelope<?> envelope) {
        IvrSessionEndedPayload p = (IvrSessionEndedPayload) envelope.payload();

        if (!ivrWriter.sessionExists(p.sessionId())) {
            log.warn("IVR_SESSION_ENDED for unknown session {} - ignoring", p.sessionId());
            return;
        }

        ivrWriter.endSession(
            p.sessionId(),
            p.endState(),
            Instant.ofEpochMilli(p.endTimeMs()),
            p.stepCount(),
            p.authenticated()
        );
    }
}
