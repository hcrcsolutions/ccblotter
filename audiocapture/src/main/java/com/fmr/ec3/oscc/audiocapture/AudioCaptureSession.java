package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.internal.AudioBuffer;
import com.fmr.ec3.oscc.audiocapture.internal.CaptureSessionWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Captures audio data from a media stream and delivers it as a WAV file.
 *
 * <p>Usage:
 * <pre>
 * AudioCaptureSession session = AudioCaptureSession.start(config, sessionId, stepId);
 * // ... on each audio frame:
 * session.write(rtpPacket, 0, rtpPacket.length);
 * // ... when capture is complete:
 * CaptureResult result = session.stop().join();
 * </pre>
 */
public class AudioCaptureSession {

    private static final Logger log = LoggerFactory.getLogger(AudioCaptureSession.class);
    private static final int INITIAL_BUFFER_CAPACITY = 16_384;

    private final AudioCaptureConfig config;
    private final String sessionId;
    private final String stepId;
    private final AudioBuffer buffer;
    private volatile boolean stopped;
    private volatile boolean cancelled;

    private AudioCaptureSession(AudioCaptureConfig config, String sessionId, String stepId) {
        this.config = config;
        this.sessionId = sessionId;
        this.stepId = stepId;
        this.buffer = new AudioBuffer(INITIAL_BUFFER_CAPACITY, config.getBufferCapacityBytes());
    }

    public static AudioCaptureSession start(AudioCaptureConfig config, String sessionId, String stepId) {
        log.debug("Starting capture: session={}, step={}", sessionId, stepId);
        return new AudioCaptureSession(config, sessionId, stepId);
    }

    public void write(byte[] data, int offset, int length) {
        if (stopped || cancelled) {
            return;
        }
        byte[] pcm = config.getCodec().decode(data, offset, length);
        buffer.append(pcm, 0, pcm.length);
    }

    public CompletableFuture<CaptureResult> stop() {
        stopped = true;
        CompletableFuture<CaptureResult> future = new CompletableFuture<>();
        CaptureSessionWorker worker = new CaptureSessionWorker(
                buffer, config.getDeliveryTarget(), sessionId, stepId, future);
        config.getThreadPool().submit(worker);
        return future;
    }

    public void cancel() {
        cancelled = true;
        stopped = true;
        buffer.drain();
        log.debug("Capture cancelled: session={}, step={}", sessionId, stepId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStepId() {
        return stepId;
    }
}
