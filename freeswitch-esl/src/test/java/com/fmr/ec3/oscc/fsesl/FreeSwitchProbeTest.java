package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.common.payload.infra.CodecUsageDto;
import com.fmr.ec3.oscc.common.payload.infra.GatewayStatusDto;
import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreeSwitchProbeTest {

    private EslConnectionManager connectionManager;
    private FreeSwitchProperties props;
    private FreeSwitchProbe probe;

    @BeforeEach
    void setUp() {
        connectionManager = mock(EslConnectionManager.class);
        props = new FreeSwitchProperties();
        probe = new FreeSwitchProbe(connectionManager, props);
    }

    @Test
    void parseStatusWithRealOutput() {
        String statusOutput = """
                UP 0 years, 0 days, 2 hours, 30 minutes, 15 seconds, 123 milliseconds, 456 microseconds
                FreeSWITCH (Version 1.10.7 -release 64bit) is ready
                5 session(s) since startup
                3 session(s) - peak 8, last 5min 2
                1 session(s) per Sec out of max 30
                1000 session(s) max
                min idle cpu 0.00/98.50
                Current Stack Size/Max 240K/8192K
                """;

        Optional<FreeSwitchProbe.StatusResult> result = probe.parseStatus(statusOutput);

        assertTrue(result.isPresent());
        FreeSwitchProbe.StatusResult status = result.get();
        assertEquals(3, status.currentSessions());
        assertEquals(8, status.peakSessions());
        assertEquals(1, status.sessionsPerSecond());
        assertEquals(30, status.maxSessions());
        assertEquals(98.5, status.idleCpu(), 0.01);
    }

    @Test
    void parseStatusEmptyResponse() {
        assertTrue(probe.parseStatus("").isEmpty());
        assertTrue(probe.parseStatus(null).isEmpty());
        assertTrue(probe.parseStatus("   ").isEmpty());
    }

    @Test
    void parseCountResponse() {
        assertEquals(123, probe.parseCountResponse("123 total.\n"));
        assertEquals(0, probe.parseCountResponse("0 total.\n"));
        assertEquals(-1, probe.parseCountResponse(""));
        assertEquals(-1, probe.parseCountResponse(null));
        assertEquals(-1, probe.parseCountResponse("no match here"));
    }

    @Test
    void fetchStatusDelegatesToConnectionManager() {
        String statusOutput = "3 session(s) - peak 5\n1 session(s) per Sec out of max 30\nmin idle cpu 0.00/95.00\n";
        when(connectionManager.sendApi("status")).thenReturn(Optional.of(statusOutput));

        Optional<FreeSwitchProbe.StatusResult> result = probe.fetchStatus();

        assertTrue(result.isPresent());
        assertEquals(3, result.get().currentSessions());
        assertEquals(95.0, result.get().idleCpu(), 0.01);
    }

    @Test
    void fetchChannelCountParsesTotal() {
        when(connectionManager.sendApi("show channels count")).thenReturn(Optional.of("42 total.\n"));

        assertEquals(42, probe.fetchChannelCount());
    }

    @Test
    void fetchChannelCountReturnsMinusOneWhenDisconnected() {
        when(connectionManager.sendApi(anyString())).thenReturn(Optional.empty());

        assertEquals(-1, probe.fetchChannelCount());
    }

    @Test
    void detectNodeTypeSipWhenSofiaPresent() {
        props.setNodeType("AUTO");
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.sendApi("module_exists mod_sofia")).thenReturn(Optional.of("true"));
        when(connectionManager.sendApi("module_exists mod_conference")).thenReturn(Optional.of("false"));
        when(connectionManager.sendApi("module_exists mod_verto")).thenReturn(Optional.of("false"));

        assertEquals("SIP", probe.detectNodeType());
    }

    @Test
    void detectNodeTypeMediaWhenConferenceOnly() {
        props.setNodeType("AUTO");
        when(connectionManager.isConnected()).thenReturn(true);
        when(connectionManager.sendApi("module_exists mod_sofia")).thenReturn(Optional.of("false"));
        when(connectionManager.sendApi("module_exists mod_conference")).thenReturn(Optional.of("true"));
        when(connectionManager.sendApi("module_exists mod_verto")).thenReturn(Optional.of("false"));

        assertEquals("MEDIA", probe.detectNodeType());
    }

    @Test
    void detectNodeTypeUsesConfigOverride() {
        props.setNodeType("MEDIA");

        assertEquals("MEDIA", probe.detectNodeType());
    }

    @Test
    void detectNodeTypeDefaultsSipWhenDisconnected() {
        props.setNodeType("AUTO");
        when(connectionManager.isConnected()).thenReturn(false);

        assertEquals("SIP", probe.detectNodeType());
    }

    @Test
    void parseStatusWithPartialData() {
        String partial = "3 session(s) - peak 5\n";

        Optional<FreeSwitchProbe.StatusResult> result = probe.parseStatus(partial);

        assertTrue(result.isPresent());
        assertEquals(3, result.get().currentSessions());
        assertEquals(5, result.get().peakSessions());
        assertEquals(0, result.get().sessionsPerSecond());
        assertEquals(0, result.get().maxSessions());
        assertEquals(100, result.get().idleCpu(), 0.01);
    }

    @Test
    void parseSofiaStatusWithGateways() {
        String output = """
                                     Name          Type                                       Data      State
                =================================================================================================
                               internal       profile            sip:mod_sofia@10.0.0.1:5060      RUNNING
                                gateway1       gateway                    sip:gateway1@10.0.0.1      REGED
                                gateway2       gateway                    sip:gateway2@10.0.0.1      NOREG
                =================================================================================================
                """;

        List<GatewayStatusDto> gateways = probe.parseSofiaStatus(output);

        assertEquals(3, gateways.size());
        assertEquals("internal", gateways.get(0).name());
        assertEquals("profile", gateways.get(0).type());
        assertEquals("RUNNING", gateways.get(0).state());
        assertEquals("gateway1", gateways.get(1).name());
        assertEquals("gateway", gateways.get(1).type());
        assertEquals("REGED", gateways.get(1).state());
        assertEquals("gateway2", gateways.get(2).name());
        assertEquals("NOREG", gateways.get(2).state());
    }

    @Test
    void parseSofiaStatusEmpty() {
        assertEquals(List.of(), probe.parseSofiaStatus(null));
        assertEquals(List.of(), probe.parseSofiaStatus(""));
        assertEquals(List.of(), probe.parseSofiaStatus("   "));
    }

    @Test
    void parseChannelsJsonWithCodecs() {
        String json = """
                {"row_count":4,"rows":[
                  {"read_codec":"PCMU","write_codec":"PCMU","direction":"inbound"},
                  {"read_codec":"PCMU","write_codec":"PCMU","direction":"outbound"},
                  {"read_codec":"OPUS","write_codec":"OPUS","direction":"inbound"},
                  {"read_codec":"PCMU","write_codec":"PCMU","direction":"inbound"}
                ]}""";

        List<CodecUsageDto> codecs = probe.parseChannelsJson(json);

        assertEquals(2, codecs.size());
        // Sorted alphabetically
        assertEquals("OPUS", codecs.get(0).codec());
        assertEquals(1, codecs.get(0).count());
        assertEquals("PCMU", codecs.get(1).codec());
        assertEquals(3, codecs.get(1).count());
    }

    @Test
    void parseChannelsJsonEmpty() {
        assertEquals(List.of(), probe.parseChannelsJson(null));
        assertEquals(List.of(), probe.parseChannelsJson(""));
        assertEquals(List.of(), probe.parseChannelsJson("{}"));
        assertEquals(List.of(), probe.parseChannelsJson("{\"row_count\":0,\"rows\":[]}"));
    }
}
