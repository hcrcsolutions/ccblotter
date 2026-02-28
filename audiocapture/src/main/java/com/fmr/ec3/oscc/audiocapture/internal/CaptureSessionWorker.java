package com.fmr.ec3.oscc.audiocapture.internal;

import com.fmr.ec3.oscc.audiocapture.CaptureException;
import com.fmr.ec3.oscc.audiocapture.CaptureResult;
import com.fmr.ec3.oscc.audiocapture.delivery.AudioDeliveryTarget;
import com.fmr.ec3.oscc.audiocapture.wav.WavWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Runnable that drains the audio buffer, encodes a WAV file, and delivers it.
 */
public class CaptureSessionWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CaptureSessionWorker.class);

    private final AudioBuffer buffer;
    private final AudioDeliveryTarget deliveryTarget;
    private final String sessionId;
    private final String stepId;
    private final CompletableFuture<CaptureResult> future;

    public CaptureSessionWorker(AudioBuffer buffer, AudioDeliveryTarget deliveryTarget,
                                String sessionId, String stepId,
                                CompletableFuture<CaptureResult> future) {
        this.buffer = buffer;
        this.deliveryTarget = deliveryTarget;
        this.sessionId = sessionId;
        this.stepId = stepId;
        this.future = future;
    }

    @Override
    public void run() {
        try {
            byte[] pcmData = buffer.drain();

            if (pcmData.length == 0) {
                future.complete(CaptureResult.empty(sessionId, stepId));
                return;
            }

            ByteArrayOutputStream wavOut = new ByteArrayOutputStream(WavWriter.totalFileSize(pcmData.length));
            WavWriter.write(wavOut, pcmData, 0, pcmData.length);
            byte[] wavData = wavOut.toByteArray();

            String url = deliveryTarget.deliver(wavData, sessionId, stepId);

            CaptureResult result = new CaptureResult(
                    url,
                    WavWriter.durationMs(pcmData.length),
                    wavData.length,
                    false,
                    sessionId,
                    stepId
            );

            log.debug("Capture delivered: session={}, step={}, duration={}ms, size={}",
                    sessionId, stepId, result.getDurationMs(), result.getFileSizeBytes());

            future.complete(result);
        } catch (Exception e) {
            log.error("Capture failed: session={}, step={}", sessionId, stepId, e);
            future.completeExceptionally(new CaptureException(
                    "Failed to deliver capture for session " + sessionId + " step " + stepId, e));
        }
    }
}
