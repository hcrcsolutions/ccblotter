package com.fmr.ec3.oscc.fsesl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal ESL (Event Socket Library) inbound client using plain java.net.Socket.
 * Supports only {@code auth} and {@code api} commands — no event subscriptions.
 */
public class EslClient {

    private static final Logger log = LoggerFactory.getLogger(EslClient.class);

    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream writer;

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
        String body = readBody(authHeaders);
        String replyText = authHeaders.get("Reply-Text");
        if (replyText == null || !replyText.startsWith("+OK")) {
            close();
            throw new IOException("ESL auth failed: " + replyText);
        }

        log.debug("ESL authenticated to {}:{}", host, port);
    }

    public synchronized String sendApi(String command) throws IOException {
        sendRaw("api " + command + "\n\n");
        Map<String, String> headers = readHeaders();
        return readBody(headers);
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
