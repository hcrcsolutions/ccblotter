package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.AudioCodec;
import com.fmr.ec3.oscc.audiocapture.delivery.AudioDeliveryTarget;
import com.fmr.ec3.oscc.audiocapture.internal.CaptureThreadPool;

/**
 * Immutable configuration for audio capture sessions. Built via {@link #builder()}.
 */
public class AudioCaptureConfig {

    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final int DEFAULT_MAX_CAPTURE_DURATION_SECONDS = 300;
    private static final int DEFAULT_BUFFER_CAPACITY_BYTES = 320_000;

    private final AudioCodec codec;
    private final AudioDeliveryTarget deliveryTarget;
    private final int maxCaptureDurationSeconds;
    private final int bufferCapacityBytes;
    private final CaptureThreadPool threadPool;

    private AudioCaptureConfig(Builder builder) {
        this.codec = builder.codec;
        this.deliveryTarget = builder.deliveryTarget;
        this.maxCaptureDurationSeconds = builder.maxCaptureDurationSeconds;
        this.bufferCapacityBytes = builder.bufferCapacityBytes;
        this.threadPool = new CaptureThreadPool(builder.threadPoolSize);
    }

    public static Builder builder() {
        return new Builder();
    }

    public AudioCodec getCodec() {
        return codec;
    }

    public AudioDeliveryTarget getDeliveryTarget() {
        return deliveryTarget;
    }

    public int getMaxCaptureDurationSeconds() {
        return maxCaptureDurationSeconds;
    }

    public int getBufferCapacityBytes() {
        return bufferCapacityBytes;
    }

    public CaptureThreadPool getThreadPool() {
        return threadPool;
    }

    public void shutdown() {
        threadPool.shutdown();
    }

    public static final class Builder {

        private AudioCodec codec;
        private AudioDeliveryTarget deliveryTarget;
        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        private int maxCaptureDurationSeconds = DEFAULT_MAX_CAPTURE_DURATION_SECONDS;
        private int bufferCapacityBytes = DEFAULT_BUFFER_CAPACITY_BYTES;

        private Builder() {
        }

        public Builder codec(AudioCodec codec) {
            this.codec = codec;
            return this;
        }

        public Builder deliveryTarget(AudioDeliveryTarget deliveryTarget) {
            this.deliveryTarget = deliveryTarget;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public Builder maxCaptureDurationSeconds(int maxCaptureDurationSeconds) {
            this.maxCaptureDurationSeconds = maxCaptureDurationSeconds;
            return this;
        }

        public Builder bufferCapacityBytes(int bufferCapacityBytes) {
            this.bufferCapacityBytes = bufferCapacityBytes;
            return this;
        }

        public AudioCaptureConfig build() {
            if (codec == null) {
                throw new IllegalArgumentException("codec is required");
            }
            if (deliveryTarget == null) {
                throw new IllegalArgumentException("deliveryTarget is required");
            }
            if (threadPoolSize <= 0) {
                throw new IllegalArgumentException("threadPoolSize must be positive");
            }
            if (maxCaptureDurationSeconds <= 0) {
                throw new IllegalArgumentException("maxCaptureDurationSeconds must be positive");
            }
            if (bufferCapacityBytes <= 0) {
                throw new IllegalArgumentException("bufferCapacityBytes must be positive");
            }
            return new AudioCaptureConfig(this);
        }
    }
}
