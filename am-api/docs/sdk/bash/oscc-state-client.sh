#!/bin/bash
#
# Simple OSCC State Client for Node Registration and Heartbeat
#
# Usage:
#   ./oscc-state-client.sh register
#   ./oscc-state-client.sh heartbeat
#   ./oscc-state-client.sh deregister
#   ./oscc-state-client.sh loop        # Register and start heartbeat loop
#
# Environment Variables:
#   OSCC_STATE_URL  - OSCC State API base URL (default: http://localhost:8080)
#   NODE_ID         - Unique node identifier (default: hostname)
#   NODE_TYPE       - Node type: TRUNK, SBC, SIP, MEDIA (default: MEDIA)
#   HOSTNAME_FQDN   - Fully qualified hostname
#   IP_ADDRESS      - Node IP address
#   DATACENTER      - Datacenter ID (default: dc1)
#   REGION          - Region (default: us-east)
#   MAX_SESSIONS    - Maximum sessions (default: 100)
#   HEARTBEAT_INTERVAL - Heartbeat interval in seconds (default: 10)
#

set -e

# Configuration with defaults
OSCC_STATE_URL="${OSCC_STATE_URL:-http://localhost:8080}"
NODE_ID="${NODE_ID:-$(hostname)}"
NODE_TYPE="${NODE_TYPE:-MEDIA}"
HOSTNAME_FQDN="${HOSTNAME_FQDN:-$(hostname -f 2>/dev/null || hostname)}"
IP_ADDRESS="${IP_ADDRESS:-$(hostname -I 2>/dev/null | awk '{print $1}' || echo '127.0.0.1')}"
DATACENTER="${DATACENTER:-dc1}"
REGION="${REGION:-us-east}"
MAX_SESSIONS="${MAX_SESSIONS:-100}"
HEARTBEAT_INTERVAL="${HEARTBEAT_INTERVAL:-10}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get current CPU usage (Linux/macOS compatible)
get_cpu_percent() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        top -l 1 | grep "CPU usage" | awk '{print $3}' | sed 's/%//'
    else
        # Linux
        grep 'cpu ' /proc/stat | awk '{usage=($2+$4)*100/($2+$4+$5)} END {printf "%.1f", usage}'
    fi
}

# Get current memory usage
get_memory_percent() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        vm_stat | awk '/Pages active/ {active=$3} /Pages wired/ {wired=$3} /Pages free/ {free=$3} END {printf "%.1f", (active+wired)/(active+wired+free)*100}' | sed 's/\./,/' | sed 's/,/./'
    else
        # Linux
        free | grep Mem | awk '{printf "%.1f", $3/$2 * 100}'
    fi
}

# Get simulated active sessions (random for demo)
get_active_sessions() {
    echo $((RANDOM % MAX_SESSIONS))
}

# Register node with OSCC State API
register_node() {
    log_info "Registering node: $NODE_ID ($NODE_TYPE)"

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST "${OSCC_STATE_URL}/api/v1/nodes/register" \
        -H "Content-Type: application/json" \
        -d "{
            \"id\": \"${NODE_ID}\",
            \"type\": \"${NODE_TYPE}\",
            \"hostname\": \"${HOSTNAME_FQDN}\",
            \"ipAddress\": \"${IP_ADDRESS}\",
            \"datacenter\": \"${DATACENTER}\",
            \"region\": \"${REGION}\",
            \"maxSessions\": ${MAX_SESSIONS}
        }")

    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')

    if [[ "$http_code" == "200" || "$http_code" == "201" ]]; then
        log_info "Registration successful"
        echo "$body" | jq -r '.heartbeatIntervalSeconds // empty' 2>/dev/null && \
            HEARTBEAT_INTERVAL=$(echo "$body" | jq -r '.heartbeatIntervalSeconds // 10')
        return 0
    else
        log_error "Registration failed (HTTP $http_code): $body"
        return 1
    fi
}

