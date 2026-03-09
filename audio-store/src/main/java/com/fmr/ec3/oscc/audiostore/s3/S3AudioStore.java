package com.fmr.ec3.oscc.audiostore.s3;

import com.fmr.ec3.oscc.audiostore.DeleteRequest;
import com.fmr.ec3.oscc.audiostore.DownloadRequest;
import com.fmr.ec3.oscc.audiostore.KeyValidator;
import com.fmr.ec3.oscc.audiostore.ListRequest;
import com.fmr.ec3.oscc.audiostore.UploadRequest;
import com.fmr.ec3.oscc.audiostore.AudioMeta;
import com.fmr.ec3.oscc.audiostore.AudioStore;
import com.fmr.ec3.oscc.audiostore.AudioStoreException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * S3-backed implementation of {@link AudioStore}.
 * All operations are non-blocking via {@link S3AsyncClient}.
 */
@Slf4j
public class S3AudioStore implements AudioStore {

    private final S3AsyncClient s3Client;
    private final String bucket;
    private final String prefix;

    public S3AudioStore(S3AudioStoreConfig config) {
        this.s3Client = config.getS3Client();
        this.bucket = config.getBucket();
        this.prefix = config.getPrefix();
    }

    @Override
    public CompletableFuture<Void> upload(UploadRequest request) {
        String key = buildKey(request.getSessionId(), request.getAudioId());
        log.debug("Uploading audio to s3://{}/{}", bucket, key);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.getContentType())
                .contentLength((long) request.getData().length)
                .build();

        return s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(request.getData()))
                .<Void>thenApply(response -> null)
                .exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<InputStream> download(DownloadRequest request) {
        String key = buildKey(request.getSessionId(), request.getAudioId());
        log.debug("Downloading audio from s3://{}/{}", bucket, key);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.getObject(getRequest, AsyncResponseTransformer.toBytes())
                .thenApply(responseBytes -> (InputStream) new ByteArrayInputStream(responseBytes.asByteArray()))
                .exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> delete(DeleteRequest request) {
        String key = buildKey(request.getSessionId(), request.getAudioId());
        log.debug("Deleting audio at s3://{}/{}", bucket, key);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.deleteObject(deleteRequest)
                .<Void>thenApply(response -> null)
                .exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<List<AudioMeta>> list(ListRequest request) {
        String sessionPrefix = buildSessionPrefix(request.getSessionId());
        log.debug("Listing audios at s3://{}/{}", bucket, sessionPrefix);

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(sessionPrefix)
                .maxKeys(request.getMaxResults())
                .build();

        return s3Client.listObjectsV2(listRequest)
                .thenApply(response -> toAudioMeta(response, request.getSessionId()))
                .exceptionally(this::handleException);
    }

    @Override
    public CompletableFuture<Void> deleteSession(String ivrSessionId) {
        KeyValidator.validate(ivrSessionId, "ivrSessionId");
        String sessionPrefix = buildSessionPrefix(ivrSessionId);
        log.debug("Deleting session at s3://{}/{}", bucket, sessionPrefix);

        return deleteSessionRecursive(sessionPrefix, null);
    }

    private CompletableFuture<Void> deleteSessionRecursive(String sessionPrefix, String continuationToken) {
        ListObjectsV2Request.Builder listBuilder = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(sessionPrefix)
                .maxKeys(1000);
        if (continuationToken != null) {
            listBuilder.continuationToken(continuationToken);
        }

        return s3Client.listObjectsV2(listBuilder.build())
                .thenCompose(response -> {
                    List<S3Object> objects = response.contents();
                    if (objects.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    CompletableFuture<Void> deleteFuture = deleteBatch(objects);

                    if (Boolean.TRUE.equals(response.isTruncated())) {
                        return deleteFuture.thenCompose(
                                v -> deleteSessionRecursive(sessionPrefix, response.nextContinuationToken()));
                    }
                    return deleteFuture;
                })
                .exceptionally(this::handleException);
    }

    private CompletableFuture<Void> deleteBatch(List<S3Object> objects) {
        List<ObjectIdentifier> identifiers = objects.stream()
                .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                .toList();

        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder()
                        .objects(identifiers)
                        .quiet(true)
                        .build())
                .build();

        return s3Client.deleteObjects(deleteRequest)
                .thenApply(response -> null);
    }

    private List<AudioMeta> toAudioMeta(ListObjectsV2Response response, String sessionId) {
        List<AudioMeta> result = new ArrayList<>();
        for (S3Object obj : response.contents()) {
            String audioId = extractAudioId(obj.key());
            result.add(new AudioMeta(audioId, sessionId, obj.size(), obj.lastModified()));
        }
        return result;
    }

    private String extractAudioId(String key) {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    private String buildKey(String sessionId, String audioId) {
        if (prefix.isEmpty()) {
            return sessionId + "/" + audioId;
        }
        return prefix + "/" + sessionId + "/" + audioId;
    }

    private String buildSessionPrefix(String sessionId) {
        if (prefix.isEmpty()) {
            return sessionId + "/";
        }
        return prefix + "/" + sessionId + "/";
    }

    private <T> T handleException(Throwable throwable) {
        Throwable cause = throwable;
        if (cause instanceof CompletionException) {
            cause = cause.getCause();
        }
        if (cause instanceof AudioStoreException) {
            throw (AudioStoreException) cause;
        }
        throw new AudioStoreException("S3 operation failed", cause);
    }
}
