# audiocapture

Plain Java library for capturing caller utterances from RTP/audio streams, encoding them as WAV files, and delivering them to a shared filesystem or object store.

## Overview

Media servers embed this library to record audio during IVR CAPTURE steps. The resulting URL is passed to `IvrMessageSender.captureStepCompleted(audioUrl)` and flows through the existing Kafka/Redis/UI pipeline to render play buttons in the IVR timeline.

```
Media Server Thread           audiocapture Library              Destination
───────────────────           ──────────────────────           ───────────
                              ┌──────────────────┐
 write(rtpPacket) ──────────> │ AudioCodec.decode │ (on caller thread)
                              │      ↓            │
                              │ AudioBuffer.append │ (synchronized)
                              └──────────────────┘
                                       │
 stop() ──────────────> submit to I/O pool
                              ┌──────────────────┐
                              │ buffer.drain()    │
                              │ WavWriter.write() │──────────> FileSystem / NFS
                              │ target.deliver()  │──────────> S3 / MinIO
                              └──────────────────┘
                                       │
                              CompletableFuture<CaptureResult>
                                   ↓
                              result.getUrl() → audioUrl for IvrMessageSender
```

## Quick Start

### Dependency

```xml
<dependency>
    <groupId>com.fmr.ec3.oscc</groupId>
    <artifactId>audiocapture</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Basic Usage

```java
// 1. Create config once at startup (shared across all sessions)
AudioCaptureConfig config = AudioCaptureConfig.builder()
    .codec(new RtpPcmExtractor(new MuLawCodec()))
    .deliveryTarget(new FileSystemDeliveryTarget(
        Path.of("/mnt/nfs/recordings"), "/audio/recordings"))
    .build();

// 2. Per-capture (during IVR CAPTURE step)
AudioCaptureSession capture = AudioCaptureSession.start(config, "IVR-00042", "IVR-00042-S4");

// 3. Feed RTP packets as they arrive
capture.write(rtpPacket, 0, rtpPacket.length);

// 4. Stop and get result
CaptureResult result = capture.stop().get(5, TimeUnit.SECONDS);
String audioUrl = result.isEmpty() ? null : result.getUrl();
// → "/audio/recordings/IVR-00042-S4.wav"

// 5. Pass to IvrMessageSender
ivrSender.captureStepCompleted(sessionId, stepId,
    "Account Number", "Say your account number.",
    recognizedText, "success", 0, audioUrl, startMs, endMs, latencyMs);

// 6. Shutdown on app teardown
config.shutdown();
```

## Package Structure

```
com.fmr.ec3.oscc.audiocapture
├── AudioCaptureSession        — main entry point (start/write/stop/cancel)
├── AudioCaptureConfig         — builder-style immutable config
├── CaptureResult              — returned URL + metadata
├── CaptureException           — unchecked exception
├── codec/
│   ├── AudioCodec             — interface: decode → 16-bit PCM
│   ├── PcmCodec               — passthrough for raw PCM
│   ├── MuLawCodec             — G.711 mu-law → PCM
│   ├── ALawCodec              — G.711 A-law → PCM
│   └── RtpPcmExtractor        — strips RTP headers, delegates to inner codec
├── wav/
│   └── WavWriter              — writes 44-byte RIFF/WAVE header + PCM data
├── delivery/
│   ├── AudioDeliveryTarget    — interface: deliver WAV bytes → return URL
│   ├── FileSystemDeliveryTarget — atomic write to local/NFS path
│   ├── ObjectStoreUploader    — @FunctionalInterface injected by media server
│   └── ObjectStoreDeliveryTarget — adapter: delegates to ObjectStoreUploader
└── internal/
    ├── AudioBuffer            — thread-safe growable byte buffer
    ├── CaptureSessionWorker   — drain → encode WAV → deliver
    └── CaptureThreadPool      — shared daemon thread pool for I/O