# Send heartbeat to OSCC State API
send_heartbeat() {
    local active_sessions
    active_sessions=$(get_active_sessions)
    local cpu_percent
    cpu_percent=$(get_cpu_percent)
    local memory_percent
    memory_percent=$(get_memory_percent)

    # Distribute sessions across categories
    local inbound=$((active_sessions * 60 / 100))
    local outbound=$((active_sessions * 40 / 100))
    local ivr=$((active_sessions * 10 / 100))
    local queue=$((active_sessions * 15 / 100))
    local agent=$((active_sessions * 65 / 100))
    local on_hold=$((active_sessions * 10 / 100))

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST "${OSCC_STATE_URL}/api/v1/nodes/${NODE_ID}/heartbeat" \
        -H "Content-Type: application/json" \
        -d "{
            \"activeSessions\": ${active_sessions},
            \"metrics\": {
                \"cpuPercent\": ${cpu_percent:-25.0},
                \"memoryPercent\": ${memory_percent:-50.0},
                \"latencyMs\": $((10 + RANDOM % 20)),
                \"jitterMs\": $((1 + RANDOM % 5)),
                \"packetLossPercent\": 0.01,
                \"errorRate\": 0.001,
                \"mosScore\": $((40 + RANDOM % 8))
            },
            \"sessionBreakdown\": {
                \"inboundSessions\": ${inbound},
                \"outboundSessions\": ${outbound},
                \"ivrSessions\": ${ivr},
                \"queueSessions\": ${queue},
                \"agentSessions\": ${agent},
                \"onHoldSessions\": ${on_hold}
            }
        }")

    local http_code
    http_code=$(echo "$response" | tail -n1)

    if [[ "$http_code" == "200" ]]; then
        log_info "Heartbeat sent (sessions: $active_sessions, cpu: ${cpu_percent}%)"
        return 0
    else
        local body
        body=$(echo "$response" | sed '$d')
        log_error "Heartbeat failed (HTTP $http_code): $body"
        return 1
    fi
}

# Deregister node from OSCC State API
deregister_node() {
    log_info "Deregistering node: $NODE_ID"

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${OSCC_STATE_URL}/api/v1/nodes/${NODE_ID}")

    if [[ "$http_code" == "204" || "$http_code" == "404" ]]; then
        log_info "Deregistration successful"
        return 0
    else
        log_error "Deregistration failed (HTTP $http_code)"
        return 1
    fi
}

# Heartbeat loop with graceful shutdown
heartbeat_loop() {
    log_info "Starting heartbeat loop (interval: ${HEARTBEAT_INTERVAL}s)"
    log_info "Press Ctrl+C to stop"

    # Trap SIGINT and SIGTERM for graceful shutdown
    trap 'log_warn "Shutting down..."; deregister_node; exit 0' SIGINT SIGTERM

    while true; do
        send_heartbeat || log_warn "Heartbeat failed, will retry"
        sleep "$HEARTBEAT_INTERVAL"
    done
}

# Main command handler
case "${1:-}" in
    register)
        register_node
        ;;
    heartbeat)
        send_heartbeat
        ;;
    deregister)
        deregister_node
        ;;
    loop)
        register_node && heartbeat_loop
        ;;
    *)
        echo "OSCC State Client - Node Registration and Heartbeat"
        echo ""
        echo "Usage: $0 <command>"
        echo ""
        echo "Commands:"
        echo "  register    - Register this node with OSCC State API"
        echo "  heartbeat   - Send a single heartbeat"
        echo "  deregister  - Deregister this node from OSCC State API"
        echo "  loop        - Register and start continuous heartbeat loop"
        echo ""
        echo "Environment Variables:"
        echo "  OSCC_STATE_URL=$OSCC_STATE_URL"
        echo "  NODE_ID=$NODE_ID"
        echo "  NODE_TYPE=$NODE_TYPE"
        echo "  DATACENTER=$DATACENTER"
        echo "  REGION=$REGION"
        echo "  MAX_SESSIONS=$MAX_SESSIONS"
        echo "  HEARTBEAT_INTERVAL=$HEARTBEAT_INTERVAL"
        exit 1
        ;;
esac
