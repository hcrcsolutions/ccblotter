# Node Registration API

This document describes how SIP servers, Media servers, SBCs, and Trunks register with the OSCC State API and report their health status.

## Overview

The OSCC State API maintains a real-time topology of infrastructure nodes. Nodes self-register on startup, send periodic heartbeats to report health and metrics, and optionally deregister on shutdown.

### Health Status Lifecycle

```
┌─────────────┐     Register      ┌─────────────┐
│   Unknown   │ ─────────────────►│   HEALTHY   │
└─────────────┘                   └──────┬──────┘
                                         │
                                         │ Heartbeats
                                         ▼
                                  ┌─────────────┐
                                  │   HEALTHY   │◄─────┐
                                  └──────┬──────┘      │
                                         │             │ Heartbeat
                                         │ No heartbeat│ received
                                         │ for 30s     │
                                         ▼             │
                                  ┌─────────────┐      │
                                  │  UNHEALTHY  │──────┘
                                  └──────┬──────┘
                                         │
                                         │ No heartbeat
                                         │ for 5 min
                                         ▼
                                  ┌─────────────┐
                                  │   Removed   │
                                  └─────────────┘
```

## API Endpoints

### Base URL

```
https://{oscc-state-host}:{port}/api/v1/nodes
```

### 1. Register Node

Registers a new node or re-registers an existing node.

**Endpoint:** `POST /api/v1/nodes/register`

**Request Body:**

```json
{
  "id": "sip-prod-01",
  "type": "SIP",
  "hostname": "sip-prod-01.example.com",
  "ipAddress": "10.1.1.50",
  "datacenter": "dc1",
  "region": "us-east",
  "maxSessions": 500,
  "carrierName": null,
  "trunkGroup": null,
  "metadata": {
    "version": "2.1.0",
    "buildDate": "2026-01-15"
  }
}
```

**Field Descriptions:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | Yes | Unique identifier for the node. Use a stable ID that survives restarts. |
| `type` | enum | Yes | One of: `TRUNK`, `SBC`, `SIP`, `MEDIA` |
| `hostname` | string | Yes | Fully qualified hostname |
| `ipAddress` | string | Yes | Primary IP address |
| `datacenter` | string | Yes | Datacenter identifier (e.g., `dc1`, `us-east-1a`) |
| `region` | string | No | Geographic region (e.g., `us-east`, `eu-west`) |
| `maxSessions` | integer | Yes | Maximum concurrent sessions this node can handle |
| `carrierName` | string | No | Carrier name (TRUNK type only) |
| `trunkGroup` | string | No | Trunk group: `primary`, `backup`, `overflow` (TRUNK type only) |
| `metadata` | object | No | Arbitrary key-value pairs for additional info |

**Response (201 Created):**

```json
{
  "id": "sip-prod-01",
  "status": "HEALTHY",
  "registeredAt": "2026-02-03T10:15:30.123Z",
  "reregistered": false,
  "heartbeatIntervalSeconds": 10,
  "heartbeatTimeoutSeconds": 30
}
```

**Response Fields:**

| Field | Description |
|-------|-------------|
| `heartbeatIntervalSeconds` | How often to send heartbeats (recommended interval) |
| `heartbeatTimeoutSeconds` | Node marked UNHEALTHY if no heartbeat within this period |

### 2. Send Heartbeat

Reports current health, session counts, and metrics.

**Endpoint:** `POST /api/v1/nodes/{nodeId}/heartbeat`

**Request Body:**

```json
{
  "activeSessions": 127,
  "metrics": {
    "cpuPercent": 45.2,
    "memoryPercent": 62.8,
    "latencyMs": 12,
    "jitterMs": 3,
    "packetLossPercent": 0.01,
    "errorRate": 0.002,
    "mosScore": 42
  },
  "sessionBreakdown": {
    "inboundSessions": 80,
    "outboundSessions": 47,
    "ivrSessions": 15,
    "queueSessions": 22,
    "agentSessions": 85,
    "onHoldSessions": 5
  }
}
```

**Field Descriptions:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `activeSessions` | integer | Yes | Current number of active sessions |
| `metrics` | object | No | Performance metrics (see below) |
| `sessionBreakdown` | object | No | Session categorization (see below) |

