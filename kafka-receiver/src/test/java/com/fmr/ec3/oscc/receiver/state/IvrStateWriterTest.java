package com.fmr.ec3.oscc.receiver.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IvrStateWriterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ListOperations<String, String> listOps;

    private IvrStateWriter writer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        writer = new IvrStateWriter(redisTemplate);
    }

    @Nested
    class CreateSessionTests {

        @Test
        @SuppressWarnings("unchecked")
        void writesAllFieldsToRedis() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");

            writer.createSession("IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu", start);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOps).putAll(eq("ivr:session:IVR-00001"), captor.capture());

            Map<String, String> fields = captor.getValue();
            assertEquals("IVR-00001", fields.get("id"));
            assertEquals("CALL-abc", fields.get("callId"));
            assertEquals("(212) 555-0100", fields.get("originator"));
            assertEquals("main-menu", fields.get("flowId"));
            assertEquals("ACTIVE", fields.get("state"));
            assertEquals("0", fields.get("stepCount"));
            assertEquals(start.toString(), fields.get("startTime"));
            assertEquals("false", fields.get("authenticated"));
        }

        @Test
        void addsSessionIdToGlobalSet() {
            writer.createSession("IVR-00001", "CALL-abc", "(212) 555-0100", "main-menu",
                Instant.now());

            verify(setOps).add("ivr:sessions:all", "IVR-00001");
        }
    }

    @Nested
    class AddStepTests {

        @Test
        void writesStepJsonWithAudioUrl() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:03Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(1L);

            writer.addStep("IVR-00001", "IVR-00001-S2", "CAPTURE", "Enter PIN",
                "Enter your PIN.", "1234", "success",
                start, end, 150, 0, null, "/audio/samples/capture-3.wav");

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(listOps).rightPush(eq("ivr:session:IVR-00001:steps"), jsonCaptor.capture());

            String json = jsonCaptor.getValue();
            assertTrue(json.contains("\"audioUrl\":\"/audio/samples/capture-3.wav\""),
                "JSON should contain audioUrl field: " + json);
            assertTrue(json.contains("\"stepType\":\"CAPTURE\""));
            assertTrue(json.contains("\"input\":\"1234\""));
            assertTrue(json.contains("\"error\":null"));
        }

        @Test
        void writesNullAudioUrlForPlayStep() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:02Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(1L);

            writer.addStep("IVR-00001", "IVR-00001-S1", "PLAY", "Welcome",
                "Welcome.", null, "success",
                start, end, 80, 0, null, null);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(listOps).rightPush(eq("ivr:session:IVR-00001:steps"), jsonCaptor.capture());

            String json = jsonCaptor.getValue();
            assertTrue(json.contains("\"audioUrl\":null"),
                "JSON should contain null audioUrl: " + json);
            assertTrue(json.contains("\"input\":null"));
        }

        @Test
        void writesNullAudioUrlForTimeoutCapture() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:05Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(2L);

            writer.addStep("IVR-00001", "IVR-00001-S3", "CAPTURE", "Enter PIN",
                "Enter your PIN.", null, "timeout",
                start, end, 200, 1, null, null);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(listOps).rightPush(eq("ivr:session:IVR-00001:steps"), jsonCaptor.capture());

            String json = jsonCaptor.getValue();
            assertTrue(json.contains("\"audioUrl\":null"));
            assertTrue(json.contains("\"outcome\":\"timeout\""));
            assertTrue(json.contains("\"retryAttempt\":1"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void updatesSessionMetadata() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:03Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(3L);

            writer.addStep("IVR-00001", "IVR-00001-S3", "CAPTURE", "Enter Amount",
                "Enter amount.", "500", "success",
                start, end, 100, 0, null, "/audio/samples/capture-1.wav");

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOps).putAll(eq("ivr:session:IVR-00001"), captor.capture());

            Map<String, String> updates = captor.getValue();
            assertEquals("Enter Amount", updates.get("currentStep"));
            assertEquals("3", updates.get("stepCount"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void authSuccessUpdatesSessionState() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:03Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(2L);

            writer.addStep("IVR-00001", "IVR-00001-S2", "AUTHENTICATE", "PIN Entry",
                "Enter PIN.", "****", "auth-success",
                start, end, 100, 0, null, null);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOps).putAll(eq("ivr:session:IVR-00001"), captor.capture());

            Map<String, String> updates = captor.getValue();
            assertEquals("true", updates.get("authenticated"));
            assertEquals("ACTIVE", updates.get("state"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void authFailedSetsAuthenticatingState() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:03Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(2L);

            writer.addStep("IVR-00001", "IVR-00001-S2", "AUTHENTICATE", "PIN Entry",
                "Enter PIN.", "****", "auth-failed",
                start, end, 100, 1, "Invalid PIN", null);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOps).putAll(eq("ivr:session:IVR-00001"), captor.capture());

            assertEquals("AUTHENTICATING", captor.getValue().get("state"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void transferStepSetsTransferringState() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:03Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(4L);

            writer.addStep("IVR-00001", "IVR-00001-S4", "TRANSFER", "Agent Transfer",
                "Transferring.", null, "in-progress",
                start, end, 100, 0, null, null);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOps).putAll(eq("ivr:session:IVR-00001"), captor.capture());

            assertEquals("TRANSFERRING", captor.getValue().get("state"));
        }

        @Test
        void escapesJsonSpecialCharacters() {
            Instant start = Instant.parse("2024-06-15T10:00:00Z");
            Instant end = Instant.parse("2024-06-15T10:00:03Z");
            when(listOps.size("ivr:session:IVR-00001:steps")).thenReturn(1L);

            writer.addStep("IVR-00001", "IVR-00001-S1", "SAY", "Balance",
                "Your balance is $1,234.56 \"premium\"", null, "success",
                start, end, 80, 0, null, null);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(listOps).rightPush(eq("ivr:session:IVR-00001:steps"), jsonCaptor.capture());

            String json = jsonCaptor.getValue();
            assertTrue(json.contains("\\\"premium\\\""),
                "Quotes should be escaped: " + json);
        }
    }

    @Nested
    class EndSessionTests {

        @Test
        @SuppressWarnings("unchecked")
        void updatesAllEndFields() {
            Instant endTime = Instant.parse("2024-06-15T10:05:00Z");
            when(redisTemplate.hasKey("ivr:session:IVR-00001")).thenReturn(true);

            writer.endSession("IVR-00001", "COMPLETED", endTime, 6, true);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(hashOps).putAll(eq("ivr:session:IVR-00001"), captor.capture());

            Map<String, String> updates = captor.getValue();
            assertEquals("COMPLETED", updates.get("state"));
            assertEquals(endTime.toString(), updates.get("endTime"));
            assertEquals("6", updates.get("stepCount"));
            assertEquals("true", updates.get("authenticated"));
        }

        @Test
        void ignoresEndForMissingSession() {
            when(redisTemplate.hasKey("ivr:session:IVR-99999")).thenReturn(false);

            writer.endSession("IVR-99999", "ABANDONED", Instant.now(), 2, false);

            verify(hashOps, never()).putAll(anyString(), anyMap());
        }
    }

    @Nested
    class SessionExistsTests {

        @Test
        void returnsTrueWhenKeyExists() {
            when(redisTemplate.hasKey("ivr:session:IVR-00001")).thenReturn(true);
            assertTrue(writer.sessionExists("IVR-00001"));
        }

        @Test
        void returnsFalseWhenKeyMissing() {
            when(redisTemplate.hasKey("ivr:session:IVR-99999")).thenReturn(false);
            assertFalse(writer.sessionExists("IVR-99999"));
        }
    }
}
