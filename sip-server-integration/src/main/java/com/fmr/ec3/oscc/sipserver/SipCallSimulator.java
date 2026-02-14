package com.fmr.ec3.oscc.sipserver;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.sip.*;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sipserver.config.SipServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SipCallSimulator {

    private static final Logger log = LoggerFactory.getLogger(SipCallSimulator.class);

    private final EventProducer eventProducer;
    private final SipServerProperties props;
    private final Random random = new Random();

    private final Map<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();
    private final Set<String> loggedInAgents = ConcurrentHashMap.newKeySet();

    private static final String[] SKILLS = {"Sales", "Support", "Billing", "Technical"};
    private static final String[] AREA_CODES = {"212", "312", "415", "469", "602", "713", "786", "917"};
    private static final int MAX_AGENTS = 10;

    public SipCallSimulator(EventProducer eventProducer, SipServerProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${sip-server.call-sim-interval-ms:3000}", initialDelay = 5000)
    public void simulateTick() {
        String sourceId = props.getNodeId();

        // Ensure some agents are logged in
        if (loggedInAgents.size() < MAX_AGENTS / 2) {
            loginAgent(sourceId);
        }

        // Simulate incoming call
        if (random.nextInt(100) < 60) {
            queueCall(sourceId);
        }

        // Route queued calls are handled inline — simulate direct route
        if (random.nextInt(100) < 50 && !loggedInAgents.isEmpty()) {
            routeCall(sourceId);
        }

        // End some active calls
        for (ActiveCall call : new ArrayList<>(activeCalls.values())) {
            long durationSec = (System.currentTimeMillis() - call.startTimeMs) / 1000;
            if (durationSec > 30 && random.nextInt(100) < 20) {
                endCall(call, sourceId);
            }
        }

        log.debug("Tick: agents={}, activeCalls={}", loggedInAgents.size(), activeCalls.size());
    }

    private void loginAgent(String sourceId) {
        String agentId = String.format("AGT-%04d", random.nextInt(9000) + 1000);
        if (loggedInAgents.contains(agentId)) return;

        loggedInAgents.add(agentId);
        String name = "Agent " + agentId;
        List<String> skills = List.of(SKILLS[random.nextInt(SKILLS.length)]);

        eventProducer.send(
            KafkaTopics.SIP_EVENTS, agentId,
            EventType.AGENT_LOGGED_IN, sourceId, agentId,
            new AgentLoggedInPayload(agentId, name, skills, sourceId)
        );
    }

    private void queueCall(String sourceId) {
        String callId = UUID.randomUUID().toString();
        String originator = generatePhoneNumber();
        String skill = SKILLS[random.nextInt(SKILLS.length)];
        int priority = random.nextInt(100) < 10 ? 1 : 3;
        long now = System.currentTimeMillis();

        eventProducer.send(
            KafkaTopics.SIP_EVENTS, originator,
            EventType.CALL_QUEUED, sourceId, callId,
            new CallQueuedPayload(callId, originator, skill, priority, now, sourceId)
        );
    }

    private void routeCall(String sourceId) {
        String callId = UUID.randomUUID().toString();
        String queuedCallId = UUID.randomUUID().toString();
        String agentId = loggedInAgents.iterator().next();
        String originator = generatePhoneNumber();
        String skill = SKILLS[random.nextInt(SKILLS.length)];
        long now = System.currentTimeMillis();

        activeCalls.put(callId, new ActiveCall(callId, agentId, originator, now));

        eventProducer.send(
            KafkaTopics.SIP_EVENTS, agentId,
            EventType.CALL_ROUTED_TO_AGENT, sourceId, callId,
            new CallRoutedToAgentPayload(callId, agentId, "Agent " + agentId, originator,
                queuedCallId, skill, now, sourceId)
        );
    }

    private void endCall(ActiveCall call, String sourceId) {
        activeCalls.remove(call.callId);
        long now = System.currentTimeMillis();
        long durationSec = (now - call.startTimeMs) / 1000;

        eventProducer.send(
            KafkaTopics.SIP_EVENTS, call.agentId,
            EventType.CALL_ENDED, sourceId, call.callId,
            new CallEndedPayload(call.callId, call.agentId, call.originator,
                call.startTimeMs, now, durationSec, "normal_clearing", sourceId)
        );
    }

    private String generatePhoneNumber() {
        String areaCode = AREA_CODES[random.nextInt(AREA_CODES.length)];
        return String.format("(%s) %d-%04d", areaCode, 200 + random.nextInt(800), random.nextInt(10000));
    }

    private record ActiveCall(String callId, String agentId, String originator, long startTimeMs) {}
}
