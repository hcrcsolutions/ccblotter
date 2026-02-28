package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.internal.AudioBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class AudioBufferTest {

    @Test
    void appendAndDrain() {
        AudioBuffer buffer = new AudioBuffer(16, 1024);

        buffer.append(new byte[]{1, 2, 3}, 0, 3);
        buffer.append(new byte[]{4, 5}, 0, 2);

        assertThat(buffer.size()).isEqualTo(5);

        byte[] drained = buffer.drain();
        assertThat(drained).containsExactly(1, 2, 3, 4, 5);
        assertThat(buffer.size()).isEqualTo(0);
    }

    @Test
    void drainEmpty() {
        AudioBuffer buffer = new AudioBuffer(16, 1024);
        byte[] drained = buffer.drain();
        assertThat(drained).isEmpty();
    }

    @Test
    void overflow() {
        AudioBuffer buffer = new AudioBuffer(4, 10);

        buffer.append(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, 0, 8);
        assertThat(buffer.wasOverflow()).isFalse();

        buffer.append(new byte[]{9, 10, 11, 12, 13}, 0, 5);
        assertThat(buffer.wasOverflow()).isTrue();

        byte[] drained = buffer.drain();
        // Should have first 10 bytes (max capacity)
        assertThat(drained).hasSize(10);
        assertThat(drained).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void concurrentAppend() throws InterruptedException {
        AudioBuffer buffer = new AudioBuffer(16, 100_000);
        int threadCount = 10;
        int appendsPerThread = 100;
        int bytesPerAppend = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < appendsPerThread; i++) {
                    buffer.append(new byte[bytesPerAppend], 0, bytesPerAppend);
                }
                latch.countDown();
            });
            threads.add(thread);
            thread.start();
        }

        latch.await();
        byte[] drained = buffer.drain();
        assertThat(drained).hasSize(threadCount * appendsPerThread * bytesPerAppend);
    }
}
