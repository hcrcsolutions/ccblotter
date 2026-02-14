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
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList(null, null));
            var payload = new AgentLoggedInPayload("AGT-0001", "John Smith", List.of("Sales"), "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_IN, payload));

            verify(agentWriter).saveAgent("AGT-0001", "John Smith", "ONLINE", null);
            verify(callWriter, never()).removeCall(anyString());
            verify(broadcaster).broadcastAgents();
            verify(broadcaster).broadcastSummary();
        }

        @Test
        void reLoginCleansUpExistingOnlineAgent() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));
            var payload = new AgentLoggedInPayload("AGT-0001", "John Smith", List.of("Sales"), "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_IN, payload));

            verify(agentWriter).saveAgent("AGT-0001", "John Smith", "ONLINE", null);
            verify(callWriter, never()).removeCall(anyString());
        }

        @Test
        void removesOrphanedCallOnReLogin() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "active-call-3"));
            var payload = new AgentLoggedInPayload("AGT-0001", "John Smith", List.of("Sales"), "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_IN, payload));

            verify(callWriter).removeCall("active-call-3");
            verify(agentWriter).saveAgent("AGT-0001", "John Smith", "ONLINE", null);
            verify(broadcaster).broadcastCalls();
        }
    }

    @Nested
    class AgentLoggedOutTests {
        @Test
        void updatesExistingAgentToUnavailable() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));
            var payload = new AgentLoggedOutPayload("AGT-0001", "end_of_shift", "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_OUT, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "UNAVAILABLE", null);
            verify(agentWriter, never()).saveAgent(anyString(), anyString(), anyString(), any());
            verify(callWriter, never()).removeCall(anyString());
        }

        @Test
        void createsUnknownAgentAsUnavailable() {
            when(agentWriter.getAgentStateAndCallId("AGT-9999"))
                .thenReturn(java.util.Arrays.asList(null, null));
            var payload = new AgentLoggedOutPayload("AGT-9999", "end_of_shift", "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_OUT, payload));

            verify(agentWriter).saveAgent("AGT-9999", "Unknown", "UNAVAILABLE", null);
        }

        @Test
        void removesOrphanedCallOnLogout() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "active-call-1"));
            var payload = new AgentLoggedOutPayload("AGT-0001", "end_of_shift", "sip-1");
            handler.handle(envelope(EventType.AGENT_LOGGED_OUT, payload));

            verify(callWriter).removeCall("active-call-1");
            verify(agentWriter).updateAgentState("AGT-0001", "UNAVAILABLE", null);
            verify(broadcaster).broadcastCalls();
        }
    }

    @Nested
    class AgentBreakTests {
        @Test
        void breakStartedSetsAway() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));
            var payload = new AgentBreakStartedPayload("AGT-0001", "Lunch", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_STARTED, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "AWAY", null);
            verify(agentWriter, never()).saveAgent(anyString(), anyString(), anyString(), any());
            verify(callWriter, never()).removeCall(anyString());
        }

        @Test
        void breakEndedSetsOnline() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("AWAY", null));
            var payload = new AgentBreakEndedPayload("AGT-0001", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_ENDED, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "ONLINE", null);
            verify(agentWriter, never()).saveAgent(anyString(), anyString(), anyString(), any());
            verify(callWriter, never()).removeCall(anyString());
        }

        @Test
        void breakEndedCreatesUnknownAgent() {
            when(agentWriter.getAgentStateAndCallId("AGT-9999"))
                .thenReturn(java.util.Arrays.asList(null, null));
            var payload = new AgentBreakEndedPayload("AGT-9999", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_ENDED, payload));

            verify(agentWriter).saveAgent("AGT-9999", "Unknown", "ONLINE", null);
        }

        @Test
        void removesOrphanedCallOnBreakEnded() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "active-call-4"));
            var payload = new AgentBreakEndedPayload("AGT-0001", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_ENDED, payload));

            verify(callWriter).removeCall("active-call-4");
            verify(agentWriter).updateAgentState("AGT-0001", "ONLINE", null);
            verify(broadcaster).broadcastCalls();
        }

        @Test
        void breakStartedCreatesUnknownAgent() {
            when(agentWriter.getAgentStateAndCallId("AGT-9999"))
                .thenReturn(java.util.Arrays.asList(null, null));
            var payload = new AgentBreakStartedPayload("AGT-9999", "Lunch", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_STARTED, payload));

            verify(agentWriter).saveAgent("AGT-9999", "Unknown", "AWAY", null);
        }

        @Test
        void removesOrphanedCallOnBreakStarted() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "active-call-2"));
            var payload = new AgentBreakStartedPayload("AGT-0001", "Lunch", "sip-1");
            handler.handle(envelope(EventType.AGENT_BREAK_STARTED, payload));

            verify(callWriter).removeCall("active-call-2");
            verify(agentWriter).updateAgentState("AGT-0001", "AWAY", null);
            verify(broadcaster).broadcastCalls();
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
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));

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
            when(agentWriter.getAgentStateAndCallId("AGT-9999"))
                .thenReturn(java.util.Arrays.asList(null, null));

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
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "old-call"));

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
        void removesFromQueueIdempotently() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-0001", "John Smith", "(212) 555-0100",
                "missing-queue", "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            // removeFromQueue is idempotent — always called, no exists check
            verify(queueWriter).removeFromQueue("missing-queue");
            verify(callWriter).saveCall(eq("call-1"), anyString(), eq("AGT-0001"),
                anyString(), any(), eq("TALKING"));
        }

        @Test
        void handlesNullQueuedCallId() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));

            long now = System.currentTimeMillis();
            var payload = new CallRoutedToAgentPayload(
                "call-1", "AGT-0001", "John Smith", "(212) 555-0100",
                null, "Sales", now, "sip-1");
            handler.handle(envelope(EventType.CALL_ROUTED_TO_AGENT, payload));

            verify(queueWriter, never()).removeFromQueue(anyString());
        }

        @Test
        void broadcastsAllTopics() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(java.util.Arrays.asList("ONLINE", null));

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
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "call-1"));

            long start = 1700000000000L;
            long end = 1700000300000L;
            var payload = new CallEndedPayload(
                "call-1", "AGT-0001", "(212) 555-0100", start, end, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            verify(agentWriter).updateAgentState("AGT-0001", "ONLINE", null);
            verify(agentWriter).updateLastCallInfo("AGT-0001", "(212) 555-0100",
                Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), 300);
            verify(callWriter).removeCall("call-1");
        }

        @Test
        void removesCallIdempotently() {
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "unknown-call"));

            var payload = new CallEndedPayload(
                "unknown-call", "AGT-0001", "(212) 555-0100",
                1700000000000L, 1700000300000L, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            // No callExists check — removeCall is idempotent
            verify(callWriter).removeCall("unknown-call");
        }

        @Test
        void skipsAgentUpdateForUnknownAgent() {
            when(agentWriter.getAgentStateAndCallId("AGT-9999"))
                .thenReturn(java.util.Arrays.asList(null, null));

            var payload = new CallEndedPayload(
                "call-1", "AGT-9999", "(212) 555-0100",
                1700000000000L, 1700000300000L, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            verify(agentWriter, never()).updateAgentState(anyString(), anyString(), any());
            verify(agentWriter, never()).updateLastCallInfo(anyString(), anyString(), any(), any(), anyLong());
            verify(callWriter).removeCall("call-1");
        }

        @Test
        void staleCallEndedDoesNotClobberCurrentCall() {
            // Agent is on call-B, but a delayed CALL_ENDED for call-A arrives
            when(agentWriter.getAgentStateAndCallId("AGT-0001"))
                .thenReturn(List.of("ON_CALL", "call-B"));

            var payload = new CallEndedPayload(
                "call-A", "AGT-0001", "(212) 555-0100",
                1700000000000L, 1700000300000L, 300, "normal_clearing", "sip-1");
            handler.handle(envelope(EventType.CALL_ENDED, payload));

            // Agent state must NOT be changed — they are still on call-B
            verify(agentWriter, never()).updateAgentState(anyString(), anyString(), any());
            verify(agentWriter, never()).updateLastCallInfo(anyString(), anyString(), any(), any(), anyLong());
            // The stale call record is still removed
            verify(callWriter).removeCall("call-A");
        }
    }

    @Nested
    class CallAbandonedTests {
        @Test
        void removesQueuedCallIdempotently() {
            var payload = new CallAbandonedPayload(
                "call-1", "(212) 555-0100", 1700000000000L, 1700000060000L, 60, "sip-1");
            handler.handle(envelope(EventType.CALL_ABANDONED, payload));

            // removeFromQueue is idempotent — no exists check
            verify(queueWriter).removeFromQueue("call-1");
            verify(broadcaster).broadcastQueue();
        }
    }

    @Nested
    class CallHoldChangedTests {
        @Test
        void updatesCallState() {
            when(callWriter.updateCallStateIfExists("call-1", "ON_HOLD")).thenReturn(true);
            var payload = new CallHoldChangedPayload("call-1", "AGT-0001", "ON_HOLD", "sip-1");
            handler.handle(envelope(EventType.CALL_HOLD_CHANGED, payload));

            verify(callWriter).updateCallStateIfExists("call-1", "ON_HOLD");
            verify(broadcaster).broadcastCalls();
        }

        @Test
        void handlesUnknownCall() {
            when(callWriter.updateCallStateIfExists("unknown", "ON_HOLD")).thenReturn(false);
            var payload = new CallHoldChangedPayload("unknown", "AGT-0001", "ON_HOLD", "sip-1");
            handler.handle(envelope(EventType.CALL_HOLD_CHANGED, payload));

            verify(callWriter).updateCallStateIfExists("unknown", "ON_HOLD");
            verify(broadcaster).broadcastCalls();
        }
    }
}
