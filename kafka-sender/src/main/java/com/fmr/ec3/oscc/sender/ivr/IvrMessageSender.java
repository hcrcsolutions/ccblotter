package com.fmr.ec3.oscc.sender.ivr;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionEndedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionStartedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrStepCompletedPayload;
import com.fmr.ec3.oscc.sender.EventProducer;

/**
 * High-level API for sending IVR session events to ccblotter.
 *
 * <h2>Overview</h2>
 * <p>The IVR monitoring system tracks caller sessions flowing through IVR trees.
 * Media servers (or any IVR engine) emit events as callers navigate menus, enter
 * input, authenticate, and eventually transfer to an agent or hang up. These events
 * flow through Kafka, are persisted to Redis, and rendered as an interactive timeline
 * in the ccblotter UI.</p>
 *
 * <h2>Kafka topic</h2>
 * <p>All IVR events are published to <b>{@code ccblotter.ivr.events}</b>.
 * The partition key is always the {@code sessionId}, which guarantees that all events
 * for the same session land on the same partition and arrive in order.</p>
 *
 * <h2>Event lifecycle</h2>
 * <p>Every IVR session follows this sequence:</p>
 * <pre>
 *   IVR_SESSION_STARTED        (exactly once, first)
 *        |
 *   IVR_STEP_COMPLETED  ----+  (zero or more, in order)
 *        |                   |
 *        +-------------------+
 *        |
 *   IVR_SESSION_ENDED          (exactly once, last)
 * </pre>
 *
 * <h2>Step types</h2>
 * <table>
 *   <tr><th>Type</th><th>Description</th><th>UI rendering</th></tr>
 *   <tr><td>{@code PLAY}</td><td>Pre-recorded audio prompt played to caller</td>
 *       <td>Left-aligned blue bubble with prompt text</td></tr>
 *   <tr><td>{@code SAY}</td><td>TTS-generated speech played to caller</td>
 *       <td>Left-aligned blue bubble with spoken text</td></tr>
 *   <tr><td>{@code CAPTURE}</td><td>Collects DTMF digits or speech input from caller</td>
 *       <td>Right-aligned green bubble with input text and optional audio play button</td></tr>
 *   <tr><td>{@code AUTHENTICATE}</td><td>PIN/password verification step</td>
 *       <td>Full-width card with lock icon; shows auth-success or auth-failed outcome</td></tr>
 *   <tr><td>{@code TRANSFER}</td><td>Handoff to a live agent or another system</td>
 *       <td>Full-width card with phone-forward icon</td></tr>
 *   <tr><td>{@code HANGUP}</td><td>System-initiated disconnect</td>
 *       <td>Full-width card with call-end icon</td></tr>
 *   <tr><td>{@code BRANCH}</td><td>Routing decision point (no caller interaction)</td>
 *       <td>Centered connector line showing branch outcome</td></tr>
 * </table>
 *
 * <h2>Session end states</h2>
 * <ul>
 *   <li><b>COMPLETED</b> — Normal flow: caller reached the end of the IVR tree
 *       (transferred to agent, heard goodbye message, etc.)</li>
 *   <li><b>ABANDONED</b> — Caller hung up before completing the flow</li>
 *   <li><b>FAILED</b> — System error terminated the session (TTS failure, timeout cascade, etc.)</li>
 * </ul>
 *
 * <h2>Dependencies</h2>
 * <p>This class lives in the {@code kafka-sender} module and depends on {@link EventProducer}.
 * Add both {@code am-common} and {@code kafka-sender} to your Maven/Gradle build:</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.fmr.ec3.oscc</groupId>
 *     <artifactId>am-common</artifactId>
 *     <version>${project.version}</version>
 * </dependency>
 * <dependency>
 *     <groupId>com.fmr.ec3.oscc</groupId>
 *     <artifactId>kafka-sender</artifactId>
 *     <version>${project.version}</version>
 * </dependency>
 * }</pre>
 * <p>The {@code kafka-sender} module provides Spring Boot auto-configuration that creates
 * the {@code EventProducer} bean automatically when {@code KafkaTemplate} is available.
 * You can then inject or construct {@code IvrMessageSender} with it.</p>
 *
 * <h2>Thread safety</h2>
 * <p>This class is thread-safe. All methods delegate to {@link EventProducer} which
 * uses {@code KafkaTemplate} (also thread-safe). You may call methods from any thread,
 * including Kafka listener threads, scheduled tasks, or request handlers.</p>
 *
 * <h2>Typical integration</h2>
 * <pre>{@code
 * @Component
 * public class MyMediaServer {
 *
 *     private final IvrMessageSender ivr;
 *
 *     public MyMediaServer(EventProducer producer) {
 *         this.ivr = new IvrMessageSender(producer, "media-server-1");
 *     }
 *
 *     public void onCallerEntered(Call call, IvrFlow flow) {
 *         ivr.sessionStarted("IVR-" + call.getId(), call.getId(),
 *             call.getOriginator(), flow.getId());
 *     }
 *
 *     public void onPromptPlayed(String sessionId, String stepId,
 *                                String promptName, String promptText,
 *                                long startMs, long endMs, long latencyMs) {
 *         ivr.playStepCompleted(sessionId, stepId, promptName, promptText,
 *             startMs, endMs, latencyMs);
 *     }
 *
 *     public void onCallerInput(String sessionId, String stepId,
 *                               String promptName, String promptText,
 *                               String digits, String audioFileUrl,
 *                               long startMs, long endMs, long latencyMs) {
 *         ivr.captureStepCompleted(sessionId, stepId, promptName, promptText,
 *             digits, "success", 0, audioFileUrl, startMs, endMs, latencyMs);
 *     }
 *
 *     public void onSessionDone(String sessionId, String callId,
 *                               int stepCount, boolean authenticated) {
 *         ivr.sessionCompleted(sessionId, callId, stepCount, authenticated);
 *     }
 * }
 * }</pre>
 *
 * <h2>Complete session example</h2>
 * <p>A typical billing inquiry session emits events in this order:</p>
 * <pre>{@code
 * IvrMessageSender ivr = new IvrMessageSender(producer, "media-east-1");
 *
 * // 1. Session begins
 * ivr.sessionStarted("IVR-00042", "CALL-a1b2", "(212) 555-0100", "billing-inquiry");
 *
 * // 2. Welcome prompt
 * ivr.playStepCompleted("IVR-00042", "IVR-00042-S1", "Welcome Greeting",
 *     "Welcome to Acme billing. For English, press 1.", t0, t1, 45);
 *
 * // 3. Language selection (DTMF capture)
 * ivr.captureStepCompleted("IVR-00042", "IVR-00042-S2", "Language Selection",
 *     "Press 1 for English, 2 for Spanish.",
 *     "1", "success", 0, null, t1, t2, 120);
 *
 * // 4. PIN authentication
 * ivr.authenticateStepCompleted("IVR-00042", "IVR-00042-S3", "PIN Entry",
 *     "Please enter your 4-digit PIN.",
 *     "auth-success", 0, null, t2, t3, 200);
 *
 * // 5. Account number capture (with audio recording)
 * ivr.captureStepCompleted("IVR-00042", "IVR-00042-S4", "Account Number",
 *     "Please say or enter your account number.",
 *     "1234567890", "success", 0,
 *     "/audio/recordings/IVR-00042-S4.wav", t3, t4, 350);
 *
 * // 6. Balance readout (TTS)
 * ivr.sayStepCompleted("IVR-00042", "IVR-00042-S5", "Balance Readout",
 *     "Your current balance is $1,234.56. Payment of $500 was received on March 1st.",
 *     t4, t5, 180);
 *
 * // 7. Routing decision
 * ivr.branchStepCompleted("IVR-00042", "IVR-00042-S6", "Route Decision",
 *     "agent-queue", t5, t6, 15);
 *
 * // 8. Transfer to agent
 * ivr.transferStepCompleted("IVR-00042", "IVR-00042-S7", "Agent Transfer",
 *     "Transferring to the next available billing specialist.", t6, t7, 90);
 *
 * // 9. Session ends normally
 * ivr.sessionCompleted("IVR-00042", "CALL-a1b2", 7, true);
 * }</pre>
 */
