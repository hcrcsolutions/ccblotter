package com.fmr.ec3.oscc.fsesl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EslClientTest {

    private ServerSocket serverSocket;
    private Thread serverThread;

    @AfterEach
    void tearDown() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.join(2000);
        }
    }

    @Test
    void authAndApiRoundTrip() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        AtomicReference<String> receivedCommand = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();

                // Send auth/request
                out.write("Content-Type: auth/request\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Read auth command
                readUntilDoubleNewline(in);

                // Send auth reply
                out.write("Content-Type: command/reply\nReply-Text: +OK accepted\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Read api command
                String apiCmd = readUntilDoubleNewline(in);
                receivedCommand.set(apiCmd);

                // Send api response
                String body = "5 session(s) - peak 10\n";
                String response = "Content-Type: api/response\nContent-Length: "
                        + body.length() + "\n\n" + body;
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                // test cleanup
            }
        });
        serverThread.start();

        // Constructor handles connect + auth synchronously
        EslClient client = new EslClient("localhost", port, "ClueCon", 3000);
        assertTrue(client.isConnected());

        String result = client.sendApi("status");
        assertEquals("5 session(s) - peak 10\n", result);
        assertTrue(receivedCommand.get().contains("api status"));

        client.close();
        assertFalse(client.isConnected());
    }

    @Test
    void authFailureThrowsException() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                out.write("Content-Type: auth/request\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();

                readUntilDoubleNewline(client.getInputStream());

                out.write("Content-Type: command/reply\nReply-Text: -ERR invalid\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                // test cleanup
            }
        });
        serverThread.start();

        assertThrows(IOException.class,
                () -> new EslClient("localhost", port, "wrong", 3000));
    }

    @Test
    void connectionRefusedThrowsException() {
        assertThrows(IOException.class,
                () -> new EslClient("localhost", 1, "pass", 1000));
    }

    private static String readUntilDoubleNewline(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (prev == '\n' && c == '\n') {
                break;
            }
            prev = c;
        }
        return sb.toString();
    }
}
