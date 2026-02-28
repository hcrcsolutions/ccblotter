package com.fmr.ec3.oscc.audiocapture.internal;

/**
 * Thread-safe growable byte buffer with a maximum capacity limit.
 */
public class AudioBuffer {

    private byte[] data;
    private int size;
    private final int maxCapacity;
    private boolean overflow;

    public AudioBuffer(int initialCapacity, int maxCapacity) {
        this.data = new byte[initialCapacity];
        this.maxCapacity = maxCapacity;
    }

    public synchronized void append(byte[] src, int offset, int length) {
        int available = maxCapacity - size;
        if (available <= 0) {
            overflow = true;
            return;
        }
        int toWrite = Math.min(length, available);
        if (toWrite < length) {
            overflow = true;
        }
        ensureCapacity(size + toWrite);
        System.arraycopy(src, offset, data, size, toWrite);
        size += toWrite;
    }

    public synchronized byte[] drain() {
        byte[] result = new byte[size];
        System.arraycopy(data, 0, result, 0, size);
        size = 0;
        data = new byte[0];
        return result;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean wasOverflow() {
        return overflow;
    }

    private void ensureCapacity(int required) {
        if (required <= data.length) {
            return;
        }
        int newCapacity = Math.max(data.length * 2, required);
        newCapacity = Math.min(newCapacity, maxCapacity);
        byte[] newData = new byte[newCapacity];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }
}