public class IvrMessageSender {

    private final EventProducer eventProducer;
    private final String mediaServerId;

    /**
     * Creates an IVR message sender.
     *
     * @param eventProducer the Kafka event producer (from kafka-sender module auto-configuration)
     * @param mediaServerId stable identifier for this media server instance, e.g. {@code "media-east-1"}.
     *                      Used for tracing which server originated each event. Must be consistent
     *                      across restarts (do not use random UUIDs).
     */
    public IvrMessageSender(EventProducer eventProducer, String mediaServerId) {
        this.eventProducer = eventProducer;
        this.mediaServerId = mediaServerId;
    }

    // ========================================================================
    // Session lifecycle
    // ========================================================================

    /**
     * Emit when a caller enters the IVR flow. This must be the <b>first</b> event for
     * every session. The receiver creates the session record in Redis and adds it to
     * the active sessions set. The UI will show the session in the IVR Sessions grid
     * immediately.
     *
     * <p><b>Call this exactly once per session, before any step events.</b> If a duplicate
     * {@code sessionId} arrives, the receiver logs a warning and overwrites the existing
     * session — this is safe but indicates a bug in your ID generation.</p>
     *
     * @param sessionId  unique identifier for this IVR session, e.g. {@code "IVR-00042"}.
     *                   Used as the Kafka partition key — all events with the same sessionId
     *                   are guaranteed to be processed in order.
     * @param callId     the underlying telephony call ID that this IVR session belongs to,
     *                   e.g. {@code "CALL-a1b2c3d4"}. Allows correlation with SIP call events.
     * @param originator the caller's phone number or SIP URI, e.g. {@code "(212) 555-0100"}.
     *                   Displayed as the session header in the UI drawer.
     * @param flowId     identifier for the IVR flow/tree being executed, e.g.
     *                   {@code "main-menu"}, {@code "billing-inquiry"}. Displayed in the UI
     *                   and useful for filtering sessions by flow type.
     */
    public void sessionStarted(String sessionId, String callId,
                                String originator, String flowId) {
        eventProducer.send(
            KafkaTopics.IVR_EVENTS, sessionId,
            EventType.IVR_SESSION_STARTED, mediaServerId, sessionId,
            new IvrSessionStartedPayload(
                sessionId, callId, originator, flowId,
                System.currentTimeMillis(), mediaServerId)
        );
    }

