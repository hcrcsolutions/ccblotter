# Infrastructure State Architecture

## Overview

This document describes the architecture for managing infrastructure topology state across multiple Kubernetes pods. The system supports:

- **Self-registration** of SIP and Media servers via REST API
- **Heartbeat-based liveness tracking** (10s interval, 30s timeout)
- **Dynamic edge management** where SIP servers announce their media connections
- **Consistent state** across all oscc-state pods via PostgreSQL + Redis

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           KUBERNETES CLUSTER                                 │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                       │
│  │ Media Pod 1  │  │ Media Pod 2  │  │ Media Pod N  │  (auto-scaling)       │
│  │              │  │              │  │              │                       │
│  │ On start:    │  │ On start:    │  │ On start:    │                       │
│  │  POST /register  POST /register   POST /register │                       │
│  │              │  │              │  │              │                       │
│  │ Every 10s:   │  │ Every 10s:   │  │ Every 10s:   │                       │
│  │  POST /heartbeat POST /heartbeat  POST /heartbeat│                       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                       │
│         │                 │                 │                                │
│         └─────────────────┼─────────────────┘                                │
│                           │                                                  │
│                           ▼                                                  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                            AM-API PODS                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │ NodeRegistrationController                                       │  │  │
│  │  │   POST   /api/v1/nodes/register                                 │  │  │
│  │  │   POST   /api/v1/nodes/{id}/heartbeat                           │  │  │
│  │  │   DELETE /api/v1/nodes/{id}                                     │  │  │
│  │  │   GET    /api/v1/nodes?type=MEDIA&datacenter=dc1&status=HEALTHY │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │ EdgeManagementController                                         │  │  │
│  │  │   POST   /api/v1/nodes/{sipId}/connections                      │  │  │
│  │  │   DELETE /api/v1/nodes/{sipId}/connections/{targetId}           │  │  │
│  │  │   PUT    /api/v1/nodes/{sipId}/connections/bulk                 │  │  │
│  │  │   DELETE /api/v1/nodes/{sipId}/connections/bulk                 │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │ NodeHealthWatchdog (scheduled every 15s)                         │  │  │
│  │  │   - Find nodes with lastHeartbeat > 30s ago                     │  │  │
│  │  │   - Mark as UNHEALTHY                                           │  │  │
│  │  │   - Broadcast topology change via WebSocket                     │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                           │                                                  │
│         ┌─────────────────┼─────────────────┐                                │
│         ▼                 ▼                 ▼                                │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                        │
│  │ PostgreSQL  │   │    Redis    │   │   Kafka     │                        │
│  │ (topology)  │   │  (dynamic)  │   │ (optional)  │                        │
│  └─────────────┘   └─────────────┘   └─────────────┘                        │
│                           │                                                  │
│                           ▼                                                  │
│  ┌──────────────┐  ┌──────────────┐                                         │
│  │  SIP Pod 1   │  │  SIP Pod 2   │                                         │
│  │              │  │              │                                         │
│  │ On start:    │  │ On start:    │                                         │
│  │  POST /register  POST /register│                                         │
│  │              │  │              │                                         │
│  │ Discover:    │  │ Discover:    │                                         │
│  │  GET /nodes?type=MEDIA&dc=dc1  │                                         │
│  │              │  │              │                                         │
│  │ Connect:     │  │ Connect:     │                                         │
│  │  PUT /connections/bulk         │                                         │
│  │              │  │              │                                         │
│  │ Every 10s:   │  │ Every 10s:   │                                         │
│  │  POST /heartbeat               │                                         │
│  └──────────────┘  └──────────────┘                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Node Lifecycle

```
┌─────────────┐     POST /register      ┌─────────────┐
│   UNKNOWN   │ ───────────────────────►│   HEALTHY   │
└─────────────┘                         └──────┬──────┘
                                               │
                                               │ heartbeat every 10s
                                               │
                                               ▼
                                        ┌─────────────┐
                              ┌────────►│   HEALTHY   │◄────────┐
                              │         └──────┬──────┘         │
                              │                │                │
                         heartbeat        no heartbeat      heartbeat
                         received          for 30s          received
                              │                │                │
                              │                ▼                │
                              │         ┌─────────────┐         │
                              └─────────│  UNHEALTHY  │─────────┘
                                        └──────┬──────┘
                                               │
                                               │ no heartbeat for 5 min
                                               │ (configurable)
                                               ▼
                                        ┌─────────────┐
                                        │   REMOVED   │
                                        │ (from pool) │
                                        └─────────────┘
```

### Timing Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| Heartbeat interval | 10 seconds | How often nodes send heartbeat |
| Unhealthy threshold | 30 seconds | Mark unhealthy if no heartbeat |
| Removal threshold | 5 minutes | Remove from topology (configurable) |
| Watchdog interval | 15 seconds | How often oscc-state checks for stale nodes |

---

## REST API Specification

### Node Registration

#### Register a Node

