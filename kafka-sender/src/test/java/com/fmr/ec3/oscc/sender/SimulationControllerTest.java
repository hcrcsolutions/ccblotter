package com.fmr.ec3.oscc.sender;

import com.fmr.ec3.oscc.sender.simulator.SipServerSimulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationControllerTest {

    @Mock
    private SipServerSimulator sipSimulator;

    @InjectMocks
    private SimulationController controller;

    @Test
    void startCallsSimulatorStart() {
        Map<String, String> result = controller.start();
        verify(sipSimulator).start();
        assertEquals("started", result.get("status"));
    }

    @Test
    void stopCallsSimulatorStop() {
        Map<String, String> result = controller.stop();
        verify(sipSimulator).stop();
        assertEquals("stopped", result.get("status"));
    }

    @Test
    void statusReturnsSimulatorState() {
        when(sipSimulator.isRunning()).thenReturn(true);
        when(sipSimulator.getAgentCount()).thenReturn(400);
        when(sipSimulator.getActiveCallCount()).thenReturn(150);
        when(sipSimulator.getQueuedCallCount()).thenReturn(12);

        Map<String, Object> result = controller.status();

        assertEquals(true, result.get("running"));
        assertEquals(400, result.get("agents"));
        assertEquals(150, result.get("activeCalls"));
        assertEquals(12, result.get("queuedCalls"));
    }
}