    /**
     * Emit when the IVR session completes normally — the caller reached the end of the
     * flow (transferred to agent, heard goodbye, etc.).
     *
     * <p><b>Call this exactly once per session, after all step events.</b></p>
     *
     * @param sessionId     the session ID (must match the one from {@link #sessionStarted})
     * @param callId        the telephony call ID
     * @param stepCount     total number of steps that were emitted for this session
     * @param authenticated whether the caller successfully authenticated during the session
     */
    public void sessionCompleted(String sessionId, String callId,
                                  int stepCount, boolean authenticated) {
        sendSessionEnded(sessionId, callId, "COMPLETED", stepCount, authenticated);
    }

    /**
     * Emit when the caller abandons the IVR session (hangs up before completing the flow).
     *
     * @param sessionId     the session ID
     * @param callId        the telephony call ID
     * @param stepCount     number of steps completed before abandonment
     * @param authenticated whether the caller had authenticated before abandoning
     */
    public void sessionAbandoned(String sessionId, String callId,
                                  int stepCount, boolean authenticated) {
        sendSessionEnded(sessionId, callId, "ABANDONED", stepCount, authenticated);
    }

    /**
     * Emit when the IVR session terminates due to a system error (TTS engine failure,
     * timeout cascade, configuration error, etc.).
     *
     * @param sessionId     the session ID
     * @param callId        the telephony call ID
     * @param stepCount     number of steps completed before failure
     * @param authenticated whether the caller had authenticated before the failure
     */
    public void sessionFailed(String sessionId, String callId,
                               int stepCount, boolean authenticated) {
        sendSessionEnded(sessionId, callId, "FAILED", stepCount, authenticated);
    }

    // ========================================================================
    // Step events — generic
    // ========================================================================

