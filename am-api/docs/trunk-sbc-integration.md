# Trunk and SBC Integration Guide

This document describes how to integrate Trunks (carrier connections) and SBCs (Session Border Controllers) with the OSCC State API for topology visualization and health monitoring.

## Overview

Unlike SIP and Media servers which typically run custom application code, Trunks and SBCs are often:
- Managed by external carriers
- Hardware appliances with limited extensibility
- Network devices monitored via SNMP or proprietary APIs

The OSCC State API supports multiple integration patterns to accommodate these constraints.

---

## Integration Options

| Option | Best For | Complexity | Real-time |
|--------|----------|------------|-----------|
| **1. Self-Registration** | Software SBCs, virtualized trunks | Low | Yes |
| **2. Proxy Registration** | Carrier-managed trunks, legacy SBCs | Medium | Yes |
| **3. Polling Adapter** | SNMP-enabled devices, existing NMS | Medium | Near real-time |
| **4. Static Configuration** | Stable infrastructure, manual tracking | Low | No |

---

## Option 1: Self-Registration (SDK Client)

Use this when the trunk or SBC can run custom code (e.g., software SBC, virtualized trunk gateway).

### Trunk Example

```java
OsccStateClient client = OsccStateClient.builder()
    .baseUrl("http://oscc-state:8080")
    .nodeId("trunk-carrier-a-primary")
    .nodeType(NodeType.TRUNK)
    .hostname("trunk-gw-01.carrier-a.net")
    .ipAddress("203.0.113.10")
    .datacenter("dc1")
    .region("us-east")
    .maxSessions(1000)
    .metadata(Map.of(
        "carrierName", "Carrier A",
        "trunkGroup", "primary",
        "circuitId", "CKT-12345",
        "contractSLA", "99.99%"
    ))
    .metricsSupplier(() -> collectTrunkMetrics())
    .build();

client.start();

// Declare connections to SBCs this trunk routes to
client.addConnectionsBulk(List.of("sbc-1", "sbc-2"));
```

### SBC Example

```java
OsccStateClient client = OsccStateClient.builder()
    .baseUrl("http://oscc-state:8080")
    .nodeId("sbc-1")
    .nodeType(NodeType.SBC)
    .hostname("sbc-01.dc1.example.com")
    .ipAddress("10.1.1.10")
    .datacenter("dc1")
    .region("us-east")
    .maxSessions(2000)
    .metadata(Map.of(
        "vendor", "AudioCodes",
        "model", "Mediant 4000",
        "firmware", "7.4.300"
    ))
    .metricsSupplier(() -> collectSbcMetrics())
    .build();

client.start();

// Declare connections to downstream SIP servers
client.addConnectionsBulk(List.of("sip-1", "sip-2"));
```

---

## Option 2: Proxy Registration

Use this when devices cannot run SDK code. A management service registers and maintains heartbeats on their behalf.

### Architecture

```
┌─────────────┐     ┌─────────────────────┐     ┌─────────────┐
│   Trunk     │────►│  Trunk/SBC Proxy    │────►│ OSCC State  │
│   (PSTN)    │     │  Service            │     │ API         │
└─────────────┘     │                     │     └─────────────┘
                    │ - Registers nodes   │
┌─────────────┐     │ - Sends heartbeats  │
│   SBC       │────►│ - Polls device APIs │
│ (Appliance) │     │ - Updates topology  │
└─────────────┘     └─────────────────────┘
```

### Proxy Service Implementation

