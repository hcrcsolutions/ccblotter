package com.fmr.ec3.oscc.fsesl;

import com.fmr.ec3.oscc.fsesl.config.FreeSwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
