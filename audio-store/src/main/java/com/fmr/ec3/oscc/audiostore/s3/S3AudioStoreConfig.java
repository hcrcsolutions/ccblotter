package com.fmr.ec3.oscc.audiostore.s3;

import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Immutable configuration for the S3 audio store. Built via {@link #builder()}.
 */
public class S3AudioStoreConfig {

    private final String bucket;
    private final String prefix;
    private final S3AsyncClient s3Client;

    private S3AudioStoreConfig(Builder builder) {
        this.bucket = builder.bucket;
        this.prefix = builder.prefix;
        this.s3Client = builder.s3Client;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBucket() {
        return bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public S3AsyncClient getS3Client() {
        return s3Client;
    }

    public static final class Builder {

        private String bucket;
        private String prefix = "";
        private S3AsyncClient s3Client;

        private Builder() {
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder s3Client(S3AsyncClient s3Client) {
            this.s3Client = s3Client;
            return this;
        }

        public S3AudioStoreConfig build() {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("bucket is required");
            }
            if (s3Client == null) {
                throw new IllegalArgumentException("s3Client is required");
            }
            return new S3AudioStoreConfig(this);
        }
    }
}
