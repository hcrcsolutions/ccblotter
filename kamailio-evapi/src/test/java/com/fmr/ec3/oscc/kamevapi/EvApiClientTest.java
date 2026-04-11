package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EvApiClientTest {

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
    void connectAndReceiveJsonLine() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> receivedLine = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                Thread.sleep(100);
                out.write("{\"event\":\"usrloc:contact-insert\",\"aor\":\"sip:agent1001@sip.example.com\"}\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        assertTrue(client.isConnected());
        client.setEventListener(line -> {
            receivedLine.set(line);
            received.countDown();
        });

        assertTrue(received.await(3, TimeUnit.SECONDS), "Should receive JSON line");
        assertTrue(receivedLine.get().contains("usrloc:contact-insert"));
        client.close();
    }

    @Test
    void multipleEventLines() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch received = new CountDownLatch(3);
        List<String> lines = Collections.synchronizedList(new ArrayList<>());

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                Thread.sleep(100);
                out.write("{\"event\":\"one\"}\n".getBytes(StandardCharsets.UTF_8));
                out.write("{\"event\":\"two\"}\n".getBytes(StandardCharsets.UTF_8));
                out.write("{\"event\":\"three\"}\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        client.setEventListener(line -> {
            lines.add(line);
            received.countDown();
        });

        assertTrue(received.await(3, TimeUnit.SECONDS), "Should receive 3 lines");
        assertEquals(3, lines.size());
        client.close();
    }

    @Test
    void blankLinesIgnored() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch received = new CountDownLatch(2);
        List<String> lines = Collections.synchronizedList(new ArrayList<>());

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                Thread.sleep(100);
                out.write("{\"event\":\"one\"}\n\n\n{\"event\":\"two\"}\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        client.setEventListener(line -> {
            lines.add(line);
            received.countDown();
        });

        assertTrue(received.await(3, TimeUnit.SECONDS), "Should receive 2 real events");
        assertEquals(2, lines.size());
        client.close();
    }

    @Test
    void sendJsonToServer() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        AtomicReference<String> serverReceived = new AtomicReference<>();
        CountDownLatch serverReady = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                serverReady.countDown();
                InputStream in = client.getInputStream();
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        break;
                    }
                    sb.append((char) c);
                }
                serverReceived.set(sb.toString());
            } catch (IOException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        serverReady.await(3, TimeUnit.SECONDS);
        client.send("{\"subscribe\":\"usrloc\"}");
        client.close();

        serverThread.join(2000);
        assertEquals("{\"subscribe\":\"usrloc\"}", serverReceived.get());
    }

    @Test
    void connectionRefusedThrowsException() {
        assertThrows(IOException.class,
                () -> new EvApiClient("localhost", 1, 1000));
    }

    @Test
    void serverClosesConnectionGracefully() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                // Give time for listener to be set
                Thread.sleep(200);
                out.write("{\"event\":\"hello\"}\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(100);
                // Server closes the connection
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        CountDownLatch received = new CountDownLatch(1);
        EvApiClient client = new EvApiClient("localhost", port, 3000);
        client.setEventListener(line -> received.countDown());

        assertTrue(received.await(3, TimeUnit.SECONDS));

        // Wait for reader thread to detect close
        Thread.sleep(500);
        client.close();
        assertFalse(client.isConnected());
    }

    @Test
    void sendRequestReceivesResponse() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch serverReady = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                serverReady.countDown();
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        break;
                    }
                    sb.append((char) c);
                }
                // Echo back a JSON-RPC response with id=1
                Thread.sleep(50);
                out.write("{\"jsonrpc\":\"2.0\",\"result\":{\"foo\":\"bar\"},\"id\":1}\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        serverReady.await(3, TimeUnit.SECONDS);

        CompletableFuture<JsonNode> future = client.sendRequest("ul.dump", null);
        JsonNode result = future.get(3, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("bar", result.get("foo").asText());
        client.close();
    }

    @Test
    void sendRequestReceivesError() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch serverReady = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                serverReady.countDown();
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        break;
                    }
                    sb.append((char) c);
                }
                Thread.sleep(50);
                out.write("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Method not found\"},\"id\":1}\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        serverReady.await(3, TimeUnit.SECONDS);

        CompletableFuture<JsonNode> future = client.sendRequest("bad.method", null);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertInstanceOf(EvApiClient.RpcException.class, ex.getCause());
        client.close();
    }

    @Test
    void pushEventsStillDeliveredDuringPendingRequest() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch pushReceived = new CountDownLatch(1);
        AtomicReference<String> pushEvent = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                serverReady.countDown();
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                // Read the request
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        break;
                    }
                    sb.append((char) c);
                }
                Thread.sleep(50);
                // Send a push event first (no "id" field)
                out.write("{\"event\":\"usrloc:contact-insert\",\"aor\":\"sip:1001@example.com\"}\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(50);
                // Then send the RPC response
                out.write("{\"jsonrpc\":\"2.0\",\"result\":[],\"id\":1}\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        client.setEventListener(line -> {
            pushEvent.set(line);
            pushReceived.countDown();
        });
        serverReady.await(3, TimeUnit.SECONDS);

        CompletableFuture<JsonNode> future = client.sendRequest("ul.dump", null);

        assertTrue(pushReceived.await(3, TimeUnit.SECONDS),
                "Push event should be delivered");
        assertTrue(pushEvent.get().contains("usrloc:contact-insert"));

        JsonNode result = future.get(3, TimeUnit.SECONDS);
        assertTrue(result.isArray());
        client.close();
    }

    @Test
    void pushEventsDeliveredWithoutParsing() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> receivedLine = new AtomicReference<>();

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                Thread.sleep(100);
                out.write("{\"event\":\"test\"}\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1000);
            } catch (IOException | InterruptedException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        client.setEventListener(line -> {
            receivedLine.set(line);
            received.countDown();
        });

        // No pending requests — fast path
        assertTrue(received.await(3, TimeUnit.SECONDS));
        assertEquals("{\"event\":\"test\"}", receivedLine.get());
        client.close();
    }

    @Test
    void disconnectCompletesPendingFuturesExceptionally() throws Exception {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        CountDownLatch serverReady = new CountDownLatch(1);

        serverThread = new Thread(() -> {
            try (Socket client = serverSocket.accept()) {
                serverReady.countDown();
                InputStream in = client.getInputStream();
                // Read the request
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = in.read()) != -1) {
                    if (c == '\n') {
                        break;
                    }
                    sb.append((char) c);
                }
                // Close without responding
            } catch (IOException e) {
                // test cleanup
            }
        });
        serverThread.start();

        EvApiClient client = new EvApiClient("localhost", port, 3000);
        serverReady.await(3, TimeUnit.SECONDS);

        CompletableFuture<JsonNode> future = client.sendRequest("ul.dump", null);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(3, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, ex.getCause());
        client.close();
    }
}
