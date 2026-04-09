package com.fmr.ec3.oscc.fsesl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Test
    void eventSubscriptionReceivesEvents() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch eventReceived = new CountDownLatch(1);
        AtomicReference<EslClient.EslMessage> receivedEvent = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();

                // Auth handshake
                out.write("Content-Type: auth/request\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                readUntilDoubleNewline(in);
                out.write("Content-Type: command/reply\nReply-Text: +OK accepted\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Give client time to start reader thread
                Thread.sleep(100);

                // Send event plain
                String eventBody = "Log-Level: 3\nLog-File: switch_core.c\n\nSome error message";
                String eventMsg = "Content-Type: text/event-plain\nContent-Length: "
                        + eventBody.length() + "\n\n" + eventBody;
                out.write(eventMsg.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Keep connection alive until test is done
                Thread.sleep(2000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EslClient client = new EslClient("localhost", port, "ClueCon", 3000);
        client.setEventListener(event -> {
            receivedEvent.set(event);
            eventReceived.countDown();
        });

        assertTrue(eventReceived.await(3, TimeUnit.SECONDS), "Event should be received");
        EslClient.EslMessage event = receivedEvent.get();
        assertNotNull(event);
        assertEquals("3", event.headers().get("Log-Level"));
        assertEquals("switch_core.c", event.headers().get("Log-File"));
        assertEquals("Some error message", event.body());

        client.close();
    }

    @Test
    void sendApiWorksWithReaderThread() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();

                // Auth handshake
                out.write("Content-Type: auth/request\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                readUntilDoubleNewline(in);
                out.write("Content-Type: command/reply\nReply-Text: +OK accepted\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Read api command
                readUntilDoubleNewline(in);

                // Send api response
                String body = "RUNNING";
                String response = "Content-Type: api/response\nContent-Length: "
                        + body.length() + "\n\n" + body;
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Read second api command
                readUntilDoubleNewline(in);

                // Send second api response
                String body2 = "42 total.\n";
                String response2 = "Content-Type: api/response\nContent-Length: "
                        + body2.length() + "\n\n" + body2;
                out.write(response2.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EslClient client = new EslClient("localhost", port, "ClueCon", 3000);

        String result1 = client.sendApi("sofia status");
        assertEquals("RUNNING", result1);

        String result2 = client.sendApi("show channels count");
        assertEquals("42 total.\n", result2);

        client.close();
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
