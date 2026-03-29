package com.fmr.ec3.oscc.state.service.storage;

import com.fmr.ec3.oscc.state.config.RecordingProperties;
import com.fmr.ec3.oscc.state.exception.RecordingNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
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
    void init() throws GeneralSecurityException {
        restTemplate = properties.isWebdavInsecureSsl()
                ? createInsecureRestTemplate()
                : new RestTemplate();
        String credentials = properties.getWebdavUsername() + ":" + properties.getWebdavPassword();
        authHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        log.info("WebDAV recording storage configured: {} (insecure-ssl={})",
                properties.getWebdavUrl(), properties.isWebdavInsecureSsl());
    }

    private RestTemplate createInsecureRestTemplate() throws GeneralSecurityException {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                .setSslContext(SSLContextBuilder.create()
                                        .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                        .build())
                                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .build())
                        .build())
                .build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
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
