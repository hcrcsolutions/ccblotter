package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FreeSwitchProbe {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchProbe.class);

    private static final Pattern SESSION_PATTERN =
            Pattern.compile("(\\d+) session\\(s\\) - peak (\\d+)");
    private static final Pattern SESSION_PER_SEC_PATTERN =
            Pattern.compile("(\\d+) session\\(s\\) per Sec out of max (\\d+)");
    private static final Pattern IDLE_CPU_PATTERN =
            Pattern.compile("min idle cpu ([\\d.]+)/([\\d.]+)");
    private static final Pattern TOTAL_PATTERN =
            Pattern.compile("(\\d+)\\s+total");

    private final EslConnectionManager connectionManager;
    private final FreeSwitchProperties props;
    private final OperatingSystemMXBean osMxBean;

    public FreeSwitchProbe(EslConnectionManager connectionManager, FreeSwitchProperties props) {
        this.connectionManager = connectionManager;
        this.props = props;
        this.osMxBean = ManagementFactory.getOperatingSystemMXBean();
    }

    public record StatusResult(
            int currentSessions,
            int peakSessions,
            int sessionsPerSecond,
            int maxSessions,
            double idleCpu
    ) {}

    public Optional<StatusResult> fetchStatus() {
        return connectionManager.sendApi("status").flatMap(this::parseStatus);
    }

    public int fetchChannelCount() {
        return connectionManager.sendApi("show channels count")
                .map(this::parseCountResponse)
                .orElse(-1);
    }

    public int fetchCallCount() {
        return connectionManager.sendApi("show calls count")
                .map(this::parseCountResponse)
                .orElse(-1);
    }

    public boolean moduleExists(String moduleName) {
        return connectionManager.sendApi("module_exists " + moduleName)
                .map(response -> "true".equalsIgnoreCase(response.trim()))
                .orElse(false);
    }

    public String detectNodeType() {
        if (!"AUTO".equalsIgnoreCase(props.getNodeType())) {
            return props.getNodeType();
        }
        if (!connectionManager.isConnected()) {
            return "SIP";
        }
        boolean hasSofia = moduleExists("mod_sofia");
        boolean hasConference = moduleExists("mod_conference");
        boolean hasVerto = moduleExists("mod_verto");

        if (hasSofia) {
            return "SIP";
        }
        if (hasConference || hasVerto) {
            return "MEDIA";
        }
        return "SIP";
    }

    public double getMemoryPercent() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            long total = sunBean.getTotalMemorySize();
            long free = sunBean.getFreeMemorySize();
            if (total > 0) {
                return Math.round((double) (total - free) / total * 1000.0) / 10.0;
            }
        }
        return 0;
    }

    Optional<StatusResult> parseStatus(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        try {
            int currentSessions = 0;
            int peakSessions = 0;
            int sessionsPerSecond = 0;
            int maxSessions = 0;
            double idleCpu = 100;

            Matcher sessionMatcher = SESSION_PATTERN.matcher(response);
            if (sessionMatcher.find()) {
                currentSessions = Integer.parseInt(sessionMatcher.group(1));
                peakSessions = Integer.parseInt(sessionMatcher.group(2));
            }

            Matcher spsMatch = SESSION_PER_SEC_PATTERN.matcher(response);
            if (spsMatch.find()) {
                sessionsPerSecond = Integer.parseInt(spsMatch.group(1));
                maxSessions = Integer.parseInt(spsMatch.group(2));
            }

            Matcher cpuMatcher = IDLE_CPU_PATTERN.matcher(response);
            if (cpuMatcher.find()) {
                idleCpu = Double.parseDouble(cpuMatcher.group(2));
            }

            return Optional.of(new StatusResult(
                    currentSessions, peakSessions, sessionsPerSecond, maxSessions, idleCpu
            ));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse ESL status response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    int parseCountResponse(String response) {
        if (response == null || response.isBlank()) {
            return -1;
        }
        Matcher matcher = TOTAL_PATTERN.matcher(response);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }
}
