package com.fmr.ec3.oscc.ivr.execution;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.config.HttpRequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes HTTP_REQUEST nodes using {@link java.net.http.HttpClient}.
 * Enforces a maximum timeout of {@value HttpRequestConfig#MAX_TIMEOUT_SECONDS} seconds.
 */
public class HttpNodeExecutor implements DynamicNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeExecutor.class);

    private final HttpClient httpClient;

    public HttpNodeExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HttpRequestConfig.MAX_TIMEOUT_SECONDS))
                .build();
    }

    public HttpNodeExecutor(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ExecutionResult execute(FlowNode node, SessionContext context) {
        Map<String, Object> config = node.getConfig();

        String url = (String) config.get("url");
        String method = (String) config.getOrDefault("method", "GET");
        String bodyTemplate = (String) config.get("bodyTemplate");
        String responseVariable = (String) config.get("responseVariable");

        int timeoutSeconds = HttpRequestConfig.MAX_TIMEOUT_SECONDS;
        Object timeoutObj = config.get("timeoutSeconds");
        if (timeoutObj instanceof Number) {
            timeoutSeconds = Math.min(
                    ((Number) timeoutObj).intValue(),
                    HttpRequestConfig.MAX_TIMEOUT_SECONDS);
        }

        try {
            String body = resolveTemplate(bodyTemplate, context);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            // Apply headers
            Object headersObj = config.get("headers");
            if (headersObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) headersObj;
                headers.forEach(requestBuilder::header);
            }

            // Set method and body
            if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.POST(body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody());
            } else if ("PUT".equalsIgnoreCase(method)) {
                requestBuilder.PUT(body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody());
            } else if ("DELETE".equalsIgnoreCase(method)) {
                requestBuilder.DELETE();
            } else {
                requestBuilder.GET();
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            log.debug("HTTP {} {} -> {} ({} chars)",
                    method, url, response.statusCode(), response.body().length());

            Map<String, Object> result = new HashMap<>();
            result.put("statusCode", response.statusCode());
            result.put("body", response.body());

            Map<String, Object> updatedVars = new HashMap<>(context.getVariables());
            if (responseVariable != null && !responseVariable.isBlank()) {
                updatedVars.put(responseVariable, response.body());
            }

            return ExecutionResult.builder()
                    .result(result)
                    .updatedVariables(updatedVars)
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IvrFlowException("HTTP request interrupted for node " + node.getId(), e);
        } catch (IvrFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new IvrFlowException(
                    "HTTP request failed for node " + node.getId() + ": " + e.getMessage(), e);
        }
    }

    private String resolveTemplate(String template, SessionContext context) {
        if (template == null) {
            return null;
        }
        String resolved = template;
        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            resolved = resolved.replace(
                    "${" + entry.getKey() + "}",
                    String.valueOf(entry.getValue()));
        }
        return resolved;
    }
}
