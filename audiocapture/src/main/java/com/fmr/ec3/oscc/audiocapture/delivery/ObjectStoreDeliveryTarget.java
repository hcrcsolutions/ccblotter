package com.fmr.ec3.oscc.audiocapture.delivery;

import java.io.IOException;

/**
 * Delivers WAV data to an object store via a caller-provided {@link ObjectStoreUploader}.
 */
public class ObjectStoreDeliveryTarget implements AudioDeliveryTarget {

    private final ObjectStoreUploader uploader;
    private final String keyPrefix;

    public ObjectStoreDeliveryTarget(ObjectStoreUploader uploader, String keyPrefix) {
        this.uploader = uploader;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String deliver(byte[] wavData, String sessionId, String stepId) throws IOException {
        String key = keyPrefix + stepId + ".wav";
        return uploader.upload(key, wavData, "audio/wav");
    }
}
