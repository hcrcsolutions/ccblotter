package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.config.RecordingProperties;
import com.fmr.ec3.oscc.state.dto.response.RecordingDto;
import com.fmr.ec3.oscc.state.entity.RecordingEntity;
import com.fmr.ec3.oscc.state.exception.InvalidRecordingException;
import com.fmr.ec3.oscc.state.exception.RecordingNotFoundException;
import com.fmr.ec3.oscc.state.repository.RecordingRepository;
import com.fmr.ec3.oscc.state.service.storage.RecordingStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordingService {

    private final RecordingProperties properties;
    private final RecordingStorage storage;
    private final RecordingRepository repository;

    public RecordingDto store(MultipartFile file) throws IOException {
        validateFile(file);

        String filename = sanitizeFilename(file.getOriginalFilename());

        // Handle filename collision by adding timestamp prefix
        if (repository.existsById(filename)) {
            filename = System.currentTimeMillis() + "_" + filename;
        }

        byte[] bytes = file.getBytes();
        storage.store(filename, new ByteArrayInputStream(bytes), bytes.length);
        log.info("Stored recording: {} ({} bytes)", filename, bytes.length);

        RecordingEntity entity = RecordingEntity.builder()
                .filename(filename)
                .sizeBytes(bytes.length)
                .build();

        // Parse WAV header for audio metadata
        try {
            WavHeader header = readWavHeader(bytes);
            entity.setSampleRate(header.sampleRate);
            entity.setChannels(header.channels);
            entity.setBitsPerSample(header.bitsPerSample);
            entity.setDurationSeconds(header.durationSeconds);
        } catch (Exception e) {
            log.debug("Could not parse WAV header for {}: {}", filename, e.getMessage());
        }

        entity = repository.save(entity);

        return toDto(entity);
    }

    public List<RecordingDto> listAll() {
        return repository.findAllByOrderByUploadedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    public Resource loadAsResource(String filename) {
        String sanitized = sanitizeFilename(filename);
        if (!repository.existsById(sanitized)) {
            throw new RecordingNotFoundException(sanitized);
        }
        try {
            return storage.load(sanitized);
        } catch (IOException e) {
            throw new RecordingNotFoundException(sanitized);
        }
    }

    public void delete(String filename) throws IOException {
        String sanitized = sanitizeFilename(filename);
        if (!repository.existsById(sanitized)) {
            throw new RecordingNotFoundException(sanitized);
        }
        storage.delete(sanitized);
        repository.deleteById(sanitized);
        log.info("Deleted recording: {}", sanitized);
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

    private RecordingDto toDto(RecordingEntity entity) {
        return RecordingDto.builder()
                .filename(entity.getFilename())
                .sizeBytes(entity.getSizeBytes())
                .durationSeconds(entity.getDurationSeconds() != null ? entity.getDurationSeconds() : 0.0)
                .sampleRate(entity.getSampleRate() != null ? entity.getSampleRate() : 0)
                .channels(entity.getChannels() != null ? entity.getChannels() : 0)
                .bitsPerSample(entity.getBitsPerSample() != null ? entity.getBitsPerSample() : 0)
                .uploadedAt(entity.getUploadedAt())
                .url(storage.getFileUrl(entity.getFilename()))
                .build();
    }

    private WavHeader readWavHeader(byte[] data) throws IOException {
        if (data.length < 44) {
            throw new IOException("File too small to contain WAV header");
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        byte[] riff = new byte[4];
        buf.get(riff);
        if (!"RIFF".equals(new String(riff))) {
            throw new IOException("Not a valid RIFF file");
        }

        buf.getInt(); // file size

        byte[] wave = new byte[4];
        buf.get(wave);
        if (!"WAVE".equals(new String(wave))) {
            throw new IOException("Not a valid WAVE file");
        }

        // Find fmt and data chunks
        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        int dataSize = 0;

        while (buf.remaining() >= 8) {
            byte[] chunkId = new byte[4];
            buf.get(chunkId);
            int chunkSize = buf.getInt();
            String id = new String(chunkId);

            if ("fmt ".equals(id)) {
                if (chunkSize < 16 || buf.remaining() < 16) {
                    throw new IOException("Invalid fmt chunk");
                }
                buf.getShort(); // audio format
                channels = buf.getShort() & 0xFFFF;
                sampleRate = buf.getInt();
                buf.getInt(); // byte rate
                buf.getShort(); // block align
                bitsPerSample = buf.getShort() & 0xFFFF;
                // Skip rest of fmt chunk if larger than 16
                int remaining = chunkSize - 16;
                if (remaining > 0 && buf.remaining() >= remaining) {
                    buf.position(buf.position() + remaining);
                }
            } else if ("data".equals(id)) {
                dataSize = chunkSize;
                break;
            } else {
                if (buf.remaining() >= chunkSize) {
                    buf.position(buf.position() + chunkSize);
                } else {
                    break;
                }
            }
        }

        double durationSeconds = 0.0;
        if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
            int bytesPerSample = bitsPerSample / 8;
            durationSeconds = (double) dataSize / (sampleRate * channels * bytesPerSample);
        }

        return new WavHeader(channels, sampleRate, bitsPerSample, durationSeconds);
    }

    private record WavHeader(int channels, int sampleRate, int bitsPerSample, double durationSeconds) {
    }
}
