package com.fmr.ec3.oscc.kamevapi;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.sip.AgentBreakStartedPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedInPayload;
import com.fmr.ec3.oscc.common.payload.sip.CallRoutedToAgentPayload;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KamailioEventSubscriberTest {

    private EvApiConnectionManager connectionManager;
    private EventProducer eventProducer;
    private KamailioProperties props;
    private KamailioEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        connectionManager = mock(EvApiConnectionManager.class);
        eventProducer = mock(EventProducer.class);
        props = new KamailioProperties();
        props.setServerId("kam-1");
        subscriber = new KamailioEventSubscriber(connectionManager, eventProducer, props);
    }

    @Test
    void usrlocInsertEventSendsLoggedIn() {
        String json = "{\"event\":\"usrloc:contact-insert\","
                + "\"aor\":\"sip:agent1001@sip.example.com\","
                + "\"contact\":\"sip:agent1001@10.0.0.1:5060\"}";

        subscriber.onEvApiEvent(json);

        ArgumentCaptor<AgentLoggedInPayload> captor =
                ArgumentCaptor.forClass(AgentLoggedInPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.SIP_EVENTS), eq("agent1001"),
                eq(EventType.AGENT_LOGGED_IN), eq("kam-1"), eq("agent1001"),
                captor.capture());

        AgentLoggedInPayload payload = captor.getValue();
        assertEquals("agent1001", payload.agentId());
        assertEquals("kam-1", payload.sipServerId());
    }

    @Test
    void presenceBreakEventSendsBreakStarted() {
        String json = "{\"event\":\"presence:update\","
                + "\"aor\":\"sip:agent1001@sip.example.com\","
                + "\"status\":\"away\"}";

        subscriber.onEvApiEvent(json);

        ArgumentCaptor<AgentBreakStartedPayload> captor =
                ArgumentCaptor.forClass(AgentBreakStartedPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.SIP_EVENTS), eq("agent1001"),
                eq(EventType.AGENT_BREAK_STARTED), eq("kam-1"), eq("agent1001"),
                captor.capture());

        assertEquals("away", captor.getValue().breakType());
    }

    @Test
    void dialogStartSendsCallRouted() {
        String json = "{\"event\":\"dialog:start\","
                + "\"callid\":\"abc-123\","
                + "\"callee\":\"sip:agent1001@sip.example.com\","
                + "\"caller\":\"sip:+15551234567@carrier.com\"}";

        subscriber.onEvApiEvent(json);

        ArgumentCaptor<CallRoutedToAgentPayload> captor =
                ArgumentCaptor.forClass(CallRoutedToAgentPayload.class);
        verify(eventProducer).send(
                eq(KafkaTopics.SIP_EVENTS), eq("agent1001"),
                eq(EventType.CALL_ROUTED_TO_AGENT), eq("kam-1"), eq("agent1001"),
                captor.capture());

        CallRoutedToAgentPayload payload = captor.getValue();
        assertEquals("abc-123", payload.callId());
        assertEquals("agent1001", payload.agentId());
    }

    @Test
    void invalidJsonLogsWarning() {
        subscriber.onEvApiEvent("not json at all");

        verifyNoInteractions(eventProducer);
    }

    @Test
    void missingEventFieldIgnored() {
        subscriber.onEvApiEvent("{\"aor\":\"sip:agent1001@example.com\"}");

        verifyNoInteractions(eventProducer);
    }

    @Test
    void unknownEventIgnored() {
        subscriber.onEvApiEvent("{\"event\":\"some:unknown\","
                + "\"aor\":\"sip:agent1001@example.com\"}");

        verifyNoInteractions(eventProducer);
    }

    @Test
    void initRegistersListenerAndCallback() {
        subscriber.init();

        verify(connectionManager).setEventListener(any());
        verify(connectionManager).addOnConnectedCallback(any());
    }
}
