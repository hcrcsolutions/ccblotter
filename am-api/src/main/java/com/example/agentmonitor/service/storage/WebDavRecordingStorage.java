package com.example.agentmonitor.service.storage;

import com.example.agentmonitor.config.RecordingProperties;
import com.example.agentmonitor.exception.RecordingNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "app.recordings.storage", havingValue = "webdav")
@RequiredArgsConstructor
@Slf4j
public class WebDavRecordingStorage implements RecordingStorage {

    private final RecordingProperties properties;

    private RestTemplate restTemplate;
    private String authHeader;

    @PostConstruct
    void init() {
        restTemplate = new RestTemplate();
        String credentials = properties.getWebdavUsername() + ":" + properties.getWebdavPassword();
        authHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        log.info("WebDAV recording storage configured: {}", properties.getWebdavUrl());
    }

    @Override
    public void store(String filename, InputStream inputStream, long size) throws IOException {
        String url = properties.getWebdavUrl() + "/" + filename;
        byte[] body = inputStream.readAllBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.setContentLength(body.length);

        HttpEntity<byte[]> request = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.PUT, request, Void.class);
        log.info("Stored recording via WebDAV: {} ({} bytes)", filename, size);
    }

    @Override
    public Resource load(String filename) throws IOException {
        String url = properties.getWebdavUrl() + "/" + filename;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);

        try {
            ResponseEntity<Resource> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Resource.class);
            if (response.getBody() == null) {
                throw new RecordingNotFoundException(filename);
            }
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new RecordingNotFoundException(filename);
        }
    }

    @Override
    public void delete(String filename) throws IOException {
        String url = properties.getWebdavUrl() + "/" + filename;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("Deleted recording via WebDAV: {}", filename);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RecordingNotFoundException(filename);
        }
    }

    @Override
    public boolean exists(String filename) throws IOException {
        String url = properties.getWebdavUrl() + "/" + filename;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);

        try {
            restTemplate.exchange(url, HttpMethod.HEAD, new HttpEntity<>(headers), Void.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    @Override
    public String getFileUrl(String filename) {
        return properties.getBaseUrl() + "/" + filename;
    }
}
