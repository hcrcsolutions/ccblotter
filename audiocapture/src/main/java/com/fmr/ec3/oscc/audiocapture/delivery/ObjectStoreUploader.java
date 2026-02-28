package com.fmr.ec3.oscc.audiocapture.delivery;

import java.io.IOException;

/**
 * Functional interface for uploading data to an object store (S3, MinIO, etc.).
 * The media server injects its own implementation with its preferred SDK.
 */
@FunctionalInterface
public interface ObjectStoreUploader {

    String upload(String key, byte[] data, String contentType) throws IOException;
}
