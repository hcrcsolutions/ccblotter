package com.fmr.ec3.oscc.audiostore;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Async interface for storing, retrieving, and deleting IVR audio audio.
 * All futures complete exceptionally with {@link AudioStoreException} on failure.
 */
public interface AudioStore {

    CompletableFuture<Void> upload(UploadRequest request);

    CompletableFuture<InputStream> download(DownloadRequest request);

    CompletableFuture<Void> delete(DeleteRequest request);

    CompletableFuture<List<AudioMeta>> list(ListRequest request);

    CompletableFuture<Void> deleteSession(String ivrSessionId);
}
