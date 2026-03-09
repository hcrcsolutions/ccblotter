package com.fmr.ec3.oscc.audiostore.s3;

import com.fmr.ec3.oscc.audiostore.DeleteRequest;
import com.fmr.ec3.oscc.audiostore.DownloadRequest;
import com.fmr.ec3.oscc.audiostore.ListRequest;
import com.fmr.ec3.oscc.audiostore.UploadRequest;
import com.fmr.ec3.oscc.audiostore.AudioMeta;
import com.fmr.ec3.oscc.audiostore.AudioStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class S3AudioStoreTest {

    private S3AsyncClient s3Client;
    private S3AudioStore store;
    private S3AudioStore storeWithPrefix;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3AsyncClient.class);

        S3AudioStoreConfig config = S3AudioStoreConfig.builder()
                .bucket("test-bucket")
                .s3Client(s3Client)
                .build();
        store = new S3AudioStore(config);

        S3AudioStoreConfig configWithPrefix = S3AudioStoreConfig.builder()
                .bucket("test-bucket")
                .prefix("audios")
                .s3Client(s3Client)
                .build();
        storeWithPrefix = new S3AudioStore(configWithPrefix);
    }

    @Test
    void uploadSendsCorrectRequest() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        byte[] data = {1, 2, 3, 4};
        UploadRequest request = UploadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .data(data)
                .contentType("audio/wav")
                .build();

        store.upload(request).join();

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(AsyncRequestBody.class));

        PutObjectRequest putRequest = captor.getValue();
        assertThat(putRequest.bucket()).isEqualTo("test-bucket");
        assertThat(putRequest.key()).isEqualTo("sess-1/utt-1");
        assertThat(putRequest.contentType()).isEqualTo("audio/wav");
        assertThat(putRequest.contentLength()).isEqualTo(4);
    }

    @Test
    void uploadWithPrefixBuildsCorrectKey() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        UploadRequest request = UploadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build();

        storeWithPrefix.upload(request).join();

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(AsyncRequestBody.class));
        assertThat(captor.getValue().key()).isEqualTo("audios/sess-1/utt-1");
    }

    @Test
    void downloadReturnsInputStream() throws Exception {
        byte[] content = {10, 20, 30};
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), content);

        when(s3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(CompletableFuture.completedFuture(responseBytes));

        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        InputStream result = store.download(request).join();
        assertThat(result.readAllBytes()).isEqualTo(content);
    }

    @Test
    void downloadWithPrefixBuildsCorrectKey() throws Exception {
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), new byte[]{1});

        when(s3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(CompletableFuture.completedFuture(responseBytes));

        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        storeWithPrefix.download(request).join();

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture(), any(AsyncResponseTransformer.class));
        assertThat(captor.getValue().key()).isEqualTo("audios/sess-1/utt-1");
    }

    @Test
    void deleteSendsCorrectRequest() throws Exception {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        store.delete(request).join();

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("sess-1/utt-1");
    }

    @Test
    void listReturnsAudioMeta() throws Exception {
        Instant now = Instant.now();
        S3Object obj = S3Object.builder()
                .key("sess-1/utt-1")
                .size(512L)
                .lastModified(now)
                .build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .maxResults(10)
                .build();

        List<AudioMeta> result = store.list(request).join();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAudioId()).isEqualTo("utt-1");
        assertThat(result.get(0).getSessionId()).isEqualTo("sess-1");
        assertThat(result.get(0).getSize()).isEqualTo(512L);
        assertThat(result.get(0).getLastModified()).isEqualTo(now);
    }

    @Test
    void listWithPrefixBuildsCorrectPrefix() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(S3Object.builder()
                        .key("audios/sess-1/utt-1")
                        .size(100L)
                        .lastModified(Instant.now())
                        .build())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .build();

        List<AudioMeta> result = storeWithPrefix.list(request).join();

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        assertThat(captor.getValue().prefix()).isEqualTo("audios/sess-1/");
        assertThat(result.get(0).getAudioId()).isEqualTo("utt-1");
    }

    @Test
    void deleteSessionDeletesAllObjects() throws Exception {
        S3Object obj1 = S3Object.builder().key("sess-1/utt-1").size(100L).lastModified(Instant.now()).build();
        S3Object obj2 = S3Object.builder().key("sess-1/utt-2").size(200L).lastModified(Instant.now()).build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj1, obj2)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        store.deleteSession("sess-1").join();

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());

        DeleteObjectsRequest deleteReq = captor.getValue();
        assertThat(deleteReq.delete().objects()).hasSize(2);
        assertThat(deleteReq.delete().quiet()).isTrue();
    }

    @Test
    void deleteSessionHandlesEmptyList() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        store.deleteSession("sess-1").join();
    }

    @Test
    void deleteSessionValidatesSessionId() {
        assertThatThrownBy(() -> store.deleteSession("../evil"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void uploadWrapsS3Exception() {
        CompletableFuture<PutObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("S3 down"));

        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(failedFuture);

        UploadRequest request = UploadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build();

        assertThatThrownBy(() -> store.upload(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class);
    }

    @Test
    void downloadWrapsS3Exception() {
        CompletableFuture<ResponseBytes<GetObjectResponse>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("S3 down"));

        when(s3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(failedFuture);

        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        assertThatThrownBy(() -> store.download(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class);
    }

    @Test
    void deleteWrapsS3Exception() {
        CompletableFuture<DeleteObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("S3 down"));

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(failedFuture);

        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        assertThatThrownBy(() -> store.delete(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class);
    }

    @Test
    void existingAudioStoreExceptionIsNotDoubleWrapped() {
        CompletableFuture<PutObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new AudioStoreException("already wrapped"));

        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(failedFuture);

        UploadRequest request = UploadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build();

        assertThatThrownBy(() -> store.upload(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class)
                .hasRootCauseMessage("already wrapped");
    }

    @Test
    void uploadUsesDefaultContentType() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        UploadRequest request = UploadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .data(new byte[]{1, 2})
                .build();

        store.upload(request).join();

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(AsyncRequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void downloadSendsCorrectBucketAndKey() throws Exception {
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), new byte[]{1});

        when(s3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(CompletableFuture.completedFuture(responseBytes));

        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        store.download(request).join();

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture(), any(AsyncResponseTransformer.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("sess-1/utt-1");
    }

    @Test
    void deleteWithPrefixBuildsCorrectKey() throws Exception {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        storeWithPrefix.delete(request).join();

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("audios/sess-1/utt-1");
    }

    @Test
    void listEmptyResultsReturnsEmptyList() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .build();

        List<AudioMeta> result = store.list(request).join();
        assertThat(result).isEmpty();
    }

    @Test
    void listMultipleObjectsReturnsMeta() throws Exception {
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-01-02T00:00:00Z");
        Instant t3 = Instant.parse("2024-01-03T00:00:00Z");

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("sess-1/audio-a").size(100L).lastModified(t1).build(),
                        S3Object.builder().key("sess-1/audio-b").size(200L).lastModified(t2).build(),
                        S3Object.builder().key("sess-1/audio-c").size(300L).lastModified(t3).build())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .build();

        List<AudioMeta> result = store.list(request).join();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getAudioId()).isEqualTo("audio-a");
        assertThat(result.get(0).getSize()).isEqualTo(100L);
        assertThat(result.get(1).getAudioId()).isEqualTo("audio-b");
        assertThat(result.get(1).getSize()).isEqualTo(200L);
        assertThat(result.get(2).getAudioId()).isEqualTo("audio-c");
        assertThat(result.get(2).getSize()).isEqualTo(300L);
    }

    @Test
    void listPassesMaxKeysToS3() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .maxResults(42)
                .build();

        store.list(request).join();

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        assertThat(captor.getValue().maxKeys()).isEqualTo(42);
    }

    @Test
    void listNoPrefixUsesSessionSlashAsPrefix() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of())
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .build();

        store.list(request).join();

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        assertThat(captor.getValue().prefix()).isEqualTo("sess-1/");
    }

    @Test
    void listWrapsS3Exception() {
        CompletableFuture<ListObjectsV2Response> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("S3 down"));

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(failedFuture);

        ListRequest request = ListRequest.builder()
                .sessionId("sess-1")
                .build();

        assertThatThrownBy(() -> store.list(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class);
    }

    @Test
    void deleteSessionWithPrefixBuildsCorrectPrefix() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("audios/sess-1/utt-1")
                        .size(100L).lastModified(Instant.now()).build())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        storeWithPrefix.deleteSession("sess-1").join();

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().prefix()).isEqualTo("audios/sess-1/");
    }

    @Test
    void deleteSessionPaginatesThroughTruncatedResults() throws Exception {
        S3Object obj1 = S3Object.builder().key("sess-1/utt-1").size(100L).lastModified(Instant.now()).build();
        S3Object obj2 = S3Object.builder().key("sess-1/utt-2").size(200L).lastModified(Instant.now()).build();

        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(obj1)
                .isTruncated(true)
                .nextContinuationToken("token-1")
                .build();
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(obj2)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(page1))
                .thenReturn(CompletableFuture.completedFuture(page2));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        store.deleteSession("sess-1").join();

        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void deleteSessionPassesContinuationToken() throws Exception {
        S3Object obj = S3Object.builder().key("sess-1/utt-1").size(100L).lastModified(Instant.now()).build();

        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(obj)
                .isTruncated(true)
                .nextContinuationToken("my-token")
                .build();
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(List.of())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(page1))
                .thenReturn(CompletableFuture.completedFuture(page2));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        store.deleteSession("sess-1").join();

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(captor.capture());

        List<ListObjectsV2Request> requests = captor.getAllValues();
        assertThat(requests.get(0).continuationToken()).isNull();
        assertThat(requests.get(1).continuationToken()).isEqualTo("my-token");
    }

    @Test
    void deleteSessionDoesNotCallDeleteObjectsWhenEmpty() throws Exception {
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(List.of())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        store.deleteSession("sess-1").join();

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void deleteSessionRejectsNullSessionId() {
        assertThatThrownBy(() -> store.deleteSession(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ivrSessionId is required");
    }

    @Test
    void deleteSessionRejectsBlankSessionId() {
        assertThatThrownBy(() -> store.deleteSession("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ivrSessionId is required");
    }

    @Test
    void deleteSessionWrapsS3ListException() {
        CompletableFuture<ListObjectsV2Response> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("list failed"));

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> store.deleteSession("sess-1").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class);
    }

    @Test
    void deleteSessionWrapsS3DeleteException() {
        S3Object obj = S3Object.builder().key("sess-1/utt-1").size(100L).lastModified(Instant.now()).build();
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        CompletableFuture<DeleteObjectsResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("delete failed"));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> store.deleteSession("sess-1").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class);
    }

    @Test
    void deleteSessionBatchUsesCorrectBucket() throws Exception {
        S3Object obj = S3Object.builder().key("sess-1/utt-1").size(100L).lastModified(Instant.now()).build();
        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        store.deleteSession("sess-1").join();

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
    }

    @Test
    void deleteSessionBatchIncludesCorrectKeys() throws Exception {
        S3Object obj1 = S3Object.builder().key("sess-1/a1").size(10L).lastModified(Instant.now()).build();
        S3Object obj2 = S3Object.builder().key("sess-1/a2").size(20L).lastModified(Instant.now()).build();
        S3Object obj3 = S3Object.builder().key("sess-1/a3").size(30L).lastModified(Instant.now()).build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj1, obj2, obj3)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectsResponse.builder().build()));

        store.deleteSession("sess-1").join();

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());

        List<String> keys = captor.getValue().delete().objects().stream()
                .map(ObjectIdentifier::key)
                .toList();
        assertThat(keys).containsExactly("sess-1/a1", "sess-1/a2", "sess-1/a3");
    }

    @Test
    void downloadExceptionPreservesOriginalCauseMessage() {
        CompletableFuture<ResponseBytes<GetObjectResponse>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("no such key"));

        when(s3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(failedFuture);

        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        assertThatThrownBy(() -> store.download(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class)
                .satisfies(e -> {
                    AudioStoreException cause = (AudioStoreException) e.getCause();
                    assertThat(cause.getMessage()).isEqualTo("S3 operation failed");
                    assertThat(cause.getCause()).isInstanceOf(RuntimeException.class);
                    assertThat(cause.getCause().getMessage()).isEqualTo("no such key");
                });
    }

    @Test
    void deleteExceptionPreservesOriginalCause() {
        CompletableFuture<DeleteObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("access denied"));

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(failedFuture);

        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        assertThatThrownBy(() -> store.delete(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class)
                .satisfies(e -> {
                    AudioStoreException cause = (AudioStoreException) e.getCause();
                    assertThat(cause.getCause().getMessage()).isEqualTo("access denied");
                });
    }

    @Test
    void existingAudioStoreExceptionNotDoubleWrappedOnDownload() {
        CompletableFuture<ResponseBytes<GetObjectResponse>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new AudioStoreException("original error"));

        when(s3Client.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(failedFuture);

        DownloadRequest request = DownloadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        assertThatThrownBy(() -> store.download(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class)
                .hasRootCauseMessage("original error");
    }

    @Test
    void existingAudioStoreExceptionNotDoubleWrappedOnDelete() {
        CompletableFuture<DeleteObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new AudioStoreException("original error"));

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(failedFuture);

        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        assertThatThrownBy(() -> store.delete(request).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AudioStoreException.class)
                .hasRootCauseMessage("original error");
    }

    @Test
    void completedUploadFutureIsNotExceptional() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        UploadRequest request = UploadRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .data(new byte[]{1})
                .build();

        CompletableFuture<Void> future = store.upload(request);
        assertThat(future.isCompletedExceptionally()).isFalse();
        assertThat(future.join()).isNull();
    }

    @Test
    void completedDeleteFutureIsNotExceptional() throws Exception {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        DeleteRequest request = DeleteRequest.builder()
                .sessionId("sess-1")
                .audioId("utt-1")
                .build();

        CompletableFuture<Void> future = store.delete(request);
        assertThat(future.isCompletedExceptionally()).isFalse();
        assertThat(future.join()).isNull();
    }
}
