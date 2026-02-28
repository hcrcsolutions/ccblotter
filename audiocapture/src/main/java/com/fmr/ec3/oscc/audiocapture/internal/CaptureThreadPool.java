package com.fmr.ec3.oscc.audiocapture.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared daemon thread pool for audio capture I/O operations.
 */
public class CaptureThreadPool {

    private final ExecutorService executor;

    public CaptureThreadPool(int poolSize) {
        AtomicInteger counter = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "audiocapture-io-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