```http
POST /api/v1/nodes/register
Content-Type: application/json

{
  "id": "media-42",
  "type": "MEDIA",
  "hostname": "media-042.dc1.example.com",
  "ipAddress": "10.1.2.42",
  "datacenter": "dc1",
  "region": "us-east",
  "maxSessions": 100,
  "metadata": {
    "podName": "media-server-7b8c9d-x2k4m",
    "version": "2.3.1"
  }
}
```

**Response (201 Created):**
```json
{
  "id": "media-42",
  "status": "HEALTHY",
  "registeredAt": "2024-02-01T12:00:00Z",
  "heartbeatIntervalSeconds": 10,
  "heartbeatTimeoutSeconds": 30
}
```

**Response (200 OK if already registered - re-registration):**
```json
{
  "id": "media-42",
  "status": "HEALTHY",
  "registeredAt": "2024-02-01T11:55:00Z",
  "reregisteredAt": "2024-02-01T12:00:00Z",
  "message": "Node re-registered successfully"
}
```

#### Send Heartbeat

```http
POST /api/v1/nodes/{nodeId}/heartbeat
Content-Type: application/json

{
  "activeSessions": 75,
  "metrics": {
    "cpuPercent": 45.5,
    "memoryPercent": 62.3,
    "latencyMs": 23,
    "jitterMs": 5,
    "packetLossPercent": 0.1,
    "errorRate": 0.05,
    "mosScore": 42
  },
  "sessionBreakdown": {
    "inboundSessions": 50,
    "outboundSessions": 25,
    "ivrSessions": 10,
    "queueSessions": 15,
    "agentSessions": 45,
    "onHoldSessions": 5
  }
}
```

**Response (200 OK):**
```json
{
  "id": "media-42",
  "status": "HEALTHY",
  "lastHeartbeat": "2024-02-01T12:00:30Z",
  "nextHeartbeatDue": "2024-02-01T12:01:00Z"
}
```

**Response (404 Not Found - node not registered):**
```json
{
  "error": "NODE_NOT_FOUND",
  "message": "Node media-42 is not registered. Please register first.",
  "registrationUrl": "/api/v1/nodes/register"
}
```

#### Deregister a Node (Graceful Shutdown)

```http
DELETE /api/v1/nodes/{nodeId}
```

**Response (204 No Content)**

#### Query Available Nodes (Service Discovery)

```http
GET /api/v1/nodes?type=MEDIA&datacenter=dc1&status=HEALTHY
```

**Response (200 OK):**
```json
{
  "nodes": [
    {
      "id": "media-42",
      "type": "MEDIA",
      "hostname": "media-042.dc1.example.com",
      "ipAddress": "10.1.2.42",
      "datacenter": "dc1",
      "region": "us-east",
      "status": "HEALTHY",
      "maxSessions": 100,
      "activeSessions": 75,
      "availableCapacity": 25,
      "lastHeartbeat": "2024-02-01T12:00:30Z"
    },
    {
      "id": "media-43",
      "type": "MEDIA",
      "hostname": "media-043.dc1.example.com",
      "ipAddress": "10.1.2.43",
      "datacenter": "dc1",
      "region": "us-east",
      "status": "HEALTHY",
      "maxSessions": 100,
      "activeSessions": 30,
      "availableCapacity": 70,
      "lastHeartbeat": "2024-02-01T12:00:28Z"
    }
  ],
  "total": 2,
  "filter": {
    "type": "MEDIA",
    "datacenter": "dc1",
    "status": "HEALTHY"
  }
}
```

---

### Edge Management (SIP → Media Connections)

#### Add Single Connection

```http
POST /api/v1/nodes/{sipId}/connections
Content-Type: application/json

{
  "targetId": "media-42"
}
```

**Response (201 Created):**
```json
{
  "edgeId": "e-sip-1-media-42",
  "sourceId": "sip-1",
  "targetId": "media-42",
  "createdAt": "2024-02-01T12:00:00Z"
}
```

#### Remove Single Connection

```http
DELETE /api/v1/nodes/{sipId}/connections/{targetId}
```

**Response (204 No Content)**

#### Add Multiple Connections (Bulk)

```http
PUT /api/v1/nodes/{sipId}/connections/bulk
Content-Type: application/json

{
  "targetIds": ["media-42", "media-43", "media-44", "media-45"]
}
```

**Response (200 OK):**
```json
{
  "sourceId": "sip-1",
  "addedCount": 2,
  "existingCount": 2,
  "failedCount": 0,
  "addedTargets": ["media-44", "media-45"],
  "existingTargets": ["media-42", "media-43"],
  "failedTargets": []
}
```

**Response with partial failure (200 OK):**
```json
{
  "sourceId": "sip-1",
  "addedCount": 2,
  "existingCount": 0,
  "failedCount": 2,
  "addedTargets": ["media-42", "media-43"],
  "existingTargets": [],
  "failedTargets": ["media-99", "media-100"]
}
```

#### Remove Multiple Connections (Bulk)

```http
DELETE /api/v1/nodes/{sipId}/connections/bulk
Content-Type: application/json

{
  "targetIds": ["media-42", "media-43"]
}
```