    /**
     * Emit when any IVR step completes. This is the low-level method that accepts all
     * fields. Prefer the typed convenience methods below for clearer call sites.
     *
     * <p><b>Ordering:</b> Steps must be emitted in the order they executed. The receiver
     * appends each step to a Redis list (RPUSH), and the UI renders them top-to-bottom.
     * Out-of-order steps will display incorrectly.</p>
     *
     * <p><b>Step IDs:</b> Must be unique within the session. Convention is
     * {@code "{sessionId}-S{n}"} where n is the 1-based step number, e.g.
     * {@code "IVR-00042-S3"}.</p>
     *
     * @param stepId       unique step identifier, e.g. {@code "IVR-00042-S3"}
     * @param sessionId    parent session ID
     * @param stepType     one of: {@code PLAY}, {@code SAY}, {@code CAPTURE},
     *                     {@code AUTHENTICATE}, {@code TRANSFER}, {@code HANGUP}, {@code BRANCH}
     * @param stepName     human-readable label, e.g. {@code "Welcome Greeting"}, {@code "PIN Entry"}.
     *                     Displayed in the session's "current step" column in the grid.
     * @param prompt       text/audio prompt shown or played to the caller. Null for steps that
     *                     don't have a prompt (e.g. BRANCH). Displayed as the bubble body text.
     * @param input        caller's response — DTMF digits or speech-to-text result. Null for
     *                     non-input steps (PLAY, SAY, BRANCH) and for timed-out captures.
     * @param outcome      result of the step. Common values:
     *                     <ul>
     *                       <li>{@code "success"} — step completed normally</li>
     *                       <li>{@code "timeout"} — caller didn't respond in time (CAPTURE)</li>
     *                       <li>{@code "no-match"} — speech input not recognized (CAPTURE)</li>
     *                       <li>{@code "auth-success"} / {@code "auth-failed"} (AUTHENTICATE)</li>
     *                       <li>{@code "in-progress"} (TRANSFER)</li>
     *                       <li>{@code "branch-1"}, {@code "branch-2"}, etc. (BRANCH)</li>
     *                     </ul>
     * @param startTimeMs  epoch millis when the step started executing
     * @param endTimeMs    epoch millis when the step finished executing
     * @param latencyMs    system response time in milliseconds (time between receiving caller
     *                     input and starting the next action). Displayed in the UI as a
     *                     connector between bubbles; values above 500ms are highlighted as warnings.
     * @param retryAttempt 0 for first attempt, incremented on retries. The UI shows a
     *                     "Retry N" badge when this is greater than 0.
     * @param error        error message if the step failed, null on success. The UI shows
     *                     a red error indicator when present.
     * @param audioUrl     URL to the captured utterance audio recording. <b>Only meaningful for
     *                     CAPTURE steps with non-null input.</b> The UI renders a play/stop button
     *                     next to the captured text. The URL must be accessible from the browser
     *                     (relative or absolute). Set to null for non-CAPTURE steps, for timed-out
     *                     captures, and when no recording is available. Example:
     *                     {@code "/audio/recordings/IVR-00042-S3.wav"}.
     */
    public void stepCompleted(String stepId, String sessionId, String stepType,
                               String stepName, String prompt, String input,
                               String outcome, long startTimeMs, long endTimeMs,
                               long latencyMs, int retryAttempt, String error,
                               String audioUrl) {
        eventProducer.send(
            KafkaTopics.IVR_EVENTS, sessionId,
            EventType.IVR_STEP_COMPLETED, mediaServerId, stepId,
            new IvrStepCompletedPayload(
                stepId, sessionId, stepType, stepName,
                prompt, input, outcome,
                startTimeMs, endTimeMs, latencyMs, retryAttempt,
                error, audioUrl, mediaServerId)
        );
    }

    // ========================================================================
    // Step events — typed convenience methods
    // ========================================================================

    /**
     * A pre-recorded audio prompt was played to the caller.
     *
     * <p>Use this for welcome greetings, menu announcements, hold music descriptions,
     * goodbye messages, and any other pre-recorded audio file playback.</p>
     *
     * <p><b>UI:</b> Renders as a left-aligned blue chat bubble with the prompt text.</p>
     *
     * @param sessionId   parent session ID
     * @param stepId      unique step ID, e.g. {@code "IVR-00042-S1"}
     * @param stepName    human-readable label, e.g. {@code "Welcome Greeting"}
     * @param prompt      description of what was played, e.g. {@code "Welcome to our service."}
     * @param startTimeMs epoch millis when playback started
     * @param endTimeMs   epoch millis when playback ended
     * @param latencyMs   system latency (time to fetch/decode the audio file)
     */
    public void playStepCompleted(String sessionId, String stepId,
                                   String stepName, String prompt,
                                   long startTimeMs, long endTimeMs,
                                   long latencyMs) {
        stepCompleted(stepId, sessionId, "PLAY", stepName,
            prompt, null, "success",
            startTimeMs, endTimeMs, latencyMs, 0, null, null);
    }