```java
@Service
@Slf4j
public class TrunkSbcProxyService {

    private final RestTemplate restTemplate;
    private final String osccStateUrl;
    private final List<DeviceConfig> managedDevices;

    /**
     * Register all managed trunks and SBCs on startup.
     */
    @PostConstruct
    public void registerDevices() {
        for (DeviceConfig device : managedDevices) {
            NodeRegistrationRequest request = NodeRegistrationRequest.builder()
                .id(device.getId())
                .type(device.getType())  // TRUNK or SBC
                .hostname(device.getHostname())
                .ipAddress(device.getIpAddress())
                .datacenter(device.getDatacenter())
                .region(device.getRegion())
                .maxSessions(device.getMaxSessions())
                .metadata(Map.of(
                    "carrierName", device.getCarrierName(),
                    "trunkGroup", device.getTrunkGroup(),
                    "managedBy", "trunk-sbc-proxy"
                ))
                .build();

            restTemplate.postForEntity(
                osccStateUrl + "/api/v1/nodes/register",
                request,
                NodeRegistrationResponse.class
            );

            log.info("Registered {} device: {}", device.getType(), device.getId());
        }

        // Register topology connections
        registerConnections();
    }

    /**
     * Send heartbeats every 10 seconds for all managed devices.
     */
    @Scheduled(fixedRate = 10000)
    public void sendHeartbeats() {
        for (DeviceConfig device : managedDevices) {
            try {
                DeviceStatus status = pollDeviceStatus(device);

                HeartbeatRequest heartbeat = HeartbeatRequest.builder()
                    .activeSessions(status.getActiveSessions())
                    .metrics(NodeMetrics.builder()
                        .cpuPercent(status.getCpuPercent())
                        .memoryPercent(status.getMemoryPercent())
                        .latencyMs(status.getLatencyMs())
                        .jitterMs(status.getJitterMs())
                        .packetLossPercent(status.getPacketLoss())
                        .errorRate(status.getErrorRate())
                        .build())
                    .build();

                restTemplate.postForEntity(
                    osccStateUrl + "/api/v1/nodes/" + device.getId() + "/heartbeat",
                    heartbeat,
                    HeartbeatResponse.class
                );

            } catch (Exception e) {
                log.warn("Failed to send heartbeat for {}: {}", device.getId(), e.getMessage());
            }
        }
    }

    /**
     * Poll device status via vendor-specific API or SNMP.
     */
    private DeviceStatus pollDeviceStatus(DeviceConfig device) {
        // Implementation depends on device type:
        // - Carrier API for trunk status
        // - SBC REST API or SNMP for SBC metrics
        return deviceApiClient.getStatus(device);
    }

    /**
     * Register topology connections based on configuration.
     */
    private void registerConnections() {
        // Trunks connect to SBCs
        for (DeviceConfig trunk : getTrunks()) {
            restTemplate.put(
                osccStateUrl + "/api/v1/nodes/" + trunk.getId() + "/connections/bulk",
                new BulkConnectionRequest(trunk.getConnectedSbcIds()),
                BulkConnectionResponse.class
            );
        }

        // SBCs connect to SIP servers
        for (DeviceConfig sbc : getSbcs()) {
            restTemplate.put(
                osccStateUrl + "/api/v1/nodes/" + sbc.getId() + "/connections/bulk",
                new BulkConnectionRequest(sbc.getConnectedSipIds()),
                BulkConnectionResponse.class
            );
        }
    }
}
```

### Configuration File

```yaml
# trunk-sbc-proxy-config.yaml
devices:
  # Carrier A - Primary Trunk
  - id: trunk-carrier-a-primary
    type: TRUNK
    hostname: trunk-gw-01.carrier-a.net
    ipAddress: 203.0.113.10
    datacenter: dc1
    region: us-east
    maxSessions: 1000
    carrierName: Carrier A
    trunkGroup: primary
    connectedSbcIds:
      - sbc-1
      - sbc-2
    pollEndpoint: https://api.carrier-a.net/trunk/status
    pollCredentials: ${CARRIER_A_API_KEY}

  # Carrier A - Backup Trunk
  - id: trunk-carrier-a-backup
    type: TRUNK
    hostname: trunk-gw-02.carrier-a.net
    ipAddress: 203.0.113.11
    datacenter: dc2
    region: us-east
    maxSessions: 1000
    carrierName: Carrier A
    trunkGroup: backup
    connectedSbcIds:
      - sbc-3
      - sbc-4

  # AudioCodes SBC
  - id: sbc-1
    type: SBC
    hostname: sbc-01.dc1.example.com
    ipAddress: 10.1.1.10
    datacenter: dc1
    region: us-east
    maxSessions: 2000
    connectedSipIds:
      - sip-1
    pollEndpoint: https://10.1.1.10/api/v1/status
    pollMethod: REST
    vendor: AudioCodes

  # Oracle SBC (SNMP)
  - id: sbc-2
    type: SBC
    hostname: sbc-02.dc1.example.com
    ipAddress: 10.1.1.11
    datacenter: dc1
    region: us-east
    maxSessions: 3000
    connectedSipIds:
      - sip-1
    pollMethod: SNMP
    snmpCommunity: ${SBC_SNMP_COMMUNITY}
    vendor: Oracle
```

---

## Option 3: Polling Adapter (SNMP/NMS Integration)

Use this when devices expose SNMP or you have an existing Network Management System (NMS).