**Response (200 OK):** Same format as bulk add response.

#### Replace All Connections (Set)

```http
PUT /api/v1/nodes/{sipId}/connections
Content-Type: application/json

{
  "targetIds": ["media-50", "media-51", "media-52"]
}
```

This removes all existing connections and creates new ones.

**Response (200 OK):**
```json
{
  "sourceId": "sip-1",
  "previousConnections": ["media-42", "media-43", "media-44"],
  "currentConnections": ["media-50", "media-51", "media-52"],
  "added": ["media-50", "media-51", "media-52"],
  "removed": ["media-42", "media-43", "media-44"]
}
```

#### Get Current Connections

```http
GET /api/v1/nodes/{sipId}/connections
```

**Response (200 OK):**
```json
{
  "sourceId": "sip-1",
  "connections": [
    {
      "targetId": "media-42",
      "targetHostname": "media-042.dc1.example.com",
      "targetStatus": "HEALTHY",
      "connectedAt": "2024-02-01T12:00:00Z"
    },
    {
      "targetId": "media-43",
      "targetHostname": "media-043.dc1.example.com",
      "targetStatus": "HEALTHY",
      "connectedAt": "2024-02-01T12:00:00Z"
    }
  ],
  "total": 2
}
```

---

## Data Model

### PostgreSQL Schema

```sql
-- Datacenter reference table
CREATE TABLE datacenters (
    id VARCHAR(50) PRIMARY KEY,
    region VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Infrastructure nodes (Trunks, SBCs, SIPs, Media servers)
CREATE TABLE nodes (
    id VARCHAR(50) PRIMARY KEY,
    type VARCHAR(10) NOT NULL CHECK (type IN ('TRUNK', 'SBC', 'SIP', 'MEDIA')),
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    datacenter_id VARCHAR(50) NOT NULL REFERENCES datacenters(id),
    max_sessions INT NOT NULL,
    carrier_name VARCHAR(100),          -- TRUNK only
    trunk_group VARCHAR(50),            -- TRUNK only
    maintenance_mode BOOLEAN DEFAULT FALSE,

    -- Registration tracking
    registration_source VARCHAR(20) NOT NULL DEFAULT 'SELF',  -- SELF, KAFKA, ADMIN
    registered_at TIMESTAMP NOT NULL,
    last_heartbeat_at TIMESTAMP,
    health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',

    -- Metadata (JSON for flexibility)
    metadata JSONB,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Dynamic edges (SIP announces connections to Media)
CREATE TABLE edges (
    id VARCHAR(100) PRIMARY KEY,
    source_id VARCHAR(50) NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    target_id VARCHAR(50) NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    bandwidth_mbps INT,

    -- Edge metadata
    created_by VARCHAR(50),             -- Which node created this edge
    created_at TIMESTAMP DEFAULT NOW(),

    UNIQUE(source_id, target_id)
);

-- Indexes
CREATE INDEX idx_nodes_datacenter ON nodes(datacenter_id);
CREATE INDEX idx_nodes_type ON nodes(type);
CREATE INDEX idx_nodes_health ON nodes(health_status);
CREATE INDEX idx_nodes_heartbeat ON nodes(last_heartbeat_at);
CREATE INDEX idx_edges_source ON edges(source_id);
CREATE INDEX idx_edges_target ON edges(target_id);
```

### Redis Data Structures

```redis
# ============================================================================
# NODE LIVENESS (TTL-based)
# ============================================================================

# Node heartbeat timestamp (for distributed consistency)
# TTL = 90 seconds (1.5x heartbeat timeout for buffer)
SETEX infra:node:media-42:heartbeat 90 "2024-02-01T12:00:30Z"

# ============================================================================
# SESSION COUNTS (Atomic updates)
# ============================================================================

# Session counts per node (Hash)
HSET infra:node:media-42:sessions \
    active 75 \
    inbound 50 \
    outbound 25 \
    ivr 10 \
    queue 15 \
    agent 45 \
    onHold 5

# ============================================================================
# METRICS (Point-in-time)
# ============================================================================

# Current metrics per node (Hash)
HSET infra:node:media-42:metrics \
    cpu 45.5 \
    memory 62.3 \
    latency 23 \
    jitter 5 \
    packetLoss 0.1 \
    errorRate 0.05 \
    mos 42

# ============================================================================
# TREND HISTORY (Sliding window)
# ============================================================================

# Trend data stream (auto-trimmed to ~60 entries = 5 minutes)
XADD infra:node:media-42:trends MAXLEN ~ 60 * \
    active 75 \
    cpu 45.5

# ============================================================================
# TOPOLOGY CACHE INVALIDATION
# ============================================================================

# Version string for cache invalidation (updated on topology change)
SET infra:topology:version "2024-02-01T12:00:30Z"

# ============================================================================
# DISTRIBUTED LOCKING (for watchdog leader election)
# ============================================================================

# Only one pod runs the health watchdog at a time
SET infra:watchdog:lock "pod-abc123" NX EX 20
```

---

## Implementation Plan

