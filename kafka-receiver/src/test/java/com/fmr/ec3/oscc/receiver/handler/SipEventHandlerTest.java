package com.fmr.ec3.oscc.receiver.handler;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.sip.*;
import com.fmr.ec3.oscc.receiver.state.AgentStateWriter;
import com.fmr.ec3.oscc.receiver.state.CallStateWriter;
import com.fmr.ec3.oscc.receiver.state.QueueStateWriter;
import com.fmr.ec3.oscc.receiver.websocket.WebSocketBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SipEventHandlerTest {

    @Mock private AgentStateWriter agentWriter;
    @Mock private CallStateWriter callWriter;
    @Mock private QueueStateWriter queueWriter;
    @Mock private WebSocketBroadcaster broadcaster;

    private SipEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SipEventHandler(agentWriter, callWriter, queueWriter, broadcaster);
    }

    private <T> EventEnvelope<T> envelope(String eventType, T payload) {
        return new EventEnvelope<>("evt-1", 1L, "sip-1", "corr-1", "key-1",
            eventType, System.currentTimeMillis(), 1, payload);
    }

    @Nested
    class AgentLoggedInTests {
        @Test
        void savesAgentAsOnline() {
            var payload = new AgentLoggedInPayload("AGT-0001", "John Smith", List.of("Sales"), "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_IN, payload));

            verify(agentWriter).saveAgent("AGT-0001", "John Smith", "ONLINE", null);
            verify(broadcaster).broadcastAgents();
            verify(broadcaster).broadcastSummary();
        }
    }

    @Nested
    class AgentLoggedOutTests {
        @Test
        void updatesExistingAgentToUnavailable() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            var payload = new AgentLoggedOutPayload("AGT-0001", "end_of_shift", "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_OUT, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "UNAVAILABLE", null);
        }

        @Test
        void createsUnknownAgentAsUnavailable() {
            when(agentWriter.agentExists("AGT-9999")).thenReturn(false);
            var payload = new AgentLoggedOutPayload("AGT-9999", "end_of_shift", "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_OUT, payload));

            verify(agentWriter).saveAgent("AGT-9999", "Unknown", "UNAVAILABLE", null);
        }
    }

    @Nested
    class AgentBreakTests {
        @Test
        void breakStartedSetsAway() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            var payload = new AgentBreakStartedPayload("AGT-0001", "Lunch", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_STARTED, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "AWAY", null);
        }

        @Test
        void breakEndedSetsOnline() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            var payload = new AgentBreakEndedPayload("AGT-0001", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_ENDED, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "ONLINE", null);
        }

        @Test
        void breakStartedCreatesUnknownAgent() {
            when(agentWriter.agentExists("AGT-9999")).thenReturn(false);
            var payload = new AgentBreakStartedPayload("AGT-9999", "Lunch", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_STARTED, payload));

            verify(agentWriter).saveAgent("AGT-9999", "Unknown", "AWAY", null);
        }
    }

    @Nested
    class CallQueuedTests {
        @Test
        void addsCallToQueue() {
            long now = System.currentTimeMillis();
            var payload = new CallQueuedPayload("call-1", "(212) 555-0100", "Sales", 2, now, "sip-1");
            handler.handle(envelope(EventType.CALL_QUEUED, payload));

            verify(queueWriter).addToQueue("call-1", "(212) 555-0100", "Sales", 2, Instant.ofEpochMilli(now));
            verify(broadcaster).broadcastQueue();
        }
    }

    @Nested
    class CallRoutedToAgentTests {
        @Test
        void happyPath() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            when(agentWriter.getAgentState("AGT-0001")).thenReturn("ONLINE");
            when(queueWriter.queuedCallExists("queued-1")).thenReturn(true);

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-0001", "John Smith", "(212) 555-0100",
                "queued-1", "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(queueWriter).removeFromQueue("queued-1");
            verify(callWriter).saveCall("call-1", "(212) 555-0100", "AGT-0001",
                "John Smith", Instant.ofEpochMilli(now), "TALKING");
            verify(agentWriter).updateAgentState("AGT-0001", "ON_CALL", "call-1");
        }

        @Test
        void createsAgentOnReferenceWhenMissing() {
            when(agentWriter.agentExists("AGT-9999")).thenReturn(false);
            when(agentWriter.getAgentState("AGT-9999")).thenReturn("ONLINE");

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-9999", "New Agent", "(212) 555-0100",
                null, "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(agentWriter).saveAgent("AGT-9999", "New Agent", "ONLINE", null);
            verify(callWriter).saveCall(eq("call-1"), anyString(), eq("AGT-9999"),
                anyString(), any(), eq("TALKING"));
        }

        @Test
        void forceEndsExistingCallWhenAgentAlreadyOnCall() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            when(agentWriter.getAgentState("AGT-0001")).thenReturn("ON_CALL");
            when(agentWriter.getAgentCurrentCallId("AGT-0001")).thenReturn("old-call");

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "new-call", "AGT-0001", "John Smith", "(212) 555-0100",
                null, "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(callWriter).removeCall("old-call");
            verify(callWriter).saveCall(eq("new-call"), anyString(), eq("AGT-0001"),
                anyString(), any(), eq("TALKING"));
        }

        @Test
        void proceedsWhenQueueEntryMissing() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            when(agentWriter.getAgentState("AGT-0001")).thenReturn("ONLINE");
            when(queueWriter.queuedCallExists("missing-queue")).thenReturn(false);

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-0001", "John Smith", "(212) 555-0100",
                "missing-queue", "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(queueWriter, never()).removeFromQueue(anyString());
            verify(callWriter).saveCall(eq("call-1"), anyString(), eq("AGT-0001"),
                anyString(), any(), eq("TALKING"));
        }

        @Test
        void handlesNullQueuedCallId() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            when(agentWriter.getAgentState("AGT-0001")).thenReturn("ONLINE");

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-0001", "John Smith", "(212) 555-0100",
                null, "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(queueWriter, never()).queuedCallExists(anyString());
            verify(queueWriter, never()).removeFromQueue(anyString());
        }

        @Test
        void broadcastsAllTopics() {
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);
            when(agentWriter.getAgentState("AGT-0001")).thenReturn("ONLINE");

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-0001", "John Smith", "(212) 555-0100",
                null, "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(broadcaster).broadcastAgents();
            verify(broadcaster).broadcastCalls();
            verify(broadcaster).broadcastQueue();
            verify(broadcaster).broadcastSummary();
        }
    }

    @Nested
    class CallEndedTests {
        @Test
        void happyPath() {
            when(callWriter.callExists("call-1")).thenReturn(true);
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);

            long start = 1700000000000L;
            long end = 1700000300000L;
            var payload = new CallEndedPayload(
                "call-1", "AGT-0001", "(212) 555-0100", start, end, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            verify(agentWriter).updateLastCallInfo("AGT-0001", "(212) 555-0100",
                Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), 300);
            verify(agentWriter).updateAgentState("AGT-0001", "ONLINE", null);
            verify(callWriter).removeCall("call-1");
        }

        @Test
        void proceedsForUnknownCall() {
            when(callWriter.callExists("unknown-call")).thenReturn(false);
            when(agentWriter.agentExists("AGT-0001")).thenReturn(true);

            var payload = new CallEndedPayload(
                "unknown-call", "AGT-0001", "(212) 555-0100",
                1700000000000L, 1700000300000L, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            // Should still update agent and remove call
            verify(agentWriter).updateAgentState("AGT-0001", "ONLINE", null);
            verify(callWriter).removeCall("unknown-call");
        }

        @Test
        void skipsAgentUpdateForUnknownAgent() {
            when(callWriter.callExists("call-1")).thenReturn(true);
            when(agentWriter.agentExists("AGT-9999")).thenReturn(false);

            var payload = new CallEndedPayload(
                "call-1", "AGT-9999", "(212) 555-0100",
                1700000000000L, 1700000300000L, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            verify(agentWriter, never()).updateAgentState(anyString(), anyString(), any());
            verify(agentWriter, never()).updateLastCallInfo(anyString(), anyString(), any(), any(), anyLong());
            verify(callWriter).removeCall("call-1");
        }
    }

    @Nested
    class CallAbandonedTests {
        @Test
        void removesQueuedCall() {
            when(queueWriter.queuedCallExists("call-1")).thenReturn(true);
            var payload = new CallAbandonedPayload(
                "call-1", "(212) 555-0100", 1700000000000L, 1700000060000L, 60, "sip-1");
            handler.handle(envelope(EventType.CALL_ABANDONED, payload));

            verify(queueWriter).removeFromQueue("call-1");
            verify(broadcaster).broadcastQueue();
        }

        @Test
        void handlesUnknownQueuedCall() {
            when(queueWriter.queuedCallExists("unknown")).thenReturn(false);
            var payload = new CallAbandonedPayload(
                "unknown", "(212) 555-0100", 1700000000000L, 1700000060000L, 60, "sip-1");
            handler.handle(envelope(EventType.CALL_ABANDONED, payload));

            verify(queueWriter, never()).removeFromQueue(anyString());
            verify(broadcaster).broadcastQueue();
        }
    }

    @Nested
    class CallHoldChangedTests {
        @Test
        void updatesCallState() {
            when(callWriter.callExists("call-1")).thenReturn(true);
            var payload = new CallHoldChangedPayload("call-1", "AGT-0001", "ON_HOLD", "sip-1");
            handler.handle(envelope(EventType.CALL_HOLD_CHANGED, payload));

            verify(callWriter).updateCallState("call-1", "ON_HOLD");
            verify(broadcaster).broadcastCalls();
        }

        @Test
        void handlesUnknownCall() {
            when(callWriter.callExists("unknown")).thenReturn(false);
            var payload = new CallHoldChangedPayload("unknown", "AGT-0001", "ON_HOLD", "sip-1");
            handler.handle(envelope(EventType.CALL_HOLD_CHANGED, payload));

            verify(callWriter, never()).updateCallState(anyString(), anyString());
            verify(broadcaster).broadcastCalls();
        }
    }
}
