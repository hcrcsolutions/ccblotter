package com.fmr.ec3.oscc.sender;

import com.fmr.ec3.oscc.sender.simulator.IvrSessionSimulator;
import com.fmr.ec3.oscc.sender.simulator.SipServerSimulator;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SipServerSimulator sipSimulator;
    private final IvrSessionSimulator ivrSimulator;

    public SimulationController(SipServerSimulator sipSimulator,
                                IvrSessionSimulator ivrSimulator) {
        this.sipSimulator = sipSimulator;
        this.ivrSimulator = ivrSimulator;
    }

    @PostMapping("/start")
    public Map<String, String> start() {
        sipSimulator.start();
        ivrSimulator.start();
        return Map.of("status", "started");
    }

    @PostMapping("/stop")
    public Map<String, String> stop() {
        sipSimulator.stop();
        ivrSimulator.stop();
        return Map.of("status", "stopped");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "running", sipSimulator.isRunning(),
            "agents", sipSimulator.getAgentCount(),
            "activeCalls", sipSimulator.getActiveCallCount(),
            "queuedCalls", sipSimulator.getQueuedCallCount(),
            "ivrSessions", ivrSimulator.getActiveSessionCount()
        );
    }
}
