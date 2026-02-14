package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.sip.*;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import com.fmr.ec3.oscc.sender.model.SimulatedAgent;
import com.fmr.ec3.oscc.sender.model.SimulatedAgent.State;
import com.fmr.ec3.oscc.sender.model.SimulatedCall;
import com.fmr.ec3.oscc.sender.model.SimulatedQueuedCall;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SipServerSimulator {

    private static final Logger log = LoggerFactory.getLogger(SipServerSimulator.class);

    private final EventProducer eventProducer;
    private final SimulationProperties props;

    private final Map<String, SimulatedAgent> agents = new ConcurrentHashMap<>();
    private final Map<String, SimulatedCall> activeCalls = new ConcurrentHashMap<>();
    private final Map<String, SimulatedQueuedCall> queuedCalls = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random random = new Random();

    private static final String SOURCE_ID = "sip-sim-1";

    // Same parameters as SimulationService
    private static final int NEW_QUEUE_CALL_CHANCE_PERCENT = 40;
    private static final int ANSWER_QUEUE_CHANCE_PERCENT = 70;
    private static final int END_CALL_CHANCE_PERCENT = 8;
    private static final int HOLD_TOGGLE_CHANCE_PERCENT = 5;
    private static final int BREAK_START_CHANCE_PERCENT = 2;
    private static final int BREAK_END_CHANCE_PERCENT = 30;
    private static final int LOGOUT_CHANCE_PERCENT = 1;
    private static final int LOGIN_CHANCE_PERCENT = 20;
    private static final double MIN_ON_CALL_RATIO = 0.50;
    private static final int MAX_QUEUE_SIZE = 30;

    private static final String[] SKILLS = {"Sales", "Support", "Billing", "Technical"};
    private static final String[] AREA_CODES = {
        "212", "213", "214", "312", "313", "404", "415", "469",
        "480", "503", "512", "602", "619", "650", "702", "713",
        "718", "720", "732", "773", "786", "818", "832", "847",
        "858", "917", "925", "949", "954", "972"
    };
    private static final String[] BREAK_TYPES = {"Lunch", "Coffee", "Personal", "Training"};
    private static final String[] LOGOUT_REASONS = {"end_of_shift", "voluntary", "system_maintenance"};
    private static final String[] CALL_END_REASONS = {"normal_clearing", "caller_hangup", "agent_hangup", "transfer"};

    private static final String[] FIRST_NAMES = {
        "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
        "David", "Elizabeth", "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
        "Thomas", "Sarah", "Christopher", "Karen", "Charles", "Lisa", "Daniel", "Nancy",
        "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
        "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle",
        "Kenneth", "Dorothy", "Kevin", "Carol", "Brian", "Amanda", "George", "Melissa",
        "Timothy", "Deborah", "Ronald", "Stephanie", "Edward", "Rebecca", "Jason", "Sharon",
        "Jeffrey", "Laura", "Ryan", "Cynthia", "Jacob", "Kathleen", "Gary", "Amy",
        "Nicholas", "Angela", "Eric", "Shirley", "Jonathan", "Anna", "Stephen", "Brenda",
        "Larry", "Pamela", "Justin", "Emma", "Scott", "Nicole", "Brandon", "Helen",
        "Benjamin", "Samantha", "Samuel", "Katherine", "Raymond", "Christine", "Gregory", "Debra",
        "Frank", "Rachel", "Alexander", "Carolyn", "Patrick", "Janet", "Jack", "Catherine",
        "Dennis", "Maria", "Jerry", "Heather", "Tyler", "Diane", "Aaron", "Ruth",
        "Jose", "Julie", "Adam", "Olivia", "Nathan", "Joyce", "Henry", "Virginia",
        "Douglas", "Victoria", "Zachary", "Kelly", "Peter", "Lauren", "Kyle", "Christina",
        "Noah", "Joan", "Ethan", "Evelyn", "Jeremy", "Judith", "Walter", "Megan",
        "Christian", "Andrea", "Keith", "Cheryl", "Roger", "Hannah", "Terry", "Jacqueline",
        "Austin", "Martha", "Sean", "Gloria", "Gerald", "Teresa", "Carl", "Ann",
        "Harold", "Sara", "Dylan", "Madison", "Arthur", "Frances", "Lawrence", "Kathryn",
        "Jordan", "Janice", "Jesse", "Jean", "Bryan", "Abigail", "Billy", "Alice"
    };
    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
        "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young",
        "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
        "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell",
        "Carter", "Roberts", "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker",
        "Cruz", "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales", "Murphy",
        "Cook", "Rogers", "Gutierrez", "Ortiz", "Morgan", "Cooper", "Peterson", "Bailey",
        "Reed", "Kelly", "Howard", "Ramos", "Kim", "Cox", "Ward", "Richardson",
        "Watson", "Brooks", "Chavez", "Wood", "James", "Bennett", "Gray", "Mendoza",
        "Ruiz", "Hughes", "Price", "Alvarez", "Castillo", "Sanders", "Patel", "Myers",
        "Long", "Ross", "Foster", "Jimenez"
    };

    public SipServerSimulator(EventProducer eventProducer, SimulationProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        generateAgents();
        loginAgents();
        running.set(true);
        log.info("SipServerSimulator initialized: {} agents, {} logged in",
            agents.size(), agents.values().stream().filter(a -> a.getState() != State.UNAVAILABLE).count());
    }

    @Scheduled(fixedDelayString = "${simulation.tick-interval-ms:2000}")
    public void tick() {
        if (!running.get()) {
            return;
        }

        try {
            long totalAgents = agents.size();
            long onCallAgents = agents.values().stream().filter(a -> a.getState() == State.ON_CALL).count();
            double onCallRatio = totalAgents > 0 ? (double) onCallAgents / totalAgents : 0;

            simulateIncomingCalls();
            simulateCallRouting(onCallRatio);
            simulateCallEndings(onCallRatio);
            simulateHoldToggle();
            simulateBreaks(onCallRatio);
            simulateLogins();
        } catch (Exception e) {
            log.error("Simulation tick error: {}", e.getMessage(), e);
        }
    }

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getAgentCount() {
        return agents.size();
    }

    public int getActiveCallCount() {
        return activeCalls.size();
    }

    public int getQueuedCallCount() {
        return queuedCalls.size();
    }

    private void generateAgents() {
        for (int i = 1; i <= props.getAgentCount(); i++) {
            String id = String.format("AGT-%04d", i);
            String name = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " "
                        + LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            List<String> skills = List.of(SKILLS[random.nextInt(SKILLS.length)]);
            agents.put(id, new SimulatedAgent(id, name, skills, State.UNAVAILABLE));
        }
    }

    private void loginAgents() {
        List<SimulatedAgent> allAgents = new ArrayList<>(agents.values());
        Collections.shuffle(allAgents, random);
        int loginCount = (int) (allAgents.size() * props.getLoginPercent() / 100.0);

        for (int i = 0; i < loginCount; i++) {
            SimulatedAgent agent = allAgents.get(i);
            agent.setState(State.ONLINE);
            eventProducer.send(
                KafkaTopics.SIP_EVENTS, agent.getId(),
                EventType.AGENT_LOGGED_IN, SOURCE_ID, agent.getId(),
                new AgentLoggedInPayload(agent.getId(), agent.getName(), agent.getSkills(), SOURCE_ID)
            );
        }
    }

    private void simulateIncomingCalls() {
        if (queuedCalls.size() >= MAX_QUEUE_SIZE) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            if (random.nextInt(100) < NEW_QUEUE_CALL_CHANCE_PERCENT) {
                String callId = UUID.randomUUID().toString();
                String originator = generatePhoneNumber();
                String skill = SKILLS[random.nextInt(SKILLS.length)];
                int priority = random.nextInt(100) < 10 ? 1 : (random.nextInt(100) < 30 ? 2 : 3);
                long now = System.currentTimeMillis();

                queuedCalls.put(callId, new SimulatedQueuedCall(callId, originator, skill, priority, now));

                eventProducer.send(
                    KafkaTopics.SIP_EVENTS, originator,
                    EventType.CALL_QUEUED, SOURCE_ID, callId,
                    new CallQueuedPayload(callId, originator, skill, priority, now, SOURCE_ID)
                );
            }
        }
    }

    private void simulateCallRouting(double currentOnCallRatio) {
        if (queuedCalls.isEmpty()) {
            return;
        }

        List<SimulatedAgent> onlineAgents = agents.values().stream()
            .filter(a -> a.getState() == State.ONLINE)
            .toList();
        if (onlineAgents.isEmpty()) {
            return;
        }

        int effectiveChance = ANSWER_QUEUE_CHANCE_PERCENT;
        if (currentOnCallRatio < MIN_ON_CALL_RATIO) {
            effectiveChance = 95;
        }

        List<SimulatedQueuedCall> queued = new ArrayList<>(queuedCalls.values());
        int callsToAnswer = Math.min(onlineAgents.size(), queued.size());

        for (int i = 0; i < callsToAnswer; i++) {
            if (random.nextInt(100) < effectiveChance) {
                SimulatedQueuedCall queuedCall = queued.get(i);
                SimulatedAgent agent = onlineAgents.get(random.nextInt(onlineAgents.size()));

                if (agent.getState() != State.ONLINE) {
                    continue;
                }

                String callId = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();

                // Update internal state
                queuedCalls.remove(queuedCall.getCallId());
                agent.setState(State.ON_CALL);
                agent.setCurrentCallId(callId);
                activeCalls.put(callId, new SimulatedCall(
                    callId, agent.getId(), agent.getName(), queuedCall.getOriginator(), now));

                eventProducer.send(
                    KafkaTopics.SIP_EVENTS, agent.getId(),
                    EventType.CALL_ROUTED_TO_AGENT, SOURCE_ID, callId,
                    new CallRoutedToAgentPayload(
                        callId, agent.getId(), agent.getName(),
                        queuedCall.getOriginator(), queuedCall.getCallId(),
                        queuedCall.getSkill(), now, SOURCE_ID)
                );
            }
        }
    }

    private void simulateCallEndings(double currentOnCallRatio) {
        long now = System.currentTimeMillis();

        for (SimulatedCall call : new ArrayList<>(activeCalls.values())) {
            long durationSeconds = (now - call.getStartTimeMs()) / 1000;

            if (currentOnCallRatio <= MIN_ON_CALL_RATIO + 0.05) {
                if (durationSeconds > 900 && random.nextInt(100) < 5) {
                    endCall(call, now, durationSeconds);
                }
                continue;
            }

            int endChance = END_CALL_CHANCE_PERCENT;
            if (durationSeconds > 300) {
                endChance += 5;
            }
            if (durationSeconds > 600) {
                endChance += 10;
            }
            if (durationSeconds > 900) {
                endChance += 15;
            }

            if (random.nextInt(100) < endChance) {
                endCall(call, now, durationSeconds);
            }
        }
    }

    private void endCall(SimulatedCall call, long endTimeMs, long durationSeconds) {
        activeCalls.remove(call.getCallId());
        SimulatedAgent agent = agents.get(call.getAgentId());
        if (agent != null) {
            agent.setState(State.ONLINE);
            agent.setCurrentCallId(null);
        }

        eventProducer.send(
            KafkaTopics.SIP_EVENTS, call.getAgentId(),
            EventType.CALL_ENDED, SOURCE_ID, call.getCallId(),
            new CallEndedPayload(
                call.getCallId(), call.getAgentId(), call.getOriginator(),
                call.getStartTimeMs(), endTimeMs, durationSeconds,
                CALL_END_REASONS[random.nextInt(CALL_END_REASONS.length)], SOURCE_ID)
        );
    }

    private void simulateHoldToggle() {
        for (SimulatedCall call : new ArrayList<>(activeCalls.values())) {
            if (random.nextInt(100) < HOLD_TOGGLE_CHANCE_PERCENT) {
                String newState = call.isOnHold() ? "TALKING" : "ON_HOLD";
                call.setOnHold(!call.isOnHold());

                eventProducer.send(
                    KafkaTopics.SIP_EVENTS, call.getAgentId(),
                    EventType.CALL_HOLD_CHANGED, SOURCE_ID, call.getCallId(),
                    new CallHoldChangedPayload(call.getCallId(), call.getAgentId(), newState, SOURCE_ID)
                );
            }
        }
    }

    private void simulateBreaks(double currentOnCallRatio) {
        for (SimulatedAgent agent : new ArrayList<>(agents.values())) {
            if (agent.getState() == State.ONLINE) {
                if (currentOnCallRatio < MIN_ON_CALL_RATIO + 0.05) {
                    continue;
                }

                if (random.nextInt(100) < BREAK_START_CHANCE_PERCENT) {
                    agent.setState(State.AWAY);
                    eventProducer.send(
                        KafkaTopics.SIP_EVENTS, agent.getId(),
                        EventType.AGENT_BREAK_STARTED, SOURCE_ID, agent.getId(),
                        new AgentBreakStartedPayload(
                            agent.getId(), BREAK_TYPES[random.nextInt(BREAK_TYPES.length)], SOURCE_ID)
                    );
                }
            } else if (agent.getState() == State.AWAY) {
                long awaySeconds = (System.currentTimeMillis() - agent.getStateChangedAtMs()) / 1000;
                int returnChance = BREAK_END_CHANCE_PERCENT;
                if (currentOnCallRatio < MIN_ON_CALL_RATIO) {
                    returnChance += 40;
                }
                if (awaySeconds > 300) {
                    returnChance += 15;
                }
                if (awaySeconds > 600) {
                    returnChance += 25;
                }

                if (random.nextInt(100) < returnChance) {
                    agent.setState(State.ONLINE);
                    eventProducer.send(
                        KafkaTopics.SIP_EVENTS, agent.getId(),
                        EventType.AGENT_BREAK_ENDED, SOURCE_ID, agent.getId(),
                        new AgentBreakEndedPayload(agent.getId(), SOURCE_ID)
                    );
                }
            }
        }
    }

    private void simulateLogins() {
        for (SimulatedAgent agent : new ArrayList<>(agents.values())) {
            if (agent.getState() == State.ONLINE) {
                if (random.nextInt(100) < LOGOUT_CHANCE_PERCENT) {
                    agent.setState(State.UNAVAILABLE);
                    eventProducer.send(
                        KafkaTopics.SIP_EVENTS, agent.getId(),
                        EventType.AGENT_LOGGED_OUT, SOURCE_ID, agent.getId(),
                        new AgentLoggedOutPayload(
                            agent.getId(), LOGOUT_REASONS[random.nextInt(LOGOUT_REASONS.length)], SOURCE_ID)
                    );
                }
            } else if (agent.getState() == State.UNAVAILABLE) {
                long unavailableSeconds = (System.currentTimeMillis() - agent.getStateChangedAtMs()) / 1000;
                int loginChance = LOGIN_CHANCE_PERCENT;
                if (unavailableSeconds > 120) {
                    loginChance += 10;
                }
                if (unavailableSeconds > 300) {
                    loginChance += 20;
                }

                if (random.nextInt(100) < loginChance) {
                    agent.setState(State.ONLINE);
                    eventProducer.send(
                        KafkaTopics.SIP_EVENTS, agent.getId(),
                        EventType.AGENT_LOGGED_IN, SOURCE_ID, agent.getId(),
                        new AgentLoggedInPayload(agent.getId(), agent.getName(), agent.getSkills(), SOURCE_ID)
                    );
                }
            }
        }
    }

    private String generatePhoneNumber() {
        String areaCode = AREA_CODES[random.nextInt(AREA_CODES.length)];
        int exchange = 200 + random.nextInt(800);
        int subscriber = random.nextInt(10000);
        return String.format("(%s) %d-%04d", areaCode, exchange, subscriber);
    }
}
