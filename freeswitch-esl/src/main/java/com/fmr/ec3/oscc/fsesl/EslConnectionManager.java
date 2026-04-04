package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EslConnectionManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EslConnectionManager.class);

    private final FreeSwitchProperties props;
    private volatile EslClient eslClient;
    private volatile boolean connected;
    private ScheduledExecutorService reconnectExecutor;
    private final AtomicLong currentDelay = new AtomicLong();

    public EslConnectionManager(FreeSwitchProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        currentDelay.set(props.getReconnectInitialDelayMs());
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "esl-reconnect");
            t.setDaemon(true);
            return t;
        });
        attemptConnect();
    }

    public boolean isConnected() {
        return connected;
    }

    public Optional<String> sendApi(String command) {
        if (!connected) {
            return Optional.empty();
        }
        EslClient client = this.eslClient;
        if (client == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(client.sendApi(command));
        } catch (IOException e) {
            log.warn("ESL command failed ({}), triggering reconnect: {}", command, e.getMessage());
            markDisconnected();
            scheduleReconnect();
            return Optional.empty();
        }
    }

    private void attemptConnect() {
        try {
            EslClient client = new EslClient(
                props.getEslHost(), props.getEslPort(),
                props.getEslPassword(), props.getEslConnectTimeoutMs()
            );
            this.eslClient = client;
            this.connected = true;
            currentDelay.set(props.getReconnectInitialDelayMs());
            log.info("ESL connected to {}:{}", props.getEslHost(), props.getEslPort());
        } catch (IOException e) {
            log.warn("ESL connect failed ({}:{}): {}", props.getEslHost(), props.getEslPort(), e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        long delay = currentDelay.get();
        log.debug("Scheduling ESL reconnect in {}ms", delay);
        reconnectExecutor.schedule(this::attemptConnect, delay, TimeUnit.MILLISECONDS);
        long nextDelay = (long) (delay * props.getReconnectMultiplier());
        currentDelay.set(Math.min(nextDelay, props.getReconnectMaxDelayMs()));
    }

    private void markDisconnected() {
        connected = false;
        EslClient client = this.eslClient;
        this.eslClient = null;
        if (client != null) {
            client.close();
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