### SNMP Polling Example

```java
@Service
public class SnmpPollingAdapter {

    private final Snmp snmp;
    private final RestTemplate osccStateClient;

    /**
     * Poll SBC metrics via SNMP and forward to OSCC State.
     */
    @Scheduled(fixedRate = 10000)
    public void pollAndForward() {
        for (SnmpTarget target : snmpTargets) {
            try {
                // Poll standard MIBs
                int activeSessions = snmpGet(target, OID_ACTIVE_SESSIONS);
                int cpuPercent = snmpGet(target, OID_CPU_USAGE);
                int memoryPercent = snmpGet(target, OID_MEMORY_USAGE);

                // Poll vendor-specific MIBs for VoIP metrics
                int latencyMs = snmpGet(target, target.getVendorOid("latency"));
                int jitterMs = snmpGet(target, target.getVendorOid("jitter"));

                HeartbeatRequest heartbeat = HeartbeatRequest.builder()
                    .activeSessions(activeSessions)
                    .metrics(NodeMetrics.builder()
                        .cpuPercent(cpuPercent)
                        .memoryPercent(memoryPercent)
                        .latencyMs(latencyMs)
                        .jitterMs(jitterMs)
                        .build())
                    .build();

                osccStateClient.postForEntity(
                    "/api/v1/nodes/" + target.getNodeId() + "/heartbeat",
                    heartbeat,
                    HeartbeatResponse.class
                );

            } catch (Exception e) {
                log.warn("SNMP poll failed for {}: {}", target.getNodeId(), e.getMessage());
            }
        }
    }
}
```

### Common SNMP OIDs for SBCs

| Metric | Standard OID | Notes |
|--------|--------------|-------|
| CPU Usage | 1.3.6.1.4.1.2021.11.9.0 | UCD-SNMP-MIB |
| Memory Usage | 1.3.6.1.4.1.2021.4.6.0 | UCD-SNMP-MIB |
| Active Sessions | Vendor-specific | See vendor documentation |
| Call Attempts | Vendor-specific | AudioCodes, Oracle, etc. |

---

## Option 4: Static Configuration

Use this for stable infrastructure where manual tracking is acceptable.

### Register via API (one-time)

```bash
# Register a trunk
curl -X POST http://oscc-state:8080/api/v1/nodes/register \
  -H "Content-Type: application/json" \
  -d '{
    "id": "trunk-carrier-a-primary",
    "type": "TRUNK",
    "hostname": "trunk-gw-01.carrier-a.net",
    "ipAddress": "203.0.113.10",
    "datacenter": "dc1",
    "region": "us-east",
    "maxSessions": 1000,
    "metadata": {
      "carrierName": "Carrier A",
      "trunkGroup": "primary",
      "registrationMode": "static"
    }
  }'

# Register an SBC
curl -X POST http://oscc-state:8080/api/v1/nodes/register \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sbc-1",
    "type": "SBC",
    "hostname": "sbc-01.dc1.example.com",
    "ipAddress": "10.1.1.10",
    "datacenter": "dc1",
    "region": "us-east",
    "maxSessions": 2000,
    "metadata": {
      "vendor": "AudioCodes",
      "registrationMode": "static"
    }
  }'

# Register connections
curl -X PUT http://oscc-state:8080/api/v1/nodes/trunk-carrier-a-primary/connections/bulk \
  -H "Content-Type: application/json" \
  -d '{"targetIds": ["sbc-1", "sbc-2"]}'

curl -X PUT http://oscc-state:8080/api/v1/nodes/sbc-1/connections/bulk \
  -H "Content-Type: application/json" \
  -d '{"targetIds": ["sip-1"]}'
```

### Simulated Heartbeats (Optional)

For static nodes, you can configure the OSCC State API to:
1. Never mark them as unhealthy (infinite timeout)
2. Display them with a "Static" indicator
3. Accept manual status updates via admin API

```yaml
# application.yaml
app:
  static-nodes:
    enabled: true
    node-ids:
      - trunk-carrier-a-primary
      - trunk-carrier-a-backup
      - sbc-1
      - sbc-2
    default-status: HEALTHY
    skip-health-check: true
```

---

## Topology Connection Patterns

### Standard Flow

```
TRUNK → SBC → SIP → MEDIA

Connections declared by upstream nodes:
- TRUNK declares connections to SBCs
- SBC declares connections to SIPs
- SIP declares connections to MEDIA
```

### Multi-Carrier Setup

