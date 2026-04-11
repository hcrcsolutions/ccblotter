package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Component
public class EvApiConnectionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EvApiConnectionManager.class);

    private final KamailioProperties props;
    private volatile EvApiClient client;
    private volatile boolean connected;
    private ScheduledExecutorService reconnectExecutor;
    private final AtomicLong currentDelay = new AtomicLong();
    private volatile Consumer<String> eventListener;
    private final CopyOnWriteArrayList<Runnable> onConnectedCallbacks =
            new CopyOnWriteArrayList<>();

    public EvApiConnectionManager(KamailioProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        currentDelay.set(props.getReconnectInitialDelayMs());
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "evapi-reconnect");
            t.setDaemon(true);
            return t;
        });
        // Connect asynchronously so @PostConstruct never blocks startup
        reconnectExecutor.execute(this::attemptConnect);
    }

    public boolean isConnected() {
        return connected;
    }

    public void send(String json) {
        if (!connected) {
            return;
        }
        EvApiClient c = this.client;
        if (c == null) {
            return;
        }
        try {
            c.send(json);
        } catch (IOException e) {
            log.warn("EVAPI send failed, triggering reconnect: {}", e.getMessage());
            markDisconnected();
            scheduleReconnect();
        }
    }

    public void setEventListener(Consumer<String> listener) {
        this.eventListener = listener;
        EvApiClient c = this.client;
        if (c != null) {
            c.setEventListener(listener);
        }
    }

    public void addOnConnectedCallback(Runnable callback) {
        this.onConnectedCallbacks.add(callback);
    }

    public CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) {
        if (!connected) {
            CompletableFuture<JsonNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IOException("Not connected"));
            return failed;
        }
        EvApiClient c = this.client;
        if (c == null) {
            CompletableFuture<JsonNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IOException("Not connected"));
            return failed;
        }
        try {
            return c.sendRequest(method, params);
        } catch (IOException e) {
            log.warn("EVAPI sendRequest failed, triggering reconnect: {}",
                    e.getMessage());
            markDisconnected();
            scheduleReconnect();
            CompletableFuture<JsonNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private void attemptConnect() {
        try {
            EvApiClient c = new EvApiClient(
                    props.getEvApiHost(), props.getEvApiPort(),
                    props.getConnectTimeoutMs());

            Consumer<String> listener = this.eventListener;
            if (listener != null) {
                c.setEventListener(listener);
            }

            this.client = c;
            this.connected = true;
            currentDelay.set(props.getReconnectInitialDelayMs());
            log.info("EVAPI connected to {}:{}", props.getEvApiHost(), props.getEvApiPort());

            for (Runnable callback : onConnectedCallbacks) {
                try {
                    callback.run();
                } catch (Exception e) {
                    log.warn("onConnectedCallback threw exception", e);
                }
            }
        } catch (IOException e) {
            log.warn("EVAPI connect failed ({}:{}): {}",
                    props.getEvApiHost(), props.getEvApiPort(), e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        long delay = currentDelay.get();
        log.debug("Scheduling EVAPI reconnect in {}ms", delay);
        reconnectExecutor.schedule(this::attemptConnect, delay, TimeUnit.MILLISECONDS);
        long nextDelay = (long) (delay * props.getReconnectMultiplier());
        currentDelay.set(Math.min(nextDelay, props.getReconnectMaxDelayMs()));
    }

    private void markDisconnected() {
        connected = false;
        EvApiClient c = this.client;
        this.client = null;
        if (c != null) {
            c.close();
        }
    }

    @Override
    public void destroy() {
        markDisconnected();
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
        }
    }
}
