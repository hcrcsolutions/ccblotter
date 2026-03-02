package com.example.agentmonitor.service;

import com.example.agentmonitor.config.RecordingProperties;
import com.example.agentmonitor.dto.response.RecordingDto;
import com.example.agentmonitor.exception.InvalidRecordingException;
import com.example.agentmonitor.exception.RecordingNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingService {

    private final RecordingProperties properties;

    private Path storageRoot;

    @PostConstruct
    void init() throws IOException {
        storageRoot = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
        log.info("Recording storage directory: {}", storageRoot);
    }

    public RecordingDto store(MultipartFile file) throws IOException {
        validateFile(file);

        String filename = sanitizeFilename(file.getOriginalFilename());
        Path target = storageRoot.resolve(filename).normalize();

        if (!target.startsWith(storageRoot)) {
            throw new InvalidRecordingException("Invalid filename");
        }

        // Handle filename collision by adding timestamp prefix
        if (Files.exists(target)) {
            filename = System.currentTimeMillis() + "_" + filename;
            target = storageRoot.resolve(filename).normalize();
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored recording: {} ({} bytes)", filename, file.getSize());

        return buildDto(target, filename);
    }

    public List<RecordingDto> listAll() throws IOException {
        try (Stream<Path> files = Files.list(storageRoot)) {
            return files
                    .filter(p -> p.toString().toLowerCase().endsWith(".wav"))
                    .sorted(Comparator.comparing(p -> {
                        try {
                            return Files.getLastModifiedTime((Path) p).toInstant();
                        } catch (IOException e) {
                            return Instant.EPOCH;
                        }
                    }).reversed())
                    .map(p -> {
                        try {
                            return buildDto(p, p.getFileName().toString());
                        } catch (IOException e) {
                            log.warn("Failed to read WAV header for {}: {}", p.getFileName(), e.getMessage());
                            return RecordingDto.builder()
                                    .filename(p.getFileName().toString())
                                    .sizeBytes(p.toFile().length())
                                    .uploadedAt(Instant.ofEpochMilli(p.toFile().lastModified()))
                                    .url("/api/recordings/" + p.getFileName())
                                    .build();
                        }
                    })
                    .toList();
        }
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = resolveAndValidate(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RecordingNotFoundException(filename);
        } catch (IOException e) {
            throw new RecordingNotFoundException(filename);
        }
    }

    public void delete(String filename) throws IOException {
        Path file = resolveAndValidate(filename);
        if (!Files.exists(file)) {
            throw new RecordingNotFoundException(filename);
        }
        Files.delete(file);
        log.info("Deleted recording: {}", filename);
    }

    private Path resolveAndValidate(String filename) {
        String sanitized = sanitizeFilename(filename);
        Path resolved = storageRoot.resolve(sanitized).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new InvalidRecordingException("Invalid filename");
        }
        return resolved;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRecordingException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".wav")) {
            throw new InvalidRecordingException("Only .wav files are accepted");
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.equals("audio/wav")
                && !contentType.equals("audio/wave")
                && !contentType.equals("audio/x-wav")) {
            throw new InvalidRecordingException("Invalid content type: " + contentType);
        }

        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new InvalidRecordingException(
                    "File exceeds maximum size of " + (properties.getMaxFileSizeBytes() / 1024 / 1024) + " MB");
        }
    }

    private String sanitizeFilename(String original) {
        if (original == null) {
            throw new InvalidRecordingException("Filename is required");
        }
        // Strip path separators and parent references
        String sanitized = original
                .replace("..", "")
                .replace("/", "")
                .replace("\\", "")
                .replace(" ", "_");

        if (sanitized.isEmpty() || sanitized.equals(".wav")) {
            throw new InvalidRecordingException("Invalid filename");
        }
        return sanitized;
    }

    private RecordingDto buildDto(Path file, String filename) throws IOException {
        long size = Files.size(file);
        Instant uploadedAt = Files.getLastModifiedTime(file).toInstant();

        RecordingDto.RecordingDtoBuilder builder = RecordingDto.builder()
                .filename(filename)
                .sizeBytes(size)
                .uploadedAt(uploadedAt)
                .url("/api/recordings/" + filename);

        // Parse WAV header for audio metadata
        try {
            WavHeader header = readWavHeader(file);
            builder.sampleRate(header.sampleRate)
                    .channels(header.channels)
                    .bitsPerSample(header.bitsPerSample)
                    .durationSeconds(header.durationSeconds);
        } catch (Exception e) {
            log.debug("Could not parse WAV header for {}: {}", filename, e.getMessage());
        }

        return builder.build();
    }

    private WavHeader readWavHeader(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            if (raf.length() < 44) {
                throw new IOException("File too small to contain WAV header");
            }

            // RIFF header
            byte[] riff = new byte[4];
            raf.readFully(riff);
            if (!"RIFF".equals(new String(riff))) {
                throw new IOException("Not a valid RIFF file");
            }

            raf.skipBytes(4); // file size

            byte[] wave = new byte[4];
            raf.readFully(wave);
            if (!"WAVE".equals(new String(wave))) {
                throw new IOException("Not a valid WAVE file");
            }

            // Find fmt chunk
            int channels = 0;
            int sampleRate = 0;
            int bitsPerSample = 0;
            int dataSize = 0;

            while (raf.getFilePointer() < raf.length() - 8) {
                byte[] chunkId = new byte[4];
                raf.readFully(chunkId);
                int chunkSize = Integer.reverseBytes(raf.readInt());
                String id = new String(chunkId);

                if ("fmt ".equals(id)) {
                    raf.skipBytes(2); // audio format
                    channels = Short.reverseBytes(raf.readShort()) & 0xFFFF;
                    sampleRate = Integer.reverseBytes(raf.readInt());
                    raf.skipBytes(4); // byte rate
                    raf.skipBytes(2); // block align
                    bitsPerSample = Short.reverseBytes(raf.readShort()) & 0xFFFF;
                    // Skip rest of fmt chunk if larger than 16
                    int remaining = chunkSize - 16;
                    if (remaining > 0) {
                        raf.skipBytes(remaining);
                    }
                } else if ("data".equals(id)) {
                    dataSize = chunkSize;
                    break;
                } else {
                    raf.skipBytes(chunkSize);
                }
            }

            double durationSeconds = 0.0;
            if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                int bytesPerSample = bitsPerSample / 8;
                durationSeconds = (double) dataSize / (sampleRate * channels * bytesPerSample);
            }

            return new WavHeader(channels, sampleRate, bitsPerSample, durationSeconds);
        }
    }

    private record WavHeader(int channels, int sampleRate, int bitsPerSample, double durationSeconds) {
    }
}