```
┌─────────────────┐
│   Carrier A     │
│ ┌─────────────┐ │     ┌───────┐
│ │ Primary     │─┼────►│ SBC-1 │──┐
│ └─────────────┘ │     └───────┘  │
│ ┌─────────────┐ │     ┌───────┐  │    ┌───────┐
│ │ Backup      │─┼────►│ SBC-2 │──┼───►│ SIP-1 │
│ └─────────────┘ │     └───────┘  │    └───────┘
└─────────────────┘                │
                                   │
┌─────────────────┐                │
│   Carrier B     │                │
│ ┌─────────────┐ │     ┌───────┐  │    ┌───────┐
│ │ Primary     │─┼────►│ SBC-3 │──┼───►│ SIP-2 │
│ └─────────────┘ │     └───────┘  │    └───────┘
│ ┌─────────────┐ │     ┌───────┐  │
│ │ Overflow    │─┼────►│ SBC-4 │──┘
│ └─────────────┘ │     └───────┘
└─────────────────┘
```

### Connection Registration for Above

```bash
# Carrier A trunks
PUT /api/v1/nodes/trunk-carrier-a-primary/connections  {"targetIds": ["sbc-1"]}
PUT /api/v1/nodes/trunk-carrier-a-backup/connections   {"targetIds": ["sbc-2"]}

# Carrier B trunks
PUT /api/v1/nodes/trunk-carrier-b-primary/connections  {"targetIds": ["sbc-3"]}
PUT /api/v1/nodes/trunk-carrier-b-overflow/connections {"targetIds": ["sbc-4"]}

# SBCs to SIPs (cross-connected for redundancy)
PUT /api/v1/nodes/sbc-1/connections {"targetIds": ["sip-1"]}
PUT /api/v1/nodes/sbc-2/connections {"targetIds": ["sip-1"]}
PUT /api/v1/nodes/sbc-3/connections {"targetIds": ["sip-2"]}
PUT /api/v1/nodes/sbc-4/connections {"targetIds": ["sip-1", "sip-2"]}
```

---

## Trunk-Specific Metadata

| Field | Description | Example |
|-------|-------------|---------|
| `carrierName` | Carrier/provider name | "Carrier A", "Twilio" |
| `trunkGroup` | Trunk classification | "primary", "backup", "overflow" |
| `circuitId` | Carrier circuit identifier | "CKT-12345" |
| `contractSLA` | Contracted SLA | "99.99%" |
| `billingCode` | Cost center/billing code | "CC-VOICE-001" |
| `maxCPS` | Max calls per second | 50 |

## SBC-Specific Metadata

| Field | Description | Example |
|-------|-------------|---------|
| `vendor` | SBC vendor | "AudioCodes", "Oracle", "Ribbon" |
| `model` | Hardware/software model | "Mediant 4000", "SBC 7000" |
| `firmware` | Firmware version | "7.4.300" |
| `licenseCapacity` | Licensed session capacity | 5000 |
| `haRole` | High availability role | "active", "standby" |
| `mediaBypass` | Media bypass enabled | true/false |

---

## Choosing an Integration Option

```
                                    ┌─────────────────────┐
                                    │ Can device run      │
                                    │ custom code/SDK?    │
                                    └──────────┬──────────┘
                                               │
                              ┌────────────────┴────────────────┐
                              │                                 │
                             Yes                                No
                              │                                 │
                              ▼                                 ▼
                    ┌─────────────────┐              ┌─────────────────────┐
                    │ Option 1:       │              │ Does device expose  │
                    │ Self-Registration│              │ API/SNMP?           │
                    └─────────────────┘              └──────────┬──────────┘
                                                               │
                                              ┌────────────────┴────────────────┐
                                              │                                 │
                                             Yes                                No
                                              │                                 │
                                              ▼                                 ▼
                                   ┌─────────────────────┐           ┌─────────────────┐
                                   │ Need real-time      │           │ Option 4:       │
                                   │ updates?            │           │ Static Config   │
                                   └──────────┬──────────┘           └─────────────────┘
                                              │
                             ┌────────────────┴────────────────┐
                             │                                 │
                            Yes                                No
                             │                                 │
                             ▼                                 ▼
                  ┌─────────────────────┐           ┌─────────────────┐
                  │ Option 2 or 3:      │           │ Option 4:       │
                  │ Proxy/Polling       │           │ Static Config   │
                  └─────────────────────┘           └─────────────────┘
```
