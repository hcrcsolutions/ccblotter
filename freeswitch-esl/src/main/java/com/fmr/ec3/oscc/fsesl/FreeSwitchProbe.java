package com.fmr.ec3.oscc.fsesl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.common.payload.infra.CodecUsageDto;
import com.fmr.ec3.oscc.common.payload.infra.GatewayStatusDto;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    record ChannelDetail(String direction, String readCodec, String writeCodec) {}

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

    public List<GatewayStatusDto> fetchGatewayStatuses() {
        return connectionManager.sendApi("sofia status")
                .map(this::parseSofiaStatus)
                .orElse(List.of());
    }

    public List<CodecUsageDto> fetchCodecUsage() {
        return connectionManager.sendApi("show channels as json")
                .map(this::parseChannelsJson)
                .orElse(List.of());
    }

    public SessionBreakdownDto fetchDetailedBreakdown() {
        return connectionManager.sendApi("show channels as json")
                .map(this::parseDetailedBreakdown)
                .orElse(new SessionBreakdownDto(0, 0, 0, 0, 0, 0));
    }

    List<GatewayStatusDto> parseSofiaStatus(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        List<GatewayStatusDto> gateways = new ArrayList<>();
        String[] lines = response.split("\n");
        boolean inTable = false;

        for (String line : lines) {
            if (line.contains("====")) {
                if (inTable) {
                    break;
                }
                inTable = true;
                continue;
            }
            if (inTable && !line.isBlank()) {
                String[] parts = line.trim().split("\\s+", 4);
                if (parts.length >= 4) {
                    gateways.add(new GatewayStatusDto(parts[0], parts[1], parts[2], parts[3]));
                }
            }
        }
        return gateways;
    }

    List<CodecUsageDto> parseChannelsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = root.get("rows");
            if (rows == null || !rows.isArray()) {
                return List.of();
            }
            Map<String, Integer> codecCounts = new HashMap<>();
            for (JsonNode row : rows) {
                JsonNode codecNode = row.get("read_codec");
                if (codecNode != null && !codecNode.asText().isEmpty()) {
                    codecCounts.merge(codecNode.asText(), 1, Integer::sum);
                }
            }
            return codecCounts.entrySet().stream()
                    .map(e -> new CodecUsageDto(e.getKey(), e.getValue()))
                    .sorted(Comparator.comparing(CodecUsageDto::codec))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse channels JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private SessionBreakdownDto parseDetailedBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return new SessionBreakdownDto(0, 0, 0, 0, 0, 0);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = root.get("rows");
            if (rows == null || !rows.isArray()) {
                return new SessionBreakdownDto(0, 0, 0, 0, 0, 0);
            }
            int inbound = 0;
            int outbound = 0;
            for (JsonNode row : rows) {
                JsonNode dirNode = row.get("direction");
                if (dirNode != null) {
                    String dir = dirNode.asText();
                    if ("inbound".equalsIgnoreCase(dir)) {
                        inbound++;
                    } else if ("outbound".equalsIgnoreCase(dir)) {
                        outbound++;
                    }
                }
            }
            return new SessionBreakdownDto(inbound, outbound, 0, 0, 0, 0);
        } catch (Exception e) {
            log.warn("Failed to parse channels JSON for breakdown: {}", e.getMessage());
            return new SessionBreakdownDto(0, 0, 0, 0, 0, 0);
        }
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