**Metrics Object:**

| Field | Type | Description |
|-------|------|-------------|
| `cpuPercent` | double | CPU utilization (0-100) |
| `memoryPercent` | double | Memory utilization (0-100) |
| `latencyMs` | integer | Average latency in milliseconds |
| `jitterMs` | integer | Jitter in milliseconds |
| `packetLossPercent` | double | Packet loss percentage (0-100) |
| `errorRate` | double | Failed calls per minute |
| `mosScore` | integer | Mean Opinion Score (10-50, representing 1.0-5.0) |

**Session Breakdown Object:**

| Field | Type | Description |
|-------|------|-------------|
| `inboundSessions` | integer | Inbound call sessions |
| `outboundSessions` | integer | Outbound call sessions |
| `ivrSessions` | integer | Sessions in IVR |
| `queueSessions` | integer | Sessions waiting in queue |
| `agentSessions` | integer | Sessions connected to agents |
| `onHoldSessions` | integer | Sessions currently on hold |

**Response (200 OK):**

```json
{
  "id": "sip-prod-01",
  "status": "HEALTHY",
  "lastHeartbeat": "2026-02-03T10:16:30.456Z",
  "nextHeartbeatDue": "2026-02-03T10:16:40.456Z"
}
```

### 3. Deregister Node

Gracefully removes a node from the topology.

**Endpoint:** `DELETE /api/v1/nodes/{nodeId}`

**Response:** `204 No Content`

### 4. Query Nodes

Retrieve registered nodes with optional filtering.

**Endpoint:** `GET /api/v1/nodes`

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `type` | enum | Filter by node type |
| `datacenter` | string | Filter by datacenter |
| `status` | enum | Filter by health status: `HEALTHY`, `DEGRADED`, `UNHEALTHY` |
| `includeUnhealthy` | boolean | Include unhealthy nodes (default: false) |

**Example:**

```bash
GET /api/v1/nodes?type=SIP&datacenter=dc1&includeUnhealthy=true
```

**Response:**

```json
{
  "nodes": [
    {
      "id": "sip-prod-01",
      "type": "SIP",
      "hostname": "sip-prod-01.example.com",
      "ipAddress": "10.1.1.50",
      "datacenter": "dc1",
      "region": "us-east",
      "status": "HEALTHY",
      "maxSessions": 500,
      "activeSessions": 127,
      "availableCapacity": 373,
      "lastHeartbeat": "2026-02-03T10:16:30.456Z"
    }
  ],
  "total": 1,
  "filter": {
    "type": "SIP",
    "datacenter": "dc1",
    "status": null
  }
}
```

## Connection Management

Nodes can declare their connections to other nodes (e.g., SIP server → Media servers). This creates edges in the topology visualization.

### Base URL

```
https://{oscc-state-host}:{port}/api/v1/nodes/{sourceId}/connections
```

### 5. Add Single Connection

Creates a connection from the source node to a target node.

**Endpoint:** `POST /api/v1/nodes/{sourceId}/connections`

**Request Body:**

