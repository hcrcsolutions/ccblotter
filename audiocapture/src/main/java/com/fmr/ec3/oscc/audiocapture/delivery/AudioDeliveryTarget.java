package com.fmr.ec3.oscc.audiocapture.delivery;

import java.io.IOException;

/**
 * Delivers a completed WAV recording and returns a URL for playback.
 */
public interface AudioDeliveryTarget {

    String deliver(byte[] wavData, String sessionId, String stepId) throws IOException;
}