```

## API Reference

### AudioCaptureSession

The main entry point for capturing audio.

| Method | Description |
|--------|-------------|
| `start(config, sessionId, stepId)` | Creates and returns a new capture session. |
| `write(byte[], int, int)` | Decodes audio data via the configured codec and appends PCM to the buffer. Called on the media server's thread; G.711 decoding is sub-microsecond. |
| `stop()` | Submits the buffer for WAV encoding and delivery on the I/O thread pool. Returns a `CompletableFuture<CaptureResult>`. |
| `cancel()` | Discards the buffer. Further `write()` calls are silently ignored. |

### AudioCaptureConfig

Builder-style immutable configuration. All sessions sharing a config share one thread pool.

| Setting | Default | Description |
|---------|---------|-------------|
| `codec` | *(required)* | Audio codec for decoding incoming data to PCM. |
| `deliveryTarget` | *(required)* | Where to write the finished WAV file. |
| `threadPoolSize` | 4 | Number of daemon threads for I/O operations. |
| `maxCaptureDurationSeconds` | 300 | Maximum capture duration. |
| `bufferCapacityBytes` | 320,000 | Maximum buffer size (~40 seconds at 8 kHz mono). Data beyond this limit is silently dropped. |

Call `config.shutdown()` on application teardown to clean up the thread pool.

### CaptureResult

Immutable result returned by `stop()`.

| Field | Type | Description |
|-------|------|-------------|
| `url` | `String` | URL to the delivered WAV file. `null` if empty. |
| `durationMs` | `long` | Audio duration in milliseconds. |
| `fileSizeBytes` | `long` | Total WAV file size in bytes. |
| `isEmpty` | `boolean` | `true` if no audio data was captured. |
| `sessionId` | `String` | The session ID passed to `start()`. |
| `stepId` | `String` | The step ID passed to `start()`. |

## Codecs

### Codec Selection

| Codec | Use When |
|-------|----------|
| `MuLawCodec` | North American telephony (G.711 mu-law, RTP payload type 0) |
| `ALawCodec` | European telephony (G.711 A-law, RTP payload type 8) |
| `PcmCodec` | Raw 16-bit signed PCM (already decoded) |
| `RtpPcmExtractor` | Wraps any codec above to strip RTP headers first |

### RTP Handling

`RtpPcmExtractor` parses RFC 3550 RTP headers including CSRC entries and extension headers, then delegates the payload to the inner codec:

```java
// mu-law inside RTP packets (most common)
new RtpPcmExtractor(new MuLawCodec())

// A-law inside RTP packets
new RtpPcmExtractor(new ALawCodec())

// raw PCM inside RTP packets
new RtpPcmExtractor(new PcmCodec())

// raw PCM without RTP framing
new PcmCodec()
```

## Delivery Targets

### FileSystemDeliveryTarget

Writes WAV files to a local or NFS-mounted directory using atomic rename (temp file + `Files.move(ATOMIC_MOVE)`). This prevents web servers from serving half-written files.

```java
new FileSystemDeliveryTarget(
    Path.of("/mnt/nfs/recordings"),  // directory (must exist)
    "/audio/recordings"              // URL prefix
)
// Produces: /audio/recordings/{stepId}.wav
```

### ObjectStoreDeliveryTarget

Delegates to a caller-provided `ObjectStoreUploader` lambda. This avoids pulling any SDK (AWS, MinIO, etc.) into the library itself.

```java
// AWS S3 example
new ObjectStoreDeliveryTarget(
    (key, data, contentType) -> {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("my-bucket")
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(data));
        return "https://my-bucket.s3.amazonaws.com/" + key;
    },
    "recordings/"  // key prefix
)
// Produces key: recordings/{stepId}.wav
```

### Custom Delivery Target

Implement `AudioDeliveryTarget` for any other storage backend:

```java
public class MyDeliveryTarget implements AudioDeliveryTarget {
    @Override
    public String deliver(byte[] wavData, String sessionId, String stepId)
            throws IOException {
        // write wavData somewhere, return the URL
    }
}
```

## Thread Safety

- `write()` is safe to call from the media server's RTP callback thread. Codec decoding (G.711 lookup table) takes sub-microseconds. The internal buffer is `synchronized`.
- `stop()` offloads WAV encoding and delivery to a shared daemon thread pool, so it returns immediately.
- Multiple `AudioCaptureSession` instances can run concurrently on the same config (and share its thread pool).
- Daemon threads do not prevent JVM shutdown.

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| Codec decode on caller's thread | G.711 lookup table is sub-microsecond at telephony data rates (8 KB/s). Avoids an extra buffer copy. |
| Growable array, not ring buffer | The buffer is only read once at `stop()`. A simple growable array is correct and efficient for this access pattern. |
| No AWS/MinIO SDK dependency | `ObjectStoreUploader` is a `@FunctionalInterface` — the media server injects a lambda with its own SDK. Keeps the library dependency-free. |
| Daemon threads | The I/O pool won't prevent JVM shutdown. `shutdown()` is available for clean teardown. |
| Atomic file write | Temp file + rename prevents web servers from serving half-written WAV files. |
| Config holds the thread pool | All sessions sharing a config share one pool. Single point of lifecycle management. |

## Building

```bash
# Compile
mvn compile -pl audiocapture

# Run tests (47 tests)
mvn test -pl audiocapture

# Install to local Maven repo
mvn install -pl audiocapture
```

No Spring dependencies — this is a plain Java library jar.
