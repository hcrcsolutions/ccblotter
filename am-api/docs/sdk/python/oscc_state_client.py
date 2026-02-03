#!/usr/bin/env python3
"""
OSCC State Client SDK for Python

A simple client for registering nodes and sending heartbeats to the OSCC State API.

Usage:
    from oscc_state_client import OsccStateClient, NodeType

    client = OsccStateClient(
        base_url="http://localhost:8080",
        node_id="sip-prod-01",
        node_type=NodeType.SIP,
        hostname="sip-prod-01.example.com",
        ip_address="10.1.1.50",
        datacenter="dc1",
        max_sessions=500
    )

    # Start registration and heartbeat loop
    client.start()

    # On shutdown
    client.stop()
"""

import atexit
import logging
import os
import platform
import random
import signal
import socket
import threading
import time
from dataclasses import dataclass, asdict, field
from enum import Enum
from typing import Callable, Dict, Optional

import requests

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class NodeType(Enum):
    TRUNK = "TRUNK"
    SBC = "SBC"
    SIP = "SIP"
    MEDIA = "MEDIA"


@dataclass
class Metrics:
    cpu_percent: float = 0.0
    memory_percent: float = 0.0
    latency_ms: int = 0
    jitter_ms: int = 0
    packet_loss_percent: float = 0.0
    error_rate: float = 0.0
    mos_score: int = 40  # 4.0 on 10-50 scale

    def to_dict(self) -> dict:
        return {
            "cpuPercent": self.cpu_percent,
            "memoryPercent": self.memory_percent,
            "latencyMs": self.latency_ms,
            "jitterMs": self.jitter_ms,
            "packetLossPercent": self.packet_loss_percent,
            "errorRate": self.error_rate,
            "mosScore": self.mos_score
        }


@dataclass
class SessionBreakdown:
    inbound_sessions: int = 0
    outbound_sessions: int = 0
    ivr_sessions: int = 0
    queue_sessions: int = 0
    agent_sessions: int = 0
    on_hold_sessions: int = 0

    def to_dict(self) -> dict:
        return {
            "inboundSessions": self.inbound_sessions,
            "outboundSessions": self.outbound_sessions,
            "ivrSessions": self.ivr_sessions,
            "queueSessions": self.queue_sessions,
            "agentSessions": self.agent_sessions,
            "onHoldSessions": self.on_hold_sessions
        }


@dataclass
class SessionInfo:
    active_sessions: int = 0
    breakdown: Optional[SessionBreakdown] = None


class OsccStateClient:
    """Client for communicating with the OSCC State API."""

    def __init__(
        self,
        base_url: str,
        node_id: str,
        node_type: NodeType,
        hostname: str,
        ip_address: str,
        datacenter: str,
        max_sessions: int,
        region: str = None,
        carrier_name: str = None,
        trunk_group: str = None,
        metadata: Dict[str, str] = None,
        metrics_supplier: Callable[[], Metrics] = None,
        session_supplier: Callable[[], SessionInfo] = None
    ):
        self.base_url = base_url.rstrip('/')
        self.node_id = node_id
        self.node_type = node_type
        self.hostname = hostname
        self.ip_address = ip_address
        self.datacenter = datacenter
        self.region = region
        self.max_sessions = max_sessions
        self.carrier_name = carrier_name
        self.trunk_group = trunk_group
        self.metadata = metadata
        self.metrics_supplier = metrics_supplier
        self.session_supplier = session_supplier

        self._running = False
        self._heartbeat_thread: Optional[threading.Thread] = None
        self._heartbeat_interval = 10
        self._stop_event = threading.Event()

    def start(self):
        """Start the client: register with OSCC State API and begin heartbeat loop."""
        if self._running:
            logger.warning("Client already started")
            return

        logger.info(f"Starting OSCC State client for node: {self.node_id}")

        try:
            self.register()
            self._start_heartbeat_loop()
            self._running = True
            logger.info("OSCC State client started successfully")
        except Exception as e:
            raise RuntimeError(f"Failed to start OSCC State client: {e}")

    def stop(self):
        """Stop the client: cancel heartbeats and deregister from OSCC State API."""
        if not self._running:
            return

        logger.info(f"Stopping OSCC State client for node: {self.node_id}")

        self._stop_event.set()
        if self._heartbeat_thread:
            self._heartbeat_thread.join(timeout=5)

        try:
            self.deregister()
            logger.info("OSCC State client stopped successfully")
        except Exception as e:
            logger.warning(f"Failed to deregister node: {e}")

        self._running = False

    def register(self) -> dict:
        """Register this node with OSCC State API."""
        payload = {
            "id": self.node_id,
            "type": self.node_type.value,
            "hostname": self.hostname,
            "ipAddress": self.ip_address,
            "datacenter": self.datacenter,
            "maxSessions": self.max_sessions
        }

        if self.region:
            payload["region"] = self.region
        if self.carrier_name:
            payload["carrierName"] = self.carrier_name
        if self.trunk_group:
            payload["trunkGroup"] = self.trunk_group
        if self.metadata:
            payload["metadata"] = self.metadata

        response = requests.post(
            f"{self.base_url}/api/v1/nodes/register",
            json=payload,
            timeout=10
        )
        response.raise_for_status()

        result = response.json()
        self._heartbeat_interval = result.get("heartbeatIntervalSeconds", 10)
        logger.info(f"Registered with OSCC State API. Heartbeat interval: {self._heartbeat_interval}s")

        return result

    def send_heartbeat(self) -> dict:
        """Send a heartbeat to OSCC State API."""
        session_info = self.session_supplier() if self.session_supplier else SessionInfo()
        metrics = self.metrics_supplier() if self.metrics_supplier else None

        payload = {
            "activeSessions": session_info.active_sessions
        }

        if metrics:
            payload["metrics"] = metrics.to_dict()

        if session_info.breakdown:
            payload["sessionBreakdown"] = session_info.breakdown.to_dict()

        response = requests.post(
            f"{self.base_url}/api/v1/nodes/{self.node_id}/heartbeat",
            json=payload,
            timeout=10
        )
        response.raise_for_status()

        return response.json()

    def deregister(self):
        """Deregister this node from OSCC State API."""
        response = requests.delete(
            f"{self.base_url}/api/v1/nodes/{self.node_id}",
            timeout=10
        )
        # 204 No Content or 404 Not Found are both acceptable
        if response.status_code not in (204, 404):
            response.raise_for_status()

    def _start_heartbeat_loop(self):
        """Start the background heartbeat loop."""
        def heartbeat_loop():
            while not self._stop_event.wait(self._heartbeat_interval):
                try:
                    self.send_heartbeat()
                    logger.debug("Heartbeat sent successfully")
                except Exception as e:
                    logger.warning(f"Heartbeat failed: {e}")

        self._heartbeat_thread = threading.Thread(target=heartbeat_loop, daemon=True)
        self._heartbeat_thread.start()