    /**
     * A TTS (text-to-speech) message was spoken to the caller.
     *
     * <p>Use this for dynamically generated speech: account balances, confirmation
     * messages, dates, amounts, etc.</p>
     *
     * <p><b>UI:</b> Renders identically to PLAY (left-aligned blue bubble).</p>
     *
     * @param sessionId   parent session ID
     * @param stepId      unique step ID
     * @param stepName    label, e.g. {@code "Balance Readout"}, {@code "TTS Response"}
     * @param spokenText  the text that was synthesized and spoken, e.g.
     *                    {@code "Your balance is $1,234.56"}
     * @param startTimeMs epoch millis when TTS started
     * @param endTimeMs   epoch millis when TTS playback ended
     * @param latencyMs   TTS synthesis latency
     */
    public void sayStepCompleted(String sessionId, String stepId,
                                  String stepName, String spokenText,
                                  long startTimeMs, long endTimeMs,
                                  long latencyMs) {
        stepCompleted(stepId, sessionId, "SAY", stepName,
            spokenText, null, "success",
            startTimeMs, endTimeMs, latencyMs, 0, null, null);
    }

    /**
     * The system collected DTMF digits or speech input from the caller.
     *
     * <p>This covers menu selections ({@code "1"}), account numbers ({@code "1234567890"}),
     * ZIP codes, dates spoken aloud ({@code "January fifth"}), and any other caller-provided
     * input.</p>
     *
     * <p><b>UI:</b> Renders as a right-aligned green bubble. The {@code input} text is shown
     * in italics. If {@code audioUrl} is provided, a play/stop button appears next to the
     * input text so the operator can hear the original utterance. Only one audio clip plays
     * at a time across all steps.</p>
     *
     * <h3>Audio URL requirements</h3>
     * <ul>
     *   <li>Must be accessible from the user's browser (same-origin, CDN, or CORS-enabled)</li>
     *   <li>WAV or MP3 format (browser-native audio playback)</li>
     *   <li>Relative URLs (e.g. {@code "/audio/recordings/IVR-00042-S3.wav"}) are resolved
     *       against the UI's origin</li>
     *   <li>Absolute URLs (e.g. {@code "https://media.example.com/rec/abc.wav"}) also work</li>
     *   <li>Pass {@code null} when no recording is available</li>
     * </ul>
     *
     * @param sessionId    parent session ID
     * @param stepId       unique step ID
     * @param stepName     label describing what was captured, e.g. {@code "Enter your PIN"},
     *                     {@code "Say or enter your date of birth"}
     * @param prompt       the prompt played before collecting input, e.g.
     *                     {@code "Please enter your 4-digit PIN."}
     * @param input        the captured input: DTMF digits ({@code "1234"}) or speech text
     *                     ({@code "January fifth"}). Null if the capture timed out or had
     *                     no match — in that case, set outcome to {@code "timeout"} or
     *                     {@code "no-match"}.
     * @param outcome      {@code "success"} if input was captured, {@code "timeout"} if the
     *                     caller didn't respond, {@code "no-match"} if speech was unrecognized
     * @param retryAttempt 0 for first attempt; increment if the step is retried after
     *                     a timeout or no-match
     * @param audioUrl     URL to the utterance recording, or null. Ignored by the UI when
     *                     {@code input} is null (no play button shown for timeouts).
     * @param startTimeMs  epoch millis when the capture prompt started
     * @param endTimeMs    epoch millis when input was received (or timeout occurred)
     * @param latencyMs    time from end-of-speech to input recognition result
     */
    public void captureStepCompleted(String sessionId, String stepId,
                                      String stepName, String prompt,
                                      String input, String outcome,
                                      int retryAttempt, String audioUrl,
                                      long startTimeMs, long endTimeMs,
                                      long latencyMs) {
        stepCompleted(stepId, sessionId, "CAPTURE", stepName,
            prompt, input, outcome,
            startTimeMs, endTimeMs, latencyMs, retryAttempt, null, audioUrl);
    }

    /**
     * A PIN or password verification step completed.
     *
     * <p>The receiver updates the session's {@code authenticated} flag and transitions
     * the session state to {@code ACTIVE} (on success) or {@code AUTHENTICATING}
     * (on failure).</p>
     *
     * <p><b>UI:</b> Renders as a full-width card with a lock icon. Auth failures show
     * a red error indicator.</p>
     *
     * @param sessionId    parent session ID
     * @param stepId       unique step ID
     * @param stepName     label, e.g. {@code "PIN Entry"}, {@code "Voice Biometrics"}
     * @param prompt       instructions shown to caller, e.g. {@code "Please enter your 4-digit PIN."}
     * @param outcome      {@code "auth-success"} or {@code "auth-failed"}
     * @param retryAttempt attempt number (0 for first try)
     * @param error        error message on failure (e.g. {@code "Invalid PIN"}), null on success
     * @param startTimeMs  epoch millis when auth started
     * @param endTimeMs    epoch millis when auth completed
     * @param latencyMs    verification latency
     */
    public void authenticateStepCompleted(String sessionId, String stepId,
                                           String stepName, String prompt,
                                           String outcome, int retryAttempt,
                                           String error,
                                           long startTimeMs, long endTimeMs,
                                           long latencyMs) {
        stepCompleted(stepId, sessionId, "AUTHENTICATE", stepName,
            prompt, "****", outcome,
            startTimeMs, endTimeMs, latencyMs, retryAttempt, error, null);
    }

