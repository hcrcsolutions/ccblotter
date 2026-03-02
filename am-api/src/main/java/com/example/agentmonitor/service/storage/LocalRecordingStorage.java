package com.example.agentmonitor.service.storage;

import com.example.agentmonitor.config.RecordingProperties;
import com.example.agentmonitor.exception.InvalidRecordingException;
import com.example.agentmonitor.exception.RecordingNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(name = "app.recordings.storage", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LocalRecordingStorage implements RecordingStorage {

    private final RecordingProperties properties;

    private Path storageRoot;

    @PostConstruct
    void init() throws IOException {
        storageRoot = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
        log.info("Local recording storage directory: {}", storageRoot);
    }

    @Override
    public void store(String filename, InputStream inputStream, long size) throws IOException {
        Path target = resolveAndValidate(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored recording locally: {} ({} bytes)", filename, size);
    }

    @Override
    public Resource load(String filename) throws IOException {
        Path file = resolveAndValidate(filename);
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        }
        throw new RecordingNotFoundException(filename);
    }

    @Override
    public void delete(String filename) throws IOException {
        Path file = resolveAndValidate(filename);
        if (!Files.exists(file)) {
            throw new RecordingNotFoundException(filename);
        }
        Files.delete(file);
        log.info("Deleted local recording: {}", filename);
    }

    @Override
    public boolean exists(String filename) throws IOException {
        Path file = resolveAndValidate(filename);
        return Files.exists(file);
    }

    @Override
    public String getFileUrl(String filename) {
        return "/api/recordings/" + filename;
    }

    private Path resolveAndValidate(String filename) {
        Path resolved = storageRoot.resolve(filename).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new InvalidRecordingException("Invalid filename");
        }
        return resolved;
    }
}
