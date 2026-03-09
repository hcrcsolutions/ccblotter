package com.fmr.ec3.oscc.audiostore.s3;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class S3AudioStoreConfigTest {

    @Test
    void buildsWithRequiredFields() {
        S3AsyncClient client = mock(S3AsyncClient.class);
        S3AudioStoreConfig config = S3AudioStoreConfig.builder()
                .bucket("my-bucket")
                .s3Client(client)
                .build();

        assertThat(config.getBucket()).isEqualTo("my-bucket");
        assertThat(config.getS3Client()).isSameAs(client);
        assertThat(config.getPrefix()).isEmpty();
    }

    @Test
    void customPrefix() {
        S3AsyncClient client = mock(S3AsyncClient.class);
        S3AudioStoreConfig config = S3AudioStoreConfig.builder()
                .bucket("my-bucket")
                .prefix("audios")
                .s3Client(client)
                .build();

        assertThat(config.getPrefix()).isEqualTo("audios");
    }

    @Test
    void rejectsNullBucket() {
        assertThatThrownBy(() -> S3AudioStoreConfig.builder()
                .s3Client(mock(S3AsyncClient.class))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket is required");
    }

    @Test
    void rejectsBlankBucket() {
        assertThatThrownBy(() -> S3AudioStoreConfig.builder()
                .bucket("  ")
                .s3Client(mock(S3AsyncClient.class))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket is required");
    }

    @Test
    void rejectsNullS3Client() {
        assertThatThrownBy(() -> S3AudioStoreConfig.builder()
                .bucket("my-bucket")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s3Client is required");
    }

    @Test
    void rejectsEmptyBucket() {
        assertThatThrownBy(() -> S3AudioStoreConfig.builder()
                .bucket("")
                .s3Client(mock(S3AsyncClient.class))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket is required");
    }

    @Test
    void defaultPrefixIsEmpty() {
        S3AudioStoreConfig config = S3AudioStoreConfig.builder()
                .bucket("b")
                .s3Client(mock(S3AsyncClient.class))
                .build();

        assertThat(config.getPrefix()).isEqualTo("");
    }

    @Test
    void prefixCanBeOverridden() {
        S3AudioStoreConfig config = S3AudioStoreConfig.builder()
                .bucket("b")
                .prefix("p1")
                .prefix("p2")
                .s3Client(mock(S3AsyncClient.class))
                .build();

        assertThat(config.getPrefix()).isEqualTo("p2");
    }
}