    /**
     * The caller is being transferred to a live agent or another system.
     *
     * <p>The receiver transitions the session state to {@code TRANSFERRING}.
     * This is typically the last step before {@link #sessionCompleted}.</p>
     *
     * <p><b>UI:</b> Renders as a full-width card with a phone-forward icon.</p>
     *
     * @param sessionId   parent session ID
     * @param stepId      unique step ID
     * @param stepName    label, e.g. {@code "Agent Transfer"}, {@code "Supervisor Escalation"}
     * @param prompt      transfer message, e.g. {@code "Transferring to the next available agent."}
     * @param startTimeMs epoch millis when transfer initiated
     * @param endTimeMs   epoch millis when transfer completed (or when the step is considered done)
     * @param latencyMs   time to initiate the transfer
     */
    public void transferStepCompleted(String sessionId, String stepId,
                                       String stepName, String prompt,
                                       long startTimeMs, long endTimeMs,
                                       long latencyMs) {
        stepCompleted(stepId, sessionId, "TRANSFER", stepName,
            prompt, null, "in-progress",
            startTimeMs, endTimeMs, latencyMs, 0, null, null);
    }

    /**
     * The system disconnected the caller.
     *
     * <p>Use this for intentional system-side hangups (e.g. after a goodbye message,
     * after too many failed auth attempts, or after an inactivity timeout).</p>
     *
     * <p><b>UI:</b> Renders as a full-width card with a call-end icon.</p>
     *
     * @param sessionId   parent session ID
     * @param stepId      unique step ID
     * @param stepName    label, e.g. {@code "Goodbye"}, {@code "Inactivity Disconnect"}
     * @param prompt      farewell message if any, or null
     * @param startTimeMs epoch millis
     * @param endTimeMs   epoch millis
     * @param latencyMs   system latency
     */
    public void hangupStepCompleted(String sessionId, String stepId,
                                     String stepName, String prompt,
                                     long startTimeMs, long endTimeMs,
                                     long latencyMs) {
        stepCompleted(stepId, sessionId, "HANGUP", stepName,
            prompt, null, "success",
            startTimeMs, endTimeMs, latencyMs, 0, null, null);
    }

    /**
     * A routing decision was made within the IVR tree (no caller interaction).
     *
     * <p>Branch steps represent internal routing logic: skill-based routing decisions,
     * time-of-day checks, account-type forks, etc. The {@code outcome} field indicates
     * which branch was taken.</p>
     *
     * <p><b>UI:</b> Renders as a centered connector with an arrow icon and the outcome label.
     * Does not appear as a chat bubble — it's a lightweight separator between other steps.</p>
     *
     * @param sessionId   parent session ID
     * @param stepId      unique step ID
     * @param stepName    label describing the decision point, e.g. {@code "Route Decision"},
     *                    {@code "Account Type Check"}
     * @param outcome     which branch was selected, e.g. {@code "branch-1"}, {@code "premium"},
     *                    {@code "after-hours"}
     * @param startTimeMs epoch millis when the branch evaluation started
     * @param endTimeMs   epoch millis when the branch was resolved
     * @param latencyMs   evaluation latency
     */
    public void branchStepCompleted(String sessionId, String stepId,
                                     String stepName, String outcome,
                                     long startTimeMs, long endTimeMs,
                                     long latencyMs) {
        stepCompleted(stepId, sessionId, "BRANCH", stepName,
            null, null, outcome,
            startTimeMs, endTimeMs, latencyMs, 0, null, null);
    }

    // ========================================================================
    // Internal
    // ========================================================================

    private void sendSessionEnded(String sessionId, String callId,
                                  String endState, int stepCount,
                                  boolean authenticated) {
        eventProducer.send(
            KafkaTopics.IVR_EVENTS, sessionId,
            EventType.IVR_SESSION_ENDED, mediaServerId, sessionId,
            new IvrSessionEndedPayload(
                sessionId, callId, endState,
                System.currentTimeMillis(), stepCount, authenticated, mediaServerId)
        );
    }
}
