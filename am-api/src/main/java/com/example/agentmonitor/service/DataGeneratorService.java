package com.example.agentmonitor.service;

import com.example.agentmonitor.model.Agent;
import com.example.agentmonitor.model.AgentState;
import com.example.agentmonitor.model.Call;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service to generate realistic test data for 400 call center agents.
 *
 * Distribution:
 * - 50% ONLINE (ready for calls)
 * - 25% ON_CALL (active calls)
 * - 15% AWAY (breaks)
 * - 10% UNAVAILABLE (logged out)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataGeneratorService {

    private final AgentService agentService;
    private final CallService callService;
    private final QueueService queueService;

    // Realistic first names
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

    // Realistic last names
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

    // Area codes for realistic phone numbers (US)
    private static final String[] AREA_CODES = {
        "212", "213", "214", "215", "216", "217", "218", "219",
        "312", "313", "314", "315", "316", "317", "318", "319",
        "404", "405", "406", "407", "408", "409", "410", "412",
        "415", "469", "480", "503", "512", "602", "619", "626",
        "650", "702", "713", "718", "720", "732", "747", "773",
        "786", "818", "832", "847", "858", "862", "909", "917",
        "925", "949", "954", "972"
    };

    private final Random random = new Random();

    /**
     * Generate 400 agents with realistic distribution of states.
     * Also creates active calls for agents in ON_CALL state.
     */
    public void generateData() {
        log.info("Starting data generation for 400 agents...");

        // Clear existing data first
        clearExistingData();

        List<Agent> agents = new ArrayList<>();
        List<String> onCallAgentIds = new ArrayList<>();

        // Generate 400 agents
        for (int i = 1; i <= 400; i++) {
            String id = String.format("AGT-%04d", i);
            String name = generateAgentName();
            AgentState state = determineAgentState(i);

            Agent.AgentBuilder builder = Agent.builder()
                    .id(id)
                    .name(name)
                    .state(state)
                    .stateChangedAt(generateStateChangedTime(state));

            // Add last call history for agents not currently on a call
            // 85% of non-ON_CALL agents have previous call history
            if (state != AgentState.ON_CALL && random.nextInt(100) < 85) {
                Instant lastCallEnd = generateLastCallEndTime(state);
                long lastCallDuration = 60 + random.nextInt(1740); // 1-30 minutes
                Instant lastCallStart = lastCallEnd.minus(lastCallDuration, ChronoUnit.SECONDS);

                builder.lastCallOriginator(generatePhoneNumber())
                       .lastCallStartTime(lastCallStart)
                       .lastCallEndTime(lastCallEnd)
                       .lastCallDurationSeconds(lastCallDuration);
            }

            Agent agent = builder.build();

            // Track ON_CALL agents for call generation
            if (state == AgentState.ON_CALL) {
                onCallAgentIds.add(id);
            }

            agents.add(agent);
        }

        // Save all agents (except ON_CALL which need calls first)
        for (Agent agent : agents) {
            if (agent.getState() != AgentState.ON_CALL) {
                agentService.saveAgent(agent);
            }
        }

        // Save ON_CALL agents with their calls
        for (String agentId : onCallAgentIds) {
            Agent agent = agents.stream()
                    .filter(a -> a.getId().equals(agentId))
                    .findFirst()
                    .orElseThrow();

            // First save agent as ONLINE (required state to start a call)
            agent.setState(AgentState.ONLINE);
            agentService.saveAgent(agent);

            // Start a call for this agent
            String originator = generatePhoneNumber();
            Call call = callService.startCall(originator, agentId);

            // Backdate the call start time for realistic duration
            backdateCallStartTime(call.getId(), generateCallStartTime());
        }

        log.info("Data generation complete. Created {} agents with {} active calls.",
                agents.size(), onCallAgentIds.size());

        // Broadcast updates
        agentService.broadcastAgents();
        agentService.broadcastSummary();
        callService.broadcastCalls();
    }

    private void clearExistingData() {
        log.info("Clearing existing data...");
        // Get all agents and remove them
        try {
            List<Agent> existingAgents = agentService.getAllAgents();
            for (Agent agent : existingAgents) {
                agentService.removeAgent(agent.getId());
            }
        } catch (Exception e) {
            log.warn("Error clearing existing agents: {}", e.getMessage());
        }
        // Clear the queue
        try {
            queueService.clearQueue();
        } catch (Exception e) {
            log.warn("Error clearing queue: {}", e.getMessage());
        }
    }

    private String generateAgentName() {
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        return firstName + " " + lastName;
    }

    /**
     * Determine agent state based on distribution:
     * - 30% ONLINE
     * - 55% ON_CALL (at least half always on call)
     * - 10% AWAY
     * - 5% UNAVAILABLE
     */
    private AgentState determineAgentState(int index) {
        int bucket = index % 100;
        if (bucket < 30) {
            return AgentState.ONLINE;
        } else if (bucket < 85) {
            return AgentState.ON_CALL;
        } else if (bucket < 95) {
            return AgentState.AWAY;
        } else {
            return AgentState.UNAVAILABLE;
        }
    }

    /**
     * Generate realistic state change time based on state.
     */
    private Instant generateStateChangedTime(AgentState state) {
        Instant now = Instant.now();
        return switch (state) {
            case ONLINE -> now.minus(random.nextInt(120), ChronoUnit.MINUTES); // 0-2 hours ago
            case ON_CALL -> now.minus(random.nextInt(30), ChronoUnit.MINUTES); // 0-30 min ago (call start)
            case AWAY -> now.minus(random.nextInt(15), ChronoUnit.MINUTES); // 0-15 min ago (recent break)
            case UNAVAILABLE -> now.minus(random.nextInt(480), ChronoUnit.MINUTES); // 0-8 hours ago
        };
    }

    /**
     * Generate a realistic US phone number.
     */
    private String generatePhoneNumber() {
        String areaCode = AREA_CODES[random.nextInt(AREA_CODES.length)];
        int exchange = 200 + random.nextInt(800); // 200-999
        int subscriber = random.nextInt(10000);
        return String.format("(%s) %d-%04d", areaCode, exchange, subscriber);
    }

    /**
     * Generate realistic call start time (1-45 minutes ago).
     */
    private Instant generateCallStartTime() {
        int minutesAgo = 1 + random.nextInt(45);
        return Instant.now().minus(minutesAgo, ChronoUnit.MINUTES);
    }

    /**
     * Generate realistic last call end time based on current agent state.
     * - ONLINE agents: last call ended recently (they're ready for next call)
     * - AWAY agents: last call ended just before they went on break
     * - UNAVAILABLE agents: last call could have ended hours ago
     */
    private Instant generateLastCallEndTime(AgentState state) {
        Instant now = Instant.now();
        return switch (state) {
            case ONLINE -> now.minus(1 + random.nextInt(60), ChronoUnit.MINUTES); // 1-60 min ago (idle time)
            case AWAY -> now.minus(5 + random.nextInt(20), ChronoUnit.MINUTES); // 5-25 min ago (before break)
            case UNAVAILABLE -> now.minus(30 + random.nextInt(420), ChronoUnit.MINUTES); // 30 min - 7.5 hours ago
            case ON_CALL -> now; // Should not happen, but handle gracefully
        };
    }

    /**
     * Backdate a call's start time for realistic duration display.
     */
    private void backdateCallStartTime(String callId, Instant newStartTime) {
        callService.updateCallStartTime(callId, newStartTime);
    }
}