### Phase 1: Dependencies and Configuration

#### 1.1 Update `pom.xml`

```xml
<dependencies>
    <!-- Web & WebSocket -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Migrations -->
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>

    <!-- Kafka (optional, for external events) -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 1.2 Configuration Properties

**`application.properties`**:
```properties
# Server
server.port=8080
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}

# PostgreSQL
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:agentmonitor}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# Liquibase
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml

# Redis
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.timeout=5000ms

# Kafka (optional)
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=oscc-state-infrastructure
spring.kafka.consumer.auto-offset-reset=earliest

# Node Health Settings
app.node.heartbeat-interval-seconds=10
app.node.unhealthy-threshold-seconds=30
app.node.removal-threshold-seconds=300
app.node.watchdog-interval-seconds=15

# Mock Data (dev only)
app.mock-data.enabled=${MOCK_DATA_ENABLED:false}

# WebSocket
spring.websocket.message-broker.stomp.endpoint=/ws
spring.websocket.message-broker.stomp.broker-prefix=/topic
spring.websocket.message-broker.stomp.application-prefix=/app

# Actuator
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.health.redis.enabled=true
management.health.db.enabled=true
```

**`application-dev.properties`**:
```properties
# Enable mock data generator for development
app.mock-data.enabled=true

# Development logging
logging.level.com.fmr.ec3.oscc.state=DEBUG
logging.level.org.springframework.web=DEBUG
```

---

### Phase 2: Core Infrastructure

#### File Structure

```
oscc-state/src/main/java/com/example/osccstate/
├── AgentMonitorApplication.java
├── config/
│   ├── WebSocketConfig.java (existing)
│   ├── RedisConfig.java
│   └── NodeHealthProperties.java
├── controller/
│   ├── NodeRegistrationController.java
│   ├── EdgeManagementController.java
│   └── InfrastructureController.java (existing)
├── entity/
│   ├── DatacenterEntity.java
│   ├── NodeEntity.java
│   └── EdgeEntity.java
├── repository/
│   ├── DatacenterRepository.java
│   ├── NodeRepository.java
│   └── EdgeRepository.java
├── service/
│   ├── NodeRegistrationService.java
│   ├── EdgeManagementService.java
│   ├── NodeHealthWatchdogService.java
│   ├── RedisStateService.java
│   ├── TopologyCacheService.java
│   ├── InfrastructureService.java (rewritten)
│   └── MockDataGeneratorService.java
├── dto/
│   ├── request/
│   │   ├── NodeRegistrationRequest.java
│   │   ├── HeartbeatRequest.java
│   │   ├── AddConnectionRequest.java
│   │   └── BulkConnectionRequest.java
│   └── response/
│       ├── NodeRegistrationResponse.java
│       ├── HeartbeatResponse.java
│       ├── NodeQueryResponse.java
│       └── ConnectionResponse.java
├── model/
│   ├── InfraServerType.java (existing)
│   ├── ServerHealthStatus.java (existing)
│   ├── NodeMetrics.java (existing)
│   ├── SessionBreakdown.java (existing)
│   └── ... (other existing models)
├── exception/
│   ├── NodeNotFoundException.java
│   ├── NodeAlreadyRegisteredException.java
│   └── InvalidConnectionException.java
└── redis/
    └── RedisKeys.java
```

---

### Phase 3: Implementation Details

#### 3.1 Node Registration Controller

```java
@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
@Slf4j
@Validated
public class NodeRegistrationController {

    private final NodeRegistrationService registrationService;
    private final NodeRepository nodeRepository;

