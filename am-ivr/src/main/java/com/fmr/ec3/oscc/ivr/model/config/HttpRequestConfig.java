package com.fmr.ec3.oscc.ivr.model.config;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for an HTTP_REQUEST node. Built via {@link #builder()}.
 */
public class HttpRequestConfig {

    public static final int MAX_TIMEOUT_SECONDS = 30;

    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final String bodyTemplate;
    private final String responseVariable;
    private final int timeoutSeconds;

    private HttpRequestConfig(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = builder.headers == null
                ? Collections.emptyMap()
                : Map.copyOf(builder.headers);
        this.bodyTemplate = builder.bodyTemplate;
        this.responseVariable = builder.responseVariable;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public String getResponseVariable() {
        return responseVariable;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public static final class Builder {

        private String url;
        private String method = "GET";
        private Map<String, String> headers;
        private String bodyTemplate;
        private String responseVariable;
        private int timeoutSeconds = 10;

        private Builder() {
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder bodyTemplate(String bodyTemplate) {
            this.bodyTemplate = bodyTemplate;
            return this;
        }

        public Builder responseVariable(String responseVariable) {
            this.responseVariable = responseVariable;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public HttpRequestConfig build() {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("url is required");
            }
            if (timeoutSeconds <= 0 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
                throw new IllegalArgumentException(
                        "timeoutSeconds must be between 1 and " + MAX_TIMEOUT_SECONDS);
            }
            return new HttpRequestConfig(this);
        }
    }
}
