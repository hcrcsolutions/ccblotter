package com.fmr.ec3.oscc.ivr.execution;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.NodeType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpNodeExecutorTest {

    @SuppressWarnings("unchecked")
    @Test
    void executeGetRequest() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"status\":\"ok\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        HttpNodeExecutor executor = new HttpNodeExecutor(mockClient);

        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://example.com/api");
        config.put("method", "GET");
        config.put("responseVariable", "apiResult");
        config.put("timeoutSeconds", 10);

        FlowNode node = FlowNode.builder()
                .id("http1")
                .type(NodeType.HTTP_REQUEST)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .sessionStartTime(Instant.now())
                .build();

        ExecutionResult result = executor.execute(node, ctx);
        assertThat(result.getResult()).containsEntry("statusCode", 200);
        assertThat(result.getResult()).containsEntry("body", "{\"status\":\"ok\"}");
        assertThat(result.getUpdatedVariables()).containsEntry("apiResult", "{\"status\":\"ok\"}");
    }

    @SuppressWarnings("unchecked")
    @Test
    void executePostWithTemplateResolution() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(201);
        when(mockResponse.body()).thenReturn("created");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        HttpNodeExecutor executor = new HttpNodeExecutor(mockClient);

        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://example.com/api");
        config.put("method", "POST");
        config.put("bodyTemplate", "{\"caller\":\"${callerId}\"}");
        config.put("timeoutSeconds", 5);

        FlowNode node = FlowNode.builder()
                .id("http2")
                .type(NodeType.HTTP_REQUEST)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .variables(Map.of("callerId", "12345"))
                .sessionStartTime(Instant.now())
                .build();

        ExecutionResult result = executor.execute(node, ctx);
        assertThat(result.getResult()).containsEntry("statusCode", 201);
    }

    @SuppressWarnings("unchecked")
    @Test
    void enforcesMaxTimeout() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("ok");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        HttpNodeExecutor executor = new HttpNodeExecutor(mockClient);

        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://example.com/api");
        config.put("timeoutSeconds", 999);

        FlowNode node = FlowNode.builder()
                .id("http3")
                .type(NodeType.HTTP_REQUEST)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .sessionStartTime(Instant.now())
                .build();

        // Should not throw — timeout is clamped to MAX_TIMEOUT_SECONDS
        ExecutionResult result = executor.execute(node, ctx);
        assertThat(result.getResult()).containsEntry("statusCode", 200);
    }

    @SuppressWarnings("unchecked")
    @Test
    void wrapsIOException() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        HttpNodeExecutor executor = new HttpNodeExecutor(mockClient);

        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://example.com/api");
        config.put("timeoutSeconds", 5);

        FlowNode node = FlowNode.builder()
                .id("http4")
                .type(NodeType.HTTP_REQUEST)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .sessionStartTime(Instant.now())
                .build();

        assertThatThrownBy(() -> executor.execute(node, ctx))
                .isInstanceOf(IvrFlowException.class)
                .hasMessageContaining("HTTP request failed")
                .hasCauseInstanceOf(IOException.class);
    }
}
