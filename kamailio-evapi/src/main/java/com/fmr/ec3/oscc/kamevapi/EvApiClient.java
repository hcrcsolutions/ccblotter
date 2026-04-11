package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * TCP client for the Kamailio EVAPI module.
 * Connects to the EVAPI socket and reads newline-delimited JSON events.
 * No authentication handshake — the connection is ready immediately.
 */
public class EvApiClient {

    private static final Logger log = LoggerFactory.getLogger(EvApiClient.class);

    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream writer;
    private final Thread readerThread;
    private volatile Consumer<String> eventListener;
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pendingRequests =
            new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class RpcException extends Exception {

        public RpcException(String message) {
            super(message);
        }
    }

    public EvApiClient(String host, int port, int connectTimeoutMs) throws IOException {
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setTcpNoDelay(true);
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = socket.getOutputStream();

        log.debug("EVAPI connected to {}:{}", host, port);

        readerThread = new Thread(this::readerLoop, "evapi-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void setEventListener(Consumer<String> listener) {
        this.eventListener = listener;
    }

    public synchronized void send(String json) throws IOException {
        writer.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    public CompletableFuture<JsonNode> sendRequest(String method, JsonNode params)
            throws IOException {
        long id = idSequence.getAndIncrement();
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("method", method);
        envelope.put("id", id);
        if (params != null) {
            envelope.set("params", params);
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        try {
            send(envelope.toString());
        } catch (IOException e) {
            pendingRequests.remove(id);
            throw e;
        }
        return future;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.debug("Error closing EVAPI socket", e);
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    private void readerLoop() {
        try {
            String line;
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.isBlank()) {
                    continue;
                }
                if (!pendingRequests.isEmpty()) {
                    if (tryCompleteRpcResponse(line)) {
                        continue;
                    }
                }
                Consumer<String> listener = this.eventListener;
                if (listener != null) {
                    try {
                        listener.accept(line);
                    } catch (Exception e) {
                        log.warn("Event listener threw exception", e);
                    }
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                log.debug("EVAPI reader loop terminated: {}", e.getMessage());
            }
        }
        completeAllPendingExceptionally(
                new IOException("EVAPI connection closed"));
        log.debug("EVAPI reader thread exiting");
    }

    private boolean tryCompleteRpcResponse(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            JsonNode idNode = node.get("id");
            if (idNode == null || idNode.isNull()) {
                return false;
            }
            long id = idNode.asLong();
            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
            if (future == null) {
                return false;
            }
            JsonNode error = node.get("error");
            if (error != null && !error.isNull()) {
                future.completeExceptionally(
                        new RpcException(error.toString()));
            } else {
                future.complete(node.get("result"));
            }
            return true;
        } catch (Exception e) {
            log.debug("Failed to parse line as JSON-RPC response: {}",
                    e.getMessage());
            return false;
        }
    }

    private void completeAllPendingExceptionally(Exception cause) {
        pendingRequests.forEach((id, future) -> {
            future.completeExceptionally(cause);
        });
        pendingRequests.clear();
    }
}