    @PostMapping("/register")
    public ResponseEntity<NodeRegistrationResponse> registerNode(
            @Valid @RequestBody NodeRegistrationRequest request) {

        log.info("Node registration request: {} ({})", request.getId(), request.getType());

        NodeRegistrationResponse response = registrationService.registerNode(request);

        HttpStatus status = response.isReregistered() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/{nodeId}/heartbeat")
    public ResponseEntity<HeartbeatResponse> heartbeat(
            @PathVariable String nodeId,
            @Valid @RequestBody HeartbeatRequest request) {

        HeartbeatResponse response = registrationService.processHeartbeat(nodeId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deregisterNode(@PathVariable String nodeId) {
        log.info("Node deregistration request: {}", nodeId);
        registrationService.deregisterNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<NodeQueryResponse> queryNodes(
            @RequestParam(required = false) InfraServerType type,
            @RequestParam(required = false) String datacenter,
            @RequestParam(required = false) ServerHealthStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean includeUnhealthy) {

        NodeQueryResponse response = registrationService.queryNodes(type, datacenter, status, includeUnhealthy);
        return ResponseEntity.ok(response);
    }
}
```

#### 3.2 Edge Management Controller

```java
@RestController
@RequestMapping("/api/v1/nodes/{sourceId}/connections")
@RequiredArgsConstructor
@Slf4j
@Validated
public class EdgeManagementController {

    private final EdgeManagementService edgeService;

    @PostMapping
    public ResponseEntity<ConnectionResponse> addConnection(
            @PathVariable String sourceId,
            @Valid @RequestBody AddConnectionRequest request) {

        log.info("Add connection: {} -> {}", sourceId, request.getTargetId());
        ConnectionResponse response = edgeService.addConnection(sourceId, request.getTargetId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> removeConnection(
            @PathVariable String sourceId,
            @PathVariable String targetId) {

        log.info("Remove connection: {} -> {}", sourceId, targetId);
        edgeService.removeConnection(sourceId, targetId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/bulk")
    public ResponseEntity<BulkConnectionResponse> addConnectionsBulk(
            @PathVariable String sourceId,
            @Valid @RequestBody BulkConnectionRequest request) {

        log.info("Bulk add connections: {} -> {} targets", sourceId, request.getTargetIds().size());
        BulkConnectionResponse response = edgeService.addConnectionsBulk(sourceId, request.getTargetIds());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkConnectionResponse> removeConnectionsBulk(
            @PathVariable String sourceId,
            @Valid @RequestBody BulkConnectionRequest request) {

        log.info("Bulk remove connections: {} -> {} targets", sourceId, request.getTargetIds().size());
        BulkConnectionResponse response = edgeService.removeConnectionsBulk(sourceId, request.getTargetIds());
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<BulkConnectionResponse> replaceConnections(
            @PathVariable String sourceId,
            @Valid @RequestBody BulkConnectionRequest request) {

        log.info("Replace all connections: {} -> {} targets", sourceId, request.getTargetIds().size());
        BulkConnectionResponse response = edgeService.replaceConnections(sourceId, request.getTargetIds());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ConnectionListResponse> getConnections(@PathVariable String sourceId) {
        ConnectionListResponse response = edgeService.getConnections(sourceId);
        return ResponseEntity.ok(response);
    }
}
```

#### 3.3 Node Health Watchdog Service

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class NodeHealthWatchdogService {

    private final NodeRepository nodeRepository;
    private final RedisStateService redisStateService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NodeHealthProperties healthProperties;

    private final StringRedisTemplate redisTemplate;

    /**
     * Runs every 15 seconds to check for stale nodes.
     * Uses distributed lock to ensure only one instance runs.
     */
    @Scheduled(fixedRateString = "${app.node.watchdog-interval-seconds:15}000")
    public void checkNodeHealth() {
        // Try to acquire distributed lock
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent("infra:watchdog:lock", getInstanceId(), Duration.ofSeconds(20));

        if (Boolean.TRUE.equals(acquired)) {
            try {
                doHealthCheck();
            } finally {
                // Lock auto-expires, no need to explicitly release
            }
        } else {
            log.debug("Another instance is running health watchdog");
        }
    }

    private void doHealthCheck() {
        Instant unhealthyThreshold = Instant.now()
            .minusSeconds(healthProperties.getUnhealthyThresholdSeconds());

        Instant removalThreshold = Instant.now()
            .minusSeconds(healthProperties.getRemovalThresholdSeconds());

        // Find nodes to mark as unhealthy
        List<NodeEntity> staleNodes = nodeRepository.findByLastHeartbeatAtBeforeAndHealthStatus(
            unhealthyThreshold, ServerHealthStatus.HEALTHY);

        int markedUnhealthy = 0;
        for (NodeEntity node : staleNodes) {
            node.setHealthStatus(ServerHealthStatus.UNHEALTHY);
            node.setUpdatedAt(Instant.now());
            nodeRepository.save(node);
            markedUnhealthy++;

            log.warn("Node {} marked UNHEALTHY (last heartbeat: {})",
                node.getId(), node.getLastHeartbeatAt());
        }

        // Find nodes to remove (optional - can be disabled)
        if (healthProperties.isAutoRemoveEnabled()) {
            List<NodeEntity> deadNodes = nodeRepository.findByLastHeartbeatAtBefore(removalThreshold);

            for (NodeEntity node : deadNodes) {
                log.warn("Removing dead node {} (last heartbeat: {})",
                    node.getId(), node.getLastHeartbeatAt());
                nodeRepository.delete(node);
            }

            if (!deadNodes.isEmpty()) {
                log.info("Removed {} dead nodes", deadNodes.size());
            }
        }

        if (markedUnhealthy > 0) {
            log.info("Marked {} nodes as UNHEALTHY", markedUnhealthy);
            // Trigger topology update broadcast
            redisStateService.updateTopologyVersion();
        }
    }

    private String getInstanceId() {
        return System.getenv().getOrDefault("HOSTNAME", "unknown-" + ProcessHandle.current().pid());
    }
}
```

#### 3.4 Node Registration Service

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class NodeRegistrationService {

    private final NodeRepository nodeRepository;
    private final DatacenterRepository datacenterRepository;
    private final RedisStateService redisStateService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NodeHealthProperties healthProperties;

    public NodeRegistrationResponse registerNode(NodeRegistrationRequest request) {
        // Check if datacenter exists, create if not
        DatacenterEntity datacenter = datacenterRepository.findById(request.getDatacenter())
            .orElseGet(() -> createDatacenter(request.getDatacenter(), request.getRegion()));

        // Check if node already exists
        Optional<NodeEntity> existingNode = nodeRepository.findById(request.getId());

        Instant now = Instant.now();
        NodeEntity node;
        boolean isReregistration = false;

        if (existingNode.isPresent()) {
            // Re-registration
            node = existingNode.get();
            node.setHostname(request.getHostname());
            node.setIpAddress(request.getIpAddress());
            node.setMaxSessions(request.getMaxSessions());
            node.setHealthStatus(ServerHealthStatus.HEALTHY);
            node.setLastHeartbeatAt(now);
            node.setUpdatedAt(now);
            node.setMetadata(request.getMetadata());
            isReregistration = true;

            log.info("Node re-registered: {}", node.getId());
        } else {
            // New registration
            node = NodeEntity.builder()
                .id(request.getId())
                .type(request.getType())
                .hostname(request.getHostname())
                .ipAddress(request.getIpAddress())
                .datacenter(datacenter)
                .maxSessions(request.getMaxSessions())
                .registrationSource("SELF")
                .registeredAt(now)
                .lastHeartbeatAt(now)
                .healthStatus(ServerHealthStatus.HEALTHY)
                .metadata(request.getMetadata())
                .createdAt(now)
                .updatedAt(now)
                .build();

            log.info("New node registered: {} ({})", node.getId(), node.getType());
        }

        nodeRepository.save(node);

        // Initialize Redis state
        redisStateService.initializeNodeState(node.getId());
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("NODE_REGISTERED", node.getId());

        return NodeRegistrationResponse.builder()
            .id(node.getId())
            .status(node.getHealthStatus())
            .registeredAt(node.getRegisteredAt())
            .reregisteredAt(isReregistration ? now : null)
            .reregistered(isReregistration)
            .heartbeatIntervalSeconds(healthProperties.getHeartbeatIntervalSeconds())
            .heartbeatTimeoutSeconds(healthProperties.getUnhealthyThresholdSeconds())
            .build();
    }

    public HeartbeatResponse processHeartbeat(String nodeId, HeartbeatRequest request) {
        NodeEntity node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NodeNotFoundException(nodeId));

        Instant now = Instant.now();

        // Update database
        node.setLastHeartbeatAt(now);
        node.setHealthStatus(ServerHealthStatus.HEALTHY);
        node.setUpdatedAt(now);
        nodeRepository.save(node);

        // Update Redis state
        redisStateService.updateHeartbeat(nodeId, now);

        if (request.getMetrics() != null) {
            redisStateService.setNodeMetrics(nodeId, request.getMetrics());
        }

        if (request.getSessionBreakdown() != null) {
            redisStateService.setSessionCounts(nodeId, request.getActiveSessions(), request.getSessionBreakdown());
        } else if (request.getActiveSessions() != null) {
            redisStateService.setActiveSessions(nodeId, request.getActiveSessions());
        }

        // Add trend data point
        if (request.getMetrics() != null && request.getActiveSessions() != null) {
            redisStateService.addTrendDataPoint(nodeId, request.getActiveSessions(), request.getMetrics().getCpuPercent());
        }

        return HeartbeatResponse.builder()
            .id(nodeId)
            .status(ServerHealthStatus.HEALTHY)
            .lastHeartbeat(now)
            .nextHeartbeatDue(now.plusSeconds(healthProperties.getHeartbeatIntervalSeconds()))
            .build();
    }

    public void deregisterNode(String nodeId) {
        NodeEntity node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NodeNotFoundException(nodeId));

        // Remove from database
        nodeRepository.delete(node);

        // Clean up Redis state
        redisStateService.removeNodeState(nodeId);
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("NODE_DEREGISTERED", nodeId);

        log.info("Node deregistered: {}", nodeId);
    }

    public NodeQueryResponse queryNodes(
            InfraServerType type,
            String datacenter,
            ServerHealthStatus status,
            boolean includeUnhealthy) {

        List<NodeEntity> nodes;

        if (type != null && datacenter != null) {
            nodes = nodeRepository.findByTypeAndDatacenterId(type, datacenter);
        } else if (type != null) {
            nodes = nodeRepository.findByType(type);
        } else if (datacenter != null) {
            nodes = nodeRepository.findByDatacenterId(datacenter);
        } else {
            nodes = nodeRepository.findAll();
        }

        // Filter by status
        if (status != null) {
            nodes = nodes.stream()
                .filter(n -> n.getHealthStatus() == status)
                .collect(Collectors.toList());
        } else if (!includeUnhealthy) {
            nodes = nodes.stream()
                .filter(n -> n.getHealthStatus() == ServerHealthStatus.HEALTHY)
                .collect(Collectors.toList());
        }

        // Enrich with Redis data
        List<NodeSummaryDto> nodeSummaries = nodes.stream()
            .map(this::toNodeSummary)
            .collect(Collectors.toList());

        return NodeQueryResponse.builder()
            .nodes(nodeSummaries)
            .total(nodeSummaries.size())
            .filter(NodeQueryResponse.Filter.builder()
                .type(type)
                .datacenter(datacenter)
                .status(status)
                .build())
            .build();
    }

    private NodeSummaryDto toNodeSummary(NodeEntity node) {
        Integer activeSessions = redisStateService.getActiveSessions(node.getId());

        return NodeSummaryDto.builder()
            .id(node.getId())
            .type(node.getType())
            .hostname(node.getHostname())
            .ipAddress(node.getIpAddress())
            .datacenter(node.getDatacenter().getId())
            .region(node.getDatacenter().getRegion())
            .status(node.getHealthStatus())
            .maxSessions(node.getMaxSessions())
            .activeSessions(activeSessions != null ? activeSessions : 0)
            .availableCapacity(node.getMaxSessions() - (activeSessions != null ? activeSessions : 0))
            .lastHeartbeat(node.getLastHeartbeatAt())
            .build();
    }

    private DatacenterEntity createDatacenter(String id, String region) {
        DatacenterEntity dc = DatacenterEntity.builder()
            .id(id)
            .region(region != null ? region : "unknown")
            .displayName(id.toUpperCase())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        log.info("Auto-created datacenter: {} ({})", id, region);
        return datacenterRepository.save(dc);
    }

    private void broadcastTopologyChange(String eventType, String nodeId) {
        Map<String, Object> event = Map.of(
            "type", eventType,
            "nodeId", nodeId,
            "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/topology/changes", event);
    }
}
```

---

### Phase 4: Mock Data Generator (Development)

```java
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mock-data.enabled", havingValue = "true")
public class MockDataGeneratorService {

    private final NodeRegistrationService registrationService;
    private final EdgeManagementService edgeService;
    private final RedisStateService redisStateService;
    private final NodeRepository nodeRepository;

    private final Random random = new Random();
    private boolean initialized = false;

    private static final String[] DATACENTERS = {"dc1", "dc2", "dc3", "dc4"};
    private static final String[] REGIONS = {"us-east", "us-east", "us-west", "us-west"};

    @PostConstruct
    public void initialize() {
        if (nodeRepository.count() == 0) {
            log.info("No nodes found, generating mock topology...");
            generateMockTopology();
        } else {
            log.info("Nodes exist ({}), skipping mock generation", nodeRepository.count());
        }
        initialized = true;
    }

    /**
     * Simulate heartbeats and metric updates every 5 seconds.
     */
    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    public void simulateHeartbeats() {
        if (!initialized) return;

        List<NodeEntity> nodes = nodeRepository.findByHealthStatus(ServerHealthStatus.HEALTHY);

        for (NodeEntity node : nodes) {
            simulateHeartbeat(node);
        }

        log.debug("Simulated heartbeats for {} nodes", nodes.size());
    }

    private void simulateHeartbeat(NodeEntity node) {
        int maxSessions = node.getMaxSessions();
        int activeSessions = random.nextInt(maxSessions);

        double utilizationFactor = (double) activeSessions / maxSessions;

        HeartbeatRequest request = HeartbeatRequest.builder()
            .activeSessions(activeSessions)
            .metrics(NodeMetrics.builder()
                .cpuPercent(round(20 + utilizationFactor * 50 + random.nextGaussian() * 5))
                .memoryPercent(round(40 + utilizationFactor * 30 + random.nextGaussian() * 3))
                .latencyMs((int)(5 + utilizationFactor * 50 + random.nextInt(20)))
                .jitterMs(Math.max(1, (int)(utilizationFactor * 10 + random.nextInt(5))))
                .packetLossPercent(round(random.nextDouble() * 0.1))
                .errorRate(round(utilizationFactor * 2 * random.nextDouble()))
                .mosScore(Math.max(10, 45 - (int)(utilizationFactor * 10)))
                .build())
            .sessionBreakdown(SessionBreakdown.builder()
                .inboundSessions((int)(activeSessions * 0.7))
                .outboundSessions((int)(activeSessions * 0.3))
                .ivrSessions((int)(activeSessions * 0.1))
                .queueSessions((int)(activeSessions * 0.15))
                .agentSessions((int)(activeSessions * 0.65))
                .onHoldSessions((int)(activeSessions * 0.1))
                .build())
            .build();

        try {
            registrationService.processHeartbeat(node.getId(), request);
        } catch (Exception e) {
            log.warn("Failed to simulate heartbeat for {}: {}", node.getId(), e.getMessage());
        }
    }

    private void generateMockTopology() {
        // Register SIP servers
        for (int i = 1; i <= 4; i++) {
            int dcIdx = i - 1;
            registerNode("sip-" + i, InfraServerType.SIP,
                String.format("sip-%02d.%s.example.com", i, DATACENTERS[dcIdx]),
                String.format("10.%d.1.10", i),
                DATACENTERS[dcIdx], REGIONS[dcIdx], 500);
        }

        // Register Media servers
        for (int i = 1; i <= 120; i++) {
            int dcIdx = (i - 1) / 30;
            registerNode("media-" + i, InfraServerType.MEDIA,
                String.format("media-%03d.%s.example.com", i, DATACENTERS[dcIdx]),
                String.format("10.%d.%d.%d", dcIdx + 1, 2 + (i % 10), 10 + ((i - 1) % 30)),
                DATACENTERS[dcIdx], REGIONS[dcIdx], 100);
        }

        // Create SIP -> Media connections
        for (int sipNum = 1; sipNum <= 4; sipNum++) {
            String sipId = "sip-" + sipNum;
            int mediaStart = (sipNum - 1) * 30 + 1;
            int mediaEnd = sipNum * 30;

            List<String> mediaIds = new ArrayList<>();
            for (int m = mediaStart; m <= mediaEnd; m++) {
                mediaIds.add("media-" + m);
            }

            edgeService.addConnectionsBulk(sipId, mediaIds);
            log.info("Created {} connections for {}", mediaIds.size(), sipId);
        }

        log.info("Mock topology generated: {} nodes", nodeRepository.count());
    }

    private void registerNode(String id, InfraServerType type, String hostname,
                             String ip, String datacenter, String region, int maxSessions) {
        NodeRegistrationRequest request = NodeRegistrationRequest.builder()
            .id(id)
            .type(type)
            .hostname(hostname)
            .ipAddress(ip)
            .datacenter(datacenter)
            .region(region)
            .maxSessions(maxSessions)
            .build();

        registrationService.registerNode(request);
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
```

---

## Files Summary

### New Files to Create

| File | Purpose |
|------|---------|
| **Database** | |
| `db/changelog/db.changelog-master.yaml` | Liquibase master changelog |
| `db/changelog/001-create-infrastructure-tables.yaml` | Schema |
| **Config** | |
| `config/NodeHealthProperties.java` | Health timing config |
| `config/RedisConfig.java` | Redis configuration |
| **Entities** | |
| `entity/DatacenterEntity.java` | JPA entity |
| `entity/NodeEntity.java` | JPA entity |
| `entity/EdgeEntity.java` | JPA entity |
| **Repositories** | |
| `repository/DatacenterRepository.java` | JPA repository |
| `repository/NodeRepository.java` | JPA repository |
| `repository/EdgeRepository.java` | JPA repository |
| **Controllers** | |
| `controller/NodeRegistrationController.java` | REST API |
| `controller/EdgeManagementController.java` | REST API |
| **Services** | |
| `service/NodeRegistrationService.java` | Registration logic |
| `service/EdgeManagementService.java` | Edge CRUD |
| `service/NodeHealthWatchdogService.java` | Health monitoring |
| `service/RedisStateService.java` | Redis operations |
| `service/TopologyCacheService.java` | Topology caching |
| `service/MockDataGeneratorService.java` | Dev mock data |
| **DTOs** | |
| `dto/request/NodeRegistrationRequest.java` | Request DTO |
| `dto/request/HeartbeatRequest.java` | Request DTO |
| `dto/request/AddConnectionRequest.java` | Request DTO |
| `dto/request/BulkConnectionRequest.java` | Request DTO |
| `dto/response/NodeRegistrationResponse.java` | Response DTO |
| `dto/response/HeartbeatResponse.java` | Response DTO |
| `dto/response/NodeQueryResponse.java` | Response DTO |
| `dto/response/ConnectionResponse.java` | Response DTO |
| `dto/response/BulkConnectionResponse.java` | Response DTO |
| **Exceptions** | |
| `exception/NodeNotFoundException.java` | Custom exception |
| `exception/InvalidConnectionException.java` | Custom exception |
| **Redis** | |
| `redis/RedisKeys.java` | Key constants |

### Files to Modify

| File | Changes |
|------|---------|
| `pom.xml` | Add JPA, PostgreSQL, Liquibase dependencies |
| `application.properties` | Add DB, health config |
| `service/InfrastructureService.java` | Read from DB + Redis |

### Files to Delete

| File | Reason |
|------|--------|
| `service/InfrastructureDataProvider.java` | Replaced by registration |
| `service/MockInfrastructureDataProvider.java` | Replaced by MockDataGeneratorService |

---

## Verification Checklist

- [ ] SIP server can register via `POST /api/v1/nodes/register`
- [ ] Media server can register via `POST /api/v1/nodes/register`
- [ ] Heartbeat updates node health via `POST /api/v1/nodes/{id}/heartbeat`
- [ ] Node marked UNHEALTHY after 30s without heartbeat
- [ ] SIP server can discover media via `GET /api/v1/nodes?type=MEDIA&status=HEALTHY`
- [ ] SIP server can add single connection via `POST /api/v1/nodes/{sipId}/connections`
- [ ] SIP server can bulk add connections via `PUT /api/v1/nodes/{sipId}/connections/bulk`
- [ ] SIP server can remove connections via `DELETE /api/v1/nodes/{sipId}/connections/{targetId}`
- [ ] Topology changes broadcast via WebSocket `/topic/topology/changes`
- [ ] Multiple oscc-state pods see consistent data
- [ ] Mock data generator creates test topology in dev mode
