package com.fmr.ec3.oscc.receiver.handler;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.infra.*;
import com.fmr.ec3.oscc.receiver.state.InfraStateWriter;
import com.fmr.ec3.oscc.receiver.websocket.WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfraEventHandlerTest {

    @Mock private InfraStateWriter infraWriter;
    @Mock private WebSocketBroadcaster broadcaster;

    private InfraEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InfraEventHandler(infraWriter, broadcaster);
    }

    private <T> EventEnvelope<T> envelope(String eventType, T payload) {
        return new EventEnvelope<>("evt-1", 1L, "src", "corr", "key",
            eventType, System.currentTimeMillis(), 1, payload);
    }

    @Test
    void handleHeartbeatWritesToRedis() {
        var metrics = new NodeMetricsDto(50.0, 60.0, 10, 2, 0.01, 0.1, 42);
        var breakdown = new SessionBreakdownDto(70, 30, 10, 15, 65, 10);
        var payload = new NodeHeartbeatPayload("sip-1", "SIP", 250, 500, metrics, breakdown);

        handler.handleHeartbeat(envelope(EventType.NODE_HEARTBEAT, payload));

        verify(infraWriter).writeHeartbeat(payload);
        verify(broadcaster).broadcastInfrastructure();
    }

    @Test
    void handleNodeDeregisteredCleansUpState() {
        var payload = new NodeDeregisteredPayload("media-5", "shutdown");

        handler.handleLifecycle(envelope(EventType.NODE_DEREGISTERED, payload));

        verify(infraWriter).removeNodeState("media-5");
        verify(infraWriter).updateTopologyVersion();
        verify(broadcaster).broadcastInfrastructure();
    }

    @Test
    void handleNodeRegisteredWritesDirectlyToRedis() {
        var payload = new NodeRegisteredPayload(
            "sip-1", "SIP", "sip-01.dc1.example.com", "10.1.1.10",
            "dc1", "us-east", 500, null, null);

        handler.handleLifecycle(envelope(EventType.NODE_REGISTERED, payload));

        verify(infraWriter).registerNode(payload);
        verify(broadcaster).broadcastInfrastructure();
    }

    @Test
    void handleNodeRegisteredWithCarrierInfo() {
        var payload = new NodeRegisteredPayload(
            "trunk-1", "TRUNK", "trunk-01.dc1.example.com", "10.1.1.20",
            "dc1", "us-east", 1000, "Verizon", "TG-001");

        handler.handleLifecycle(envelope(EventType.NODE_REGISTERED, payload));

        verify(infraWriter).registerNode(payload);
        verify(broadcaster).broadcastInfrastructure();
    }

    @Test
    void handleNodeAlarmDoesNotThrow() {
        var payload = new NodeAlarmPayload("sip-2", "CPU_HIGH", "WARNING", 80.0, 92.5);
        handler.handleLifecycle(envelope(EventType.NODE_ALARM, payload));
        // Should just log, no state writer interactions
        verifyNoInteractions(infraWriter);
    }
}
