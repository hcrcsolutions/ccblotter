# OSCC State Client SDKs

This directory contains client SDKs and examples for integrating infrastructure nodes (SIP servers, Media servers, SBCs, Trunks) with the OSCC State API.

## Quick Start

### Bash (for testing/scripting)

```bash
# Set environment variables
export OSCC_STATE_URL="http://localhost:8080"
export NODE_ID="sip-test-01"
export NODE_TYPE="SIP"
export DATACENTER="dc1"
export MAX_SESSIONS="100"

# Make script executable
chmod +x bash/oscc-state-client.sh

# Register and start heartbeat loop
./bash/oscc-state-client.sh loop
```

### Python

```bash
# Install dependencies
pip install requests

# Optional: for real system metrics
pip install psutil

# Run example
OSCC_STATE_URL="http://localhost:8080" python python/oscc_state_client.py
```

### Java

```java
// Add dependencies: jackson-databind, jackson-datatype-jsr310

OsccStateClient client = OsccStateClient.builder()
    .baseUrl("http://localhost:8080")
    .nodeId("sip-prod-01")
    .nodeType(NodeType.SIP)
    .hostname("sip-prod-01.example.com")
    .ipAddress("10.1.1.50")
    .datacenter("dc1")
    .maxSessions(500)
    .metricsSupplier(() -> collectMetrics())
    .sessionSupplier(() -> collectSessionInfo())
    .build();

client.start();

// On shutdown
Runtime.getRuntime().addShutdownHook(new Thread(client::close));
```

## SDK Files

| Language | Files | Description |
|----------|-------|-------------|
| Java | `java/OsccStateClient.java` | Full-featured client with builder pattern |
| Java | `java/SipServerExample.java` | Complete SIP server integration example |
| Java | `java/MediaServerExample.java` | Complete Media server integration example |
| Python | `python/oscc_state_client.py` | Python client with example usage |
| Bash | `bash/oscc-state-client.sh` | Shell script for testing and simple integrations |

## API Documentation

See [node-registration-api.md](../node-registration-api.md) for complete API documentation.

## Integration Checklist

- [ ] Choose a stable, unique `nodeId` (hostname or UUID)
- [ ] Implement metrics collection (CPU, memory, latency, jitter)
- [ ] Implement session counting with breakdown
- [ ] Call register on startup
- [ ] Send heartbeats every 10 seconds (or as specified)
- [ ] Handle heartbeat failures with retry logic
- [ ] Call deregister on graceful shutdown
- [ ] Add shutdown hook for cleanup

## Environment Variables

All SDKs support these environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `OSCC_STATE_URL` | `http://localhost:8080` | OSCC State API base URL |
| `NODE_ID` | hostname | Unique node identifier |
| `NODE_TYPE` | `MEDIA` | Node type: TRUNK, SBC, SIP, MEDIA |
| `DATACENTER` | `dc1` | Datacenter identifier |
| `REGION` | `us-east` | Geographic region |
| `MAX_SESSIONS` | `100` | Maximum concurrent sessions |
| `HEARTBEAT_INTERVAL` | `10` | Heartbeat interval in seconds |

## Testing Your Integration

1. Start the OSCC State backend:
   ```bash
   cd am-api && ./gradlew bootRun
   ```

2. Start the frontend:
   ```bash
   cd am-ui && npm run dev
   ```

3. Run your client SDK

4. Open http://localhost:3000/topology to see your node appear

5. Watch the node's health status, metrics, and session counts update in real-time
