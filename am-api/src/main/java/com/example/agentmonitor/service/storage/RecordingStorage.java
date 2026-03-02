package com.example.agentmonitor.service.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public interface RecordingStorage {

    void store(String filename, InputStream inputStream, long size) throws IOException;

    Resource load(String filename) throws IOException;

    void delete(String filename) throws IOException;

    boolean exists(String filename) throws IOException;

    String getFileUrl(String filename);
}