```json
{
  "targetId": "media-01",
  "bandwidthMbps": 1000
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `targetId` | string | Yes | Target node ID to connect to |
| `bandwidthMbps` | integer | No | Connection bandwidth in Mbps |

**Response (201 Created):**

```json
{
  "id": "edge-sip-01-media-01",
  "sourceId": "sip-01",
  "targetId": "media-01",
  "bandwidthMbps": 1000,
  "created": true
}
```

### 6. Remove Single Connection

Removes a connection from the source node to a target node.

**Endpoint:** `DELETE /api/v1/nodes/{sourceId}/connections/{targetId}`

**Response:** `204 No Content`

### 7. Add Connections in Bulk

Adds multiple connections from a source node to multiple targets.

**Endpoint:** `PUT /api/v1/nodes/{sourceId}/connections/bulk`

**Request Body:**

```json
{
  "targetIds": ["media-01", "media-02", "media-03"]
}
```

**Response (200 OK):**

```json
{
  "sourceId": "sip-01",
  "addedCount": 2,
  "existingCount": 1,
  "failedCount": 0,
  "addedTargets": ["media-02", "media-03"],
  "existingTargets": ["media-01"],
  "failedTargets": []
}
```

### 8. Remove Connections in Bulk

Removes multiple connections from a source node.

**Endpoint:** `DELETE /api/v1/nodes/{sourceId}/connections/bulk`

**Request Body:**

```json
{
  "targetIds": ["media-01", "media-02"]
}
```

**Response (200 OK):** Same format as bulk add response.

### 9. Replace All Connections

Replaces all existing connections with a new set (atomic operation).

**Endpoint:** `PUT /api/v1/nodes/{sourceId}/connections`

**Request Body:**

```json
{
  "targetIds": ["media-05", "media-06", "media-07"]
}
```

**Response (200 OK):** Same format as bulk add response.

### 10. Get Current Connections

Lists all connections from a source node.

**Endpoint:** `GET /api/v1/nodes/{sourceId}/connections`

**Response (200 OK):**

```json
{
  "sourceId": "sip-01",
  "connections": [
    {
      "edgeId": "edge-sip-01-media-01",
      "targetId": "media-01",
      "bandwidthMbps": 1000
    },
    {
      "edgeId": "edge-sip-01-media-02",
      "targetId": "media-02",
      "bandwidthMbps": null
    }
  ],
  "total": 2
}
```

## Typical Topology Flow

```
TRUNK → SBC → SIP → MEDIA
```

1. **TRUNK** (carrier connections) connect to **SBCs**
2. **SBCs** (session border controllers) connect to **SIPs**
3. **SIPs** (SIP proxy servers) connect to **MEDIA** servers
4. **MEDIA** servers are leaf nodes (no downstream connections)

When a node registers, it should also declare its downstream connections:

```java
// SIP server example
client.start();  // Register the node
client.addConnectionsBulk(List.of("media-01", "media-02", "media-03"));  // Declare connections
```

## Error Responses

All error responses follow this format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "2026-02-03T10:15:30.123Z"
}
```

**Common Error Codes:**

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `NODE_NOT_FOUND` | 404 | Node ID does not exist |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `DATACENTER_NOT_FOUND` | 400 | Datacenter does not exist |
| `DUPLICATE_NODE` | 409 | Node ID already registered |

## Health Thresholds

The system uses these thresholds to determine node health:

| Metric | Warning | Critical |
|--------|---------|----------|
| CPU Utilization | > 60% | > 80% |
| Memory Utilization | > 70% | > 85% |
| Latency | > 100ms | > 200ms |
| Capacity Utilization | > 60% | > 80% |

## WebSocket Updates

Nodes can subscribe to topology changes via WebSocket:

**Endpoint:** `ws://{oscc-state-host}:{port}/ws`

**Subscribe to:** `/topic/topology/changes`

**Event Types:**

```json
{"type": "NODE_REGISTERED", "nodeId": "sip-prod-01", "timestamp": "..."}
{"type": "NODE_DEREGISTERED", "nodeId": "sip-prod-01", "timestamp": "..."}
{"type": "NODE_STATUS_CHANGED", "nodeId": "sip-prod-01", "status": "UNHEALTHY", "timestamp": "..."}
{"type": "TOPOLOGY_UPDATED", "timestamp": "..."}
```

## Best Practices

1. **Use stable node IDs**: Use hostname or a UUID that persists across restarts
2. **Handle re-registration**: On startup, always call register - the API handles re-registration gracefully
3. **Heartbeat interval**: Send heartbeats every 10 seconds (or as specified in registration response)
4. **Graceful shutdown**: Call deregister on shutdown to immediately remove from topology
5. **Retry logic**: Implement exponential backoff for failed API calls
6. **Metrics accuracy**: Report real metrics - they're used for capacity planning and alerting
7. **Declare connections after registration**: Register the node first, then declare downstream connections
8. **Use bulk APIs for efficiency**: When connecting to multiple nodes, use `addConnectionsBulk` instead of individual calls
9. **Connection ownership**: Upstream nodes own connections (SIP declares connections to MEDIA, not vice versa)