# ==================== Example Usage ====================

def get_system_metrics() -> Metrics:
    """Collect current system metrics."""
    try:
        import psutil
        cpu = psutil.cpu_percent()
        memory = psutil.virtual_memory().percent
    except ImportError:
        # Fallback if psutil not available
        cpu = random.uniform(20, 60)
        memory = random.uniform(40, 70)

    return Metrics(
        cpu_percent=cpu,
        memory_percent=memory,
        latency_ms=random.randint(10, 25),
        jitter_ms=random.randint(1, 5),
        packet_loss_percent=random.uniform(0, 0.1),
        error_rate=random.uniform(0, 0.05),
        mos_score=random.randint(40, 48)
    )


def get_session_info() -> SessionInfo:
    """Collect current session information."""
    # In a real implementation, this would come from your session manager
    active = random.randint(50, 200)

    breakdown = SessionBreakdown(
        inbound_sessions=int(active * 0.6),
        outbound_sessions=int(active * 0.4),
        ivr_sessions=int(active * 0.1),
        queue_sessions=int(active * 0.15),
        agent_sessions=int(active * 0.65),
        on_hold_sessions=int(active * 0.1)
    )

    return SessionInfo(active_sessions=active, breakdown=breakdown)


def main():
    """Example: Run as a SIP server agent."""
    oscc_state_url = os.environ.get("OSCC_STATE_URL", "http://localhost:8080")
    node_id = os.environ.get("NODE_ID", f"sip-{socket.gethostname()}")
    datacenter = os.environ.get("DATACENTER", "dc1")
    region = os.environ.get("REGION", "us-east")
    max_sessions = int(os.environ.get("MAX_SESSIONS", "500"))

    print(f"Starting SIP Server with OSCC State API integration")
    print(f"  OSCC State URL: {oscc_state_url}")
    print(f"  Node ID: {node_id}")
    print(f"  Datacenter: {datacenter}")

    client = OsccStateClient(
        base_url=oscc_state_url,
        node_id=node_id,
        node_type=NodeType.SIP,
        hostname=socket.getfqdn(),
        ip_address=socket.gethostbyname(socket.gethostname()),
        datacenter=datacenter,
        region=region,
        max_sessions=max_sessions,
        metadata={
            "version": "2.1.0",
            "platform": platform.system()
        },
        metrics_supplier=get_system_metrics,
        session_supplier=get_session_info
    )

    # Register shutdown handler
    def shutdown(signum, frame):
        print("\nShutting down...")
        client.stop()
        exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    # Start the client
    client.start()

    # Keep main thread alive
    print("Press Ctrl+C to stop")
    while True:
        time.sleep(1)


if __name__ == "__main__":
    main()
