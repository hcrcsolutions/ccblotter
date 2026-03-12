package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a TRANSFER node. Built via {@link #builder()}.
 */
public class TransferConfig {

    private final String destination;
    private final TransferType transferType;
    private final String whisperPrompt;

    private TransferConfig(Builder builder) {
        this.destination = builder.destination;
        this.transferType = builder.transferType;
        this.whisperPrompt = builder.whisperPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDestination() {
        return destination;
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public String getWhisperPrompt() {
        return whisperPrompt;
    }

    public enum TransferType {
        AGENT,
        QUEUE,
        EXTERNAL
    }

    public static final class Builder {

        private String destination;
        private TransferType transferType = TransferType.AGENT;
        private String whisperPrompt;

        private Builder() {
        }

        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }

        public Builder transferType(TransferType transferType) {
            this.transferType = transferType;
            return this;
        }

        public Builder whisperPrompt(String whisperPrompt) {
            this.whisperPrompt = whisperPrompt;
            return this;
        }

        public TransferConfig build() {
            if (destination == null || destination.isBlank()) {
                throw new IllegalArgumentException("destination is required");
            }
            return new TransferConfig(this);
        }
    }
}
