package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedInPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SipServerSimulatorTest {

    @Mock
    private EventProducer eventProducer;

    private SimulationProperties props;
    private SipServerSimulator simulator;

    @BeforeEach
    void setUp() {
        props = new SimulationProperties();
        props.setAgentCount(10);
        props.setLoginPercent(80);

        simulator = new SipServerSimulator(eventProducer, props);
    }

    @Test
    void initGeneratesAgentsAndLogsThemIn() {
        simulator.init();

        assertEquals(10, simulator.getAgentCount());
        // 80% of 10 = 8 agents should receive AGENT_LOGGED_IN
        verify(eventProducer, times(8)).send(
            eq(KafkaTopics.SIP_EVENTS), anyString(),
            eq(EventType.AGENT_LOGGED_IN), anyString(), anyString(),
            any(AgentLoggedInPayload.class));
    }

    @Test
    void initSetsRunningTrue() {
        simulator.init();
        assertTrue(simulator.isRunning());
    }

    @Test
    void stopPreventsTickExecution() {
        simulator.init();
        reset(eventProducer);

        simulator.stop();
        assertFalse(simulator.isRunning());

        simulator.tick();
        // No events should be produced when stopped
        verifyNoInteractions(eventProducer);
    }

    @Test
    void startResumesTickExecution() {
        simulator.init();
        simulator.stop();
        simulator.start();
        assertTrue(simulator.isRunning());
    }

    @Test
    void tickProducesEvents() {
        simulator.init();
        reset(eventProducer);

        // Run several ticks - with 10 agents, something should happen
        for (int i = 0; i < 20; i++) {
            simulator.tick();
        }

        // At least some events should have been produced (queue calls, routing, etc.)
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.SIP_EVENTS), anyString(), anyString(),
            anyString(), anyString(), any());
    }

    @Test
    void agentLoggedInPayloadContainsSkills() {
        simulator.init();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventProducer, atLeastOnce()).send(
            eq(KafkaTopics.SIP_EVENTS), anyString(),
            eq(EventType.AGENT_LOGGED_IN), anyString(), anyString(),
            payloadCaptor.capture());

        for (Object payload : payloadCaptor.getAllValues()) {
            AgentLoggedInPayload p = (AgentLoggedInPayload) payload;
            assertNotNull(p.agentId());
            assertTrue(p.agentId().startsWith("AGT-"));
            assertNotNull(p.agentName());
            assertTrue(p.agentName().contains(" ")); // First + Last name
            assertFalse(p.skills().isEmpty());
        }
    }

    @Test
    void initialStateCountsAreCorrect() {
        simulator.init();

        assertEquals(10, simulator.getAgentCount());
        assertEquals(0, simulator.getActiveCallCount());
        assertEquals(0, simulator.getQueuedCallCount());
    }
}
