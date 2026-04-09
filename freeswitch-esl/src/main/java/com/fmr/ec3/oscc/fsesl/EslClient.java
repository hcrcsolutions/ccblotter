package com.fmr.ec3.oscc.fsesl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ESL (Event Socket Library) inbound client using plain java.net.Socket.
 * Supports {@code auth}, {@code api}, generic commands, and asynchronous event subscription.
 *
 * A background reader thread continuously reads messages from the socket and routes them:
 * - api/response or command/reply → reply queue (consumed by sendApi/sendCommand)
 * - text/event-plain → dispatched to registered event listener
 * - text/disconnect-notice → logged and socket closed
 */
public class EslClient {

    private static final Logger log = LoggerFactory.getLogger(EslClient.class);
    private static final long REPLY_TIMEOUT_MS = 10_000;

    public record EslMessage(Map<String, String> headers, String body) {}

    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream writer;
    private final LinkedBlockingQueue<EslMessage> replyQueue = new LinkedBlockingQueue<>();
    private volatile Consumer<EslMessage> eventListener;
    private final Thread readerThread;

    public EslClient(String host, int port, String password, int connectTimeoutMs) throws IOException {
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setTcpNoDelay(true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = socket.getOutputStream();

        // Read auth/request
        Map<String, String> headers = readHeaders();
        String contentType = headers.get("Content-Type");
        if (!"auth/request".equals(contentType)) {
            close();
            throw new IOException("Expected auth/request, got: " + contentType);
        }

        // Send auth
        sendRaw("auth " + password + "\n\n");

        // Read auth reply
        Map<String, String> authHeaders = readHeaders();
        readBody(authHeaders);
        String replyText = authHeaders.get("Reply-Text");
        if (replyText == null || !replyText.startsWith("+OK")) {
            close();
            throw new IOException("ESL auth failed: " + replyText);
        }

        log.debug("ESL authenticated to {}:{}", host, port);

        // Start background reader thread
        readerThread = new Thread(this::readerLoop, "esl-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized String sendApi(String command) throws IOException {
        sendRaw("api " + command + "\n\n");
        EslMessage reply = awaitReply();
        return reply.body();
    }

    public synchronized String sendCommand(String command) throws IOException {
        sendRaw(command + "\n\n");
        EslMessage reply = awaitReply();
        String replyText = reply.headers().get("Reply-Text");
        return replyText != null ? replyText : reply.body();
    }

    public void setEventListener(Consumer<EslMessage> listener) {
        this.eventListener = listener;
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
            log.debug("Error closing ESL socket", e);
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    private EslMessage awaitReply() throws IOException {
        try {
            EslMessage reply = replyQueue.poll(REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (reply == null) {
                throw new IOException("Timed out waiting for ESL reply");
            }
            return reply;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for ESL reply", e);
        }
    }

    private void readerLoop() {
        try {
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                Map<String, String> headers = readHeaders();
                if (headers.isEmpty()) {
                    break;
                }
                String contentType = headers.get("Content-Type");
                String body = readBody(headers);

                if ("text/event-plain".equals(contentType)) {
                    EslMessage event = parseEventPlain(body);
                    Consumer<EslMessage> listener = this.eventListener;
                    if (listener != null) {
                        try {
                            listener.accept(event);
                        } catch (Exception e) {
                            log.warn("Event listener threw exception", e);
                        }
                    }
                } else if ("text/disconnect-notice".equals(contentType)) {
                    log.info("ESL disconnect notice received");
                    break;
                } else {
                    // api/response or command/reply
                    replyQueue.offer(new EslMessage(headers, body));
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                log.debug("ESL reader loop terminated: {}", e.getMessage());
            }
        }
        log.debug("ESL reader thread exiting");
    }

    private EslMessage parseEventPlain(String raw) {
        Map<String, String> eventHeaders = new HashMap<>();
        String eventBody = "";

        if (raw == null || raw.isEmpty()) {
            return new EslMessage(eventHeaders, eventBody);
        }

        // Event plain format: URL-encoded headers (key: value), blank line, then body
        String[] parts = raw.split("\n\n", 2);
        String headerSection = parts[0];
        if (parts.length > 1) {
            eventBody = parts[1];
        }

        for (String line : headerSection.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = URLDecoder.decode(line.substring(colon + 1).trim(), StandardCharsets.UTF_8);
                eventHeaders.put(key, value);
            }
        }

        return new EslMessage(eventHeaders, eventBody);
    }

    private void sendRaw(String data) throws IOException {
        writer.write(data.getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    private Map<String, String> readHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    private String readBody(Map<String, String> headers) throws IOException {
        String contentLengthStr = headers.get("Content-Length");
        if (contentLengthStr == null) {
            return "";
        }
        int contentLength = Integer.parseInt(contentLengthStr.trim());
        if (contentLength <= 0) {
            return "";
        }
        char[] buf = new char[contentLength];
        int totalRead = 0;
        while (totalRead < contentLength) {
            int read = reader.read(buf, totalRead, contentLength - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream reading ESL body");
            }
            totalRead += read;
        }
        return new String(buf, 0, totalRead);
    }
}
