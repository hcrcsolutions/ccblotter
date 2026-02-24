# IVR Formal Grammar Specification

**Version:** 0.1.0-draft
**Status:** DRAFT — not for implementation

---

## 1. Overview

This document defines a formal grammar for describing IVR (Interactive Voice Response) call flows. The grammar serves two purposes:

1. **Static flow definition** — a schema for authoring IVR call flows ahead of time
2. **Runtime protocol** — the message format exchanged between an IVR engine and an AI backend during a live call

The AI backend owns conversation state. The IVR engine is a stateless executor: it receives a step (or sequence of steps), executes it, reports the outcome, and waits for the next instruction. The IVR engine maintains local fallback flows that activate when the AI backend is unreachable.

### 1.1 Design Principles

- **Fail-closed.** Every step must define what happens on failure. Undefined failure behavior is a specification error.
- **Transport-agnostic.** The grammar describes messages, not how they are delivered. Section 7 defines bindings for HTTP and WebSocket.
- **No implicit defaults.** Global defaults exist but must be declared explicitly in the flow definition. An IVR engine must reject a flow that omits the `defaults` block.

### 1.2 Terminology

| Term | Definition |
|------|-----------|
| **Step** | A single atomic instruction the IVR engine executes |
| **Flow** | An ordered sequence of steps with a declared entry point |
| **Turn** | One round-trip: IVR sends an outcome, AI backend responds with the next step(s) |
| **Utterance** | Caller input — spoken words, DTMF digits, or both |
| **Barge-in** | Caller interrupts a prompt before it finishes playing |
| **Fallback flow** | A static flow the IVR executes when the AI backend is unreachable |

---

## 2. Formal Grammar (EBNF)

```ebnf
(* ─── Top-level ─── *)
flow            = "{" , flow-meta , defaults , fallback , entry-point , step-map , "}" ;
flow-meta       = '"id"' , ":" , string ,
                  '"version"' , ":" , string ,
                  '"description"' , ":" , string ;
defaults        = '"defaults"' , ":" , defaults-block ;
fallback        = '"fallback"' , ":" , step-ref ;
entry-point     = '"entryPoint"' , ":" , step-ref ;
step-map        = '"steps"' , ":" , "{" , { step-entry } , "}" ;
step-entry      = step-ref , ":" , step ;

(* ─── Steps ─── *)
step            = play-step | say-step | capture-step | auth-step
                | transfer-step | hangup-step | branch-step | goto-step ;

play-step       = '{"type": "play"' , "," , play-body , "," , common-fields , "}" ;
play-body       = '"src"' , ":" , uri
                , [ "," , '"bargeIn"' , ":" , boolean ]
                , [ "," , '"loop"' , ":" , integer ] ;

say-step        = '{"type": "say"' , "," , say-body , "," , common-fields , "}" ;
say-body        = '"text"' , ":" , string
                , '"voice"' , ":" , voice-spec
                , [ "," , '"bargeIn"' , ":" , boolean ]
                , [ "," , '"ssml"' , ":" , boolean ] ;

capture-step    = '{"type": "capture"' , "," , capture-body , "," , common-fields , "}" ;
capture-body    = '"input"' , ":" , input-mode
                , [ "," , '"prompt"' , ":" , ( play-body | say-body ) ]
                , [ "," , '"grammar"' , ":" , dtmf-grammar ]
                , [ "," , '"hints"' , ":" , "[" , { string } , "]" ]
                , [ "," , timeout-config ] ;

auth-step       = '{"type": "authenticate"' , "," , auth-body , "," , common-fields , "}" ;
auth-body       = '"method"' , ":" , auth-method
                , '"credentials"' , ":" , "[" , { credential-spec } , "]"
                , [ "," , '"maxAttempts"' , ":" , integer ]
                , [ "," , '"onLockout"' , ":" , step-ref ] ;

transfer-step   = '{"type": "transfer"' , "," , transfer-body , "}" ;
transfer-body   = '"destination"' , ":" , string
                , '"reason"' , ":" , string
                , [ "," , '"announceToAgent"' , ":" , string ] ;

hangup-step     = '{"type": "hangup"' , "," , '"reason"' , ":" , string , "}" ;

branch-step     = '{"type": "branch"' , "," , branch-body , "}" ;
branch-body     = '"conditions"' , ":" , "[" , { condition } , "]"
                , '"default"' , ":" , step-ref ;

goto-step       = '{"type": "goto"' , "," , '"target"' , ":" , step-ref , "}" ;

(* ─── Common fields (present on every step except transfer/hangup) ─── *)
common-fields   = '"onSuccess"' , ":" , step-ref
                , '"onError"' , ":" , step-ref
                , [ "," , '"metadata"' , ":" , object ] ;

(* ─── Input and timeout ─── *)
input-mode      = '"dtmf"' | '"speech"' | '"dtmf+speech"' ;
timeout-config  = '"noInputTimeout"' , ":" , duration
                , '"noMatchTimeout"' , ":" , duration
                , '"maxRetries"' , ":" , integer
                , '"onMaxRetries"' , ":" , step-ref ;

(* ─── DTMF grammar ─── *)
dtmf-grammar    = "{" , '"pattern"' , ":" , regex
                , '"terminatingDigit"' , ":" , string
                , '"minDigits"' , ":" , integer
                , '"maxDigits"' , ":" , integer , "}" ;

(* ─── Authentication ─── *)
auth-method     = '"credential"' | '"voiceprint"' | '"credential+voiceprint"' ;
credential-spec = "{" , '"field"' , ":" , string
                , '"capture"' , ":" , capture-step , "}" ;

(* ─── Branching ─── *)
condition       = "{" , '"expression"' , ":" , string
                , '"target"' , ":" , step-ref , "}" ;

(* ─── Defaults ─── *)
defaults-block  = "{" , '"noInputTimeout"' , ":" , duration
                , '"noMatchTimeout"' , ":" , duration
                , '"maxRetries"' , ":" , integer
                , '"onMaxRetries"' , ":" , step-ref
                , '"bargeIn"' , ":" , boolean
                , '"voice"' , ":" , voice-spec
                , '"maxStepCount"' , ":" , integer , "}" ;

(* ─── Primitives ─── *)
voice-spec      = "{" , '"engine"' , ":" , string
                , '"name"' , ":" , string
                , '"language"' , ":" , string , "}" ;
step-ref        = string ;           (* step ID reference *)
duration        = string ;           (* ISO 8601 duration, e.g. "PT5S" *)
uri             = string ;           (* RFC 3986 URI *)
regex           = string ;           (* regular expression pattern *)
string          = '"' , { character } , '"' ;
integer         = digit , { digit } ;
boolean         = "true" | "false" ;
object          = "{" , { string , ":" , value } , "}" ;
```

---

## 3. Step Types

### 3.1 Play

Plays a pre-recorded audio file.

```json
{
  "type": "play",
  "src": "https://media.example.com/prompts/welcome.wav",
  "bargeIn": true,
  "loop": 1,
  "onSuccess": "capture_account",
  "onError": "fallback_welcome"
}
```

**Constraints:**
- `src` must resolve to an audio resource. If it returns a non-2xx status or an undecodable format, the step fails immediately — it must not play silence.
- `bargeIn` inherits from `defaults.bargeIn` if omitted.
- `loop: 0` is invalid. Minimum is 1.

**No inline audio.** The grammar does not support base64-encoded audio in messages. Audio resources are always referenced by URI. Inline audio would bloat messages, add latency, and complicate validation. If the AI backend needs to serve dynamically generated audio, it must host it behind a URI.

### 3.2 Say

Synthesizes speech from text.

```json
{
  "type": "say",
  "text": "Please enter your 10-digit account number.",
  "voice": { "engine": "neural", "name": "en-US-Aria", "language": "en-US" },
  "bargeIn": true,
  "ssml": false,
  "onSuccess": "capture_account",
  "onError": "fallback_say_account"
}
```

**SSML example** — reading back an account number and balance with controlled pronunciation:

```json
{
  "type": "say",
  "text": "<speak>Your account number is <say-as interpret-as=\"digits\">5551234567</say-as>. <break time=\"500ms\"/> Your current balance is <say-as interpret-as=\"currency\">$1042.50</say-as>.</speak>",
  "ssml": true,
  "bargeIn": true,
  "onSuccess": "post_balance_menu",
  "onError": "system_error"
}
```

Without SSML, the TTS engine would likely read "5551234567" as "five billion, five hundred fifty-one million..." and "$1042.50" inconsistently. SSML removes the ambiguity.

**Common SSML tags for IVR use:**

| Tag | Purpose | Example |
|-----|---------|---------|
| `<say-as interpret-as="digits">` | Read number as individual digits | "1-2-3-4" not "one thousand..." |
| `<say-as interpret-as="currency">` | Read as money | "$14.50" as "fourteen dollars and fifty cents" |
| `<say-as interpret-as="date">` | Read as date | "02/24/2026" as "February twenty-fourth..." |
| `<say-as interpret-as="telephone">` | Read as phone number | "(555) 123-4567" with natural pauses |
| `<break time="Xms"/>` | Insert pause | Silence between sentences |
| `<emphasis level="strong">` | Stress a word | "Your payment is **past due**" |
| `<prosody rate="slow">` | Control speech rate | Slow down for important information |

**Constraints:**
- When `ssml: true`, `text` must be valid SSML wrapped in a `<speak>` root element. The IVR engine must validate SSML before sending it to the TTS engine. Malformed SSML is an error, not a silent fallback to plain text.
- When `ssml: false` or omitted, `text` is plain text. Any XML-like content in `text` is read literally ("less than speak greater than..."), not interpreted.
- `voice` inherits from `defaults.voice` if omitted.

### 3.3 Capture

Collects caller input. This is the most complex step because it combines prompt playback with input collection, and those two activities overlap when barge-in is enabled.

```json
{
  "type": "capture",
  "input": "dtmf+speech",
  "prompt": {
    "text": "Please say or enter your account number.",
    "bargeIn": true
  },
  "grammar": {
    "pattern": "^[0-9]{10}$",
    "terminatingDigit": "#",
    "minDigits": 10,
    "maxDigits": 10
  },
  "hints": ["account number", "ten digits"],
  "noInputTimeout": "PT7S",
  "noMatchTimeout": "PT3S",
  "maxRetries": 2,
  "onMaxRetries": "transfer_agent",
  "onSuccess": "verify_account",
  "onError": "system_error"
}
```

**Input mode semantics:**

| Mode | Behavior |
|------|----------|
| `dtmf` | Collects DTMF digits only. Speech is ignored. |
| `speech` | Captures spoken utterance only. DTMF is ignored. |
| `dtmf+speech` | Accepts whichever arrives first. See conflict rules below. |

**DTMF + speech conflict resolution (dtmf+speech mode):**

When both DTMF and speech arrive within the same capture window, a conflict exists. The grammar defines a deterministic resolution:

1. If DTMF input arrives first AND matches the grammar pattern, DTMF wins. Speech recognition is cancelled.
2. If speech arrives first AND DTMF has not started, speech wins.
3. If both arrive within 500ms of each other, DTMF wins. Rationale: DTMF is deterministic; speech requires interpretation.

**This 500ms window is a source of user frustration.** A caller who starts speaking and then presses a digit within 500ms will have their speech discarded. This is a known trade-off. The window duration should be configurable in `defaults` in a future revision.

**Barge-in during capture:**

When `prompt.bargeIn` is true and the caller begins input before the prompt finishes:
1. Prompt playback stops immediately.
2. Input collection continues normally.
3. The AI backend receives the partial prompt text (how much was played) in the outcome metadata as `promptPlayedPercent`.

**Critical edge case:** If the prompt is "Please say your name", and the caller barges in after "Please say", the caller heard an incomplete instruction. The AI backend must be prepared for utterances that don't match the expected intent because the caller didn't hear the full prompt. The grammar cannot solve this — it is an AI backend responsibility.

**Timeout behavior:**
- `noInputTimeout`: Duration of silence before triggering a no-input event. Starts after the prompt finishes (or after barge-in cancels the prompt).
- `noMatchTimeout`: Duration after ASR returns a result that doesn't match the grammar. Irrelevant for speech-only mode (the AI backend decides what "matches").
- `maxRetries`: The prompt replays up to this many times on no-input/no-match. On the final retry failure, `onMaxRetries` fires.
- All three inherit from `defaults` if omitted at the step level.

### 3.4 Authenticate

Structured multi-step authentication. This is syntactic sugar — it could be expressed as a sequence of capture steps and an API call — but authentication has enough invariants to justify a dedicated step type.

```json
{
  "type": "authenticate",
  "method": "credential+voiceprint",
  "credentials": [
    {
      "field": "accountNumber",
      "capture": {
        "type": "capture",
        "input": "dtmf",
        "prompt": { "text": "Please enter your account number." },
        "grammar": { "pattern": "^[0-9]{10}$", "terminatingDigit": "#", "minDigits": 10, "maxDigits": 10 }
      }
    },
    {
      "field": "pin",
      "capture": {
        "type": "capture",
        "input": "dtmf",
        "prompt": { "text": "Please enter your 4-digit PIN." },
        "grammar": { "pattern": "^[0-9]{4}$", "terminatingDigit": "#", "minDigits": 4, "maxDigits": 4 }
      }
    }
  ],
  "maxAttempts": 3,
  "onLockout": "transfer_fraud",
  "onSuccess": "authenticated_menu",
  "onError": "system_error"
}
```

**Credential collection order:** Credentials are collected in array order. This is not negotiable — the IVR engine must not reorder them.

**Voiceprint mode:**
When `method` includes `voiceprint`, the IVR engine streams caller audio to the biometric service during the entire authentication step. The grammar does not define the biometric protocol — it only declares that biometric verification is required. The IVR engine's biometric integration is an implementation detail.

**Partial failure during authentication:**

This is the most dangerous edge case in the grammar. If the AI backend becomes unreachable after the first credential is collected but before authentication completes:

1. The IVR engine must NOT store partially collected credentials.
2. The IVR engine must NOT retry authentication from the middle — it must restart from the first credential.
3. The IVR engine must activate the fallback flow with the authentication context cleared.

**Lockout:** After `maxAttempts` failed authentication attempts, `onLockout` fires. This is distinct from `onError` (system failure) and `onMaxRetries` (input failure). Lockout typically routes to a fraud investigation queue.

**Security constraint:** `bargeIn` is forced to `false` for all prompts within an authentication step. Rationale: barge-in during PIN entry could allow a brute-force timing attack where the attacker measures prompt interruption latency to infer digit acceptance. This is a deliberate restriction.

### 3.5 Transfer

Routes the caller to a destination (agent queue, extension, external number).

```json
{
  "type": "transfer",
  "destination": "queue://support/billing",
  "reason": "authentication_failed",
  "announceToAgent": "Caller failed authentication 3 times on account ending 4521."
}
```

**Constraints:**
- Transfer is a terminal step. It has no `onSuccess` or `onError` — once the transfer initiates, the IVR flow for this call is complete.
- If the transfer fails (queue full, destination unreachable), the IVR engine must log the failure and hang up. It must not silently loop.

### 3.6 Hangup

Terminates the call.

```json
{
  "type": "hangup",
  "reason": "caller_completed_self_service"
}
```

**Constraints:**
- Terminal step. No `onSuccess` or `onError`.
- `reason` is mandatory. IVR engines must not hang up without a reason — this is critical for analytics and debugging.

### 3.7 Branch

Conditional routing based on expressions evaluated against the current turn context.

```json
{
  "type": "branch",
  "conditions": [
    { "expression": "intent == 'balance_inquiry'", "target": "play_balance" },
    { "expression": "intent == 'transfer_funds'", "target": "auth_step" },
    { "expression": "confidence < 0.6", "target": "capture_retry" }
  ],
  "default": "fallback_menu"
}
```

**Evaluation order:** Conditions are evaluated top to bottom. First match wins. If no condition matches, `default` fires.

**Expression language:** The grammar intentionally does not define the expression language. It is an implementation detail of the IVR engine. However, the expression must be a pure function of the turn context — it must not have side effects.

**Open question:** Should the grammar constrain the expression language to a safe subset (no loops, no function calls)? An unrestricted expression language in a branch step is a code injection vector if expressions come from the AI backend at runtime. Decision deferred — flagged as a security concern.

### 3.8 Goto

Unconditional jump to another step.

```json
{
  "type": "goto",
  "target": "main_menu"
}
```

**Constraints:**
- `target` must reference a step that exists in `steps`. Dangling references are a validation error.
- Goto enables loops. The `defaults.maxStepCount` limit prevents infinite loops at runtime.

---

## 4. Fallback Flows

When the AI backend is unreachable, the IVR engine switches to its local fallback flow. This creates a fundamental tension:

**The IVR is supposed to be stateless, but fallback flows are state.**

Resolution: the fallback flow is a separate, self-contained flow definition embedded in the main flow. It does not share steps with the main flow. The IVR engine has exactly two modes:

1. **Online mode** — executes steps from the AI backend, stateless
2. **Fallback mode** — executes the local fallback flow, stateful

The transition between modes is one-way per failure event:

```
Online → Fallback (on AI backend timeout)
```

The IVR engine must NOT attempt to return to online mode mid-call after entering fallback. Rationale: the AI backend lost context for this call. Resuming would require re-establishing state, which is undefined.

```json
{
  "fallback": "fallback_entry",
  "steps": {
    "fallback_entry": {
      "type": "say",
      "text": "We are experiencing technical difficulties. Let me connect you to an agent.",
      "bargeIn": false,
      "onSuccess": "fallback_transfer",
      "onError": "fallback_hangup"
    },
    "fallback_transfer": {
      "type": "transfer",
      "destination": "queue://support/general",
      "reason": "ai_backend_unreachable"
    },
    "fallback_hangup": {
      "type": "hangup",
      "reason": "fallback_transfer_failed"
    }
  }
}
```

**Timeout definition:** The AI backend is considered unreachable after `defaults.aiBackendTimeout` (must be defined in `defaults`). This is the total time for a single turn response, not a TCP timeout.

---

## 5. Runtime Protocol

### 5.1 Turn Structure

Each turn consists of an **outcome** (IVR to AI backend) and a **directive** (AI backend to IVR).

**Outcome (IVR sends):**

```json
{
  "callId": "call-uuid-001",
  "turnId": 7,
  "stepId": "capture_account",
  "stepType": "capture",
  "result": "success",
  "data": {
    "inputMode": "dtmf",
    "utterance": "5551234567",
    "confidence": 1.0,
    "dtmfDigits": "5551234567#",
    "promptPlayedPercent": 100,
    "durationMs": 8340
  },
  "timestamp": "2026-02-24T14:30:22.451Z"
}
```

**Directive (AI backend sends):**

```json
{
  "callId": "call-uuid-001",
  "turnId": 8,
  "steps": [
    {
      "type": "say",
      "text": "Thank you. I found your account.",
      "bargeIn": false,
      "onSuccess": "_next",
      "onError": "_error"
    },
    {
      "type": "capture",
      "input": "speech",
      "prompt": {
        "text": "How can I help you today?",
        "bargeIn": true
      },
      "onSuccess": "_respond",
      "onError": "_error"
    }
  ]
}
```

**The `steps` array in a directive is an ordered sequence.** The IVR engine executes them in order. The special step references `_next` (advance to next step in the array), `_respond` (send outcome to AI backend), and `_error` (activate error handling) are reserved.

### 5.2 Outcome Result Types

| Result | Meaning |
|--------|---------|
| `success` | Step completed normally |
| `no_input` | Caller provided no input within timeout (after all retries) |
| `no_match` | Caller input didn't match grammar (after all retries) |
| `barge_in` | Caller interrupted prompt (only meaningful for play/say without capture) |
| `hangup` | Caller hung up during this step |
| `error` | System error (TTS failure, ASR failure, etc.) |
| `auth_success` | Authentication succeeded |
| `auth_failed` | Authentication failed (wrong credentials) |
| `auth_lockout` | Authentication locked out (max attempts exceeded) |

### 5.3 Turn Sequencing

`turnId` is a monotonically increasing integer starting at 1. The AI backend must reject any outcome where `turnId` is not exactly `previousTurnId + 1`. This prevents replay attacks and detects lost messages.

**If the IVR engine receives a directive with a `turnId` that doesn't match its expected value, it must enter fallback mode.** This is not recoverable — the state is desynchronized.

---

## 6. Validation Rules

A flow definition must pass these validation rules before it is accepted by an IVR engine. Validation failures are fatal — partial flows must not execute.

### 6.1 Structural Validation

| Rule | Description |
|------|-------------|
| V-001 | `defaults` block is present and complete (all fields defined) |
| V-002 | `fallback` references a step that exists in `steps` |
| V-003 | `entryPoint` references a step that exists in `steps` |
| V-004 | Every `step-ref` in every step resolves to an existing step in `steps` |
| V-005 | No step references itself directly in `onSuccess` or `onError` (immediate cycle) |
| V-006 | Every non-terminal step has both `onSuccess` and `onError` |
| V-007 | Terminal steps (`transfer`, `hangup`) do not have `onSuccess` or `onError` |

### 6.2 Semantic Validation

| Rule | Description |
|------|-------------|
| V-101 | `capture` steps with `input: "dtmf"` or `input: "dtmf+speech"` must have a `grammar` block |
| V-102 | `grammar.minDigits` <= `grammar.maxDigits` |
| V-103 | `auth` steps with `method: "credential"` or `method: "credential+voiceprint"` must have at least one credential |
| V-104 | `auth` step credential capture sub-steps must not have `bargeIn: true` |
| V-105 | `defaults.maxStepCount` must be > 0 and <= 1000 |
| V-106 | `play` step `loop` must be >= 1 |
| V-107 | `branch` step must have at least one condition and a `default` |

### 6.3 Reachability Validation

| Rule | Description |
|------|-------------|
| V-201 | Every step in `steps` must be reachable from `entryPoint` (no orphan steps) |
| V-202 | Every path from `entryPoint` must eventually reach a terminal step (`transfer` or `hangup`) or `_respond`. A flow with paths that loop indefinitely without a terminal is invalid. (Note: `maxStepCount` is a runtime safety net, not a substitute for this check.) |

---

## 7. Transport Bindings

The grammar is transport-agnostic. This section defines how outcomes and directives are carried over specific transports.

### 7.1 HTTP Binding

- **Outcome:** `POST /ivr/turn` with the outcome JSON as the request body.
- **Directive:** The HTTP response body contains the directive JSON.
- **Content-Type:** `application/json`
- **Timeout:** The IVR engine must enforce `defaults.aiBackendTimeout` as the HTTP request timeout. On timeout, enter fallback mode.

**Limitation:** HTTP is request/response. The AI backend cannot push directives unprompted. If the AI backend needs to interrupt the IVR mid-step (e.g., fraud alert), HTTP cannot support this.

### 7.2 WebSocket Binding

- **Connection:** The IVR engine opens a WebSocket connection at call start and closes it at call end.
- **Outcome:** IVR sends a text frame containing the outcome JSON.
- **Directive:** AI backend sends a text frame containing the directive JSON.
- **Audio streaming:** For voiceprint authentication, the IVR engine sends binary frames containing audio chunks. The framing protocol for audio is out of scope for this grammar.

**Advantage over HTTP:** The AI backend can push directives at any time (e.g., "hang up — fraud detected"). The grammar supports this via a special **server-initiated directive** with `turnId: 0`, which the IVR engine must process immediately regardless of current step.

**Heartbeat:** The IVR engine must send a ping frame every 10 seconds. If no pong is received within 5 seconds, the connection is considered dead and the IVR enters fallback mode.

---

## 8. JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://schema.example.com/ivr-grammar/0.1.0/flow.json",
  "title": "IVR Flow Definition",
  "type": "object",
  "required": ["id", "version", "defaults", "fallback", "entryPoint", "steps"],
  "additionalProperties": false,
  "properties": {
    "id": { "type": "string", "minLength": 1 },
    "version": { "type": "string", "pattern": "^[0-9]+\\.[0-9]+\\.[0-9]+$" },
    "description": { "type": "string" },
    "defaults": { "$ref": "#/$defs/defaults" },
    "fallback": { "type": "string", "minLength": 1 },
    "entryPoint": { "type": "string", "minLength": 1 },
    "steps": {
      "type": "object",
      "minProperties": 1,
      "additionalProperties": { "$ref": "#/$defs/step" }
    }
  },
  "$defs": {
    "defaults": {
      "type": "object",
      "required": [
        "noInputTimeout", "noMatchTimeout", "maxRetries",
        "onMaxRetries", "bargeIn", "voice", "maxStepCount",
        "aiBackendTimeout"
      ],
      "additionalProperties": false,
      "properties": {
        "noInputTimeout": { "$ref": "#/$defs/duration" },
        "noMatchTimeout": { "$ref": "#/$defs/duration" },
        "maxRetries": { "type": "integer", "minimum": 0, "maximum": 10 },
        "onMaxRetries": { "type": "string", "minLength": 1 },
        "bargeIn": { "type": "boolean" },
        "voice": { "$ref": "#/$defs/voiceSpec" },
        "maxStepCount": { "type": "integer", "minimum": 1, "maximum": 1000 },
        "aiBackendTimeout": { "$ref": "#/$defs/duration" }
      }
    },
    "voiceSpec": {
      "type": "object",
      "required": ["engine", "name", "language"],
      "additionalProperties": false,
      "properties": {
        "engine": { "type": "string" },
        "name": { "type": "string" },
        "language": { "type": "string", "pattern": "^[a-z]{2}-[A-Z]{2}$" }
      }
    },
    "duration": {
      "type": "string",
      "pattern": "^PT[0-9]+[SM]$",
      "description": "ISO 8601 duration (e.g. PT5S, PT2M)"
    },
    "step": {
      "type": "object",
      "required": ["type"],
      "discriminator": { "propertyName": "type" },
      "oneOf": [
        { "$ref": "#/$defs/playStep" },
        { "$ref": "#/$defs/sayStep" },
        { "$ref": "#/$defs/captureStep" },
        { "$ref": "#/$defs/authStep" },
        { "$ref": "#/$defs/transferStep" },
        { "$ref": "#/$defs/hangupStep" },
        { "$ref": "#/$defs/branchStep" },
        { "$ref": "#/$defs/gotoStep" }
      ]
    },
    "playStep": {
      "type": "object",
      "required": ["type", "src", "onSuccess", "onError"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "play" },
        "src": { "type": "string", "format": "uri" },
        "bargeIn": { "type": "boolean" },
        "loop": { "type": "integer", "minimum": 1 },
        "onSuccess": { "type": "string" },
        "onError": { "type": "string" },
        "metadata": { "type": "object" }
      }
    },
    "sayStep": {
      "type": "object",
      "required": ["type", "text", "onSuccess", "onError"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "say" },
        "text": { "type": "string", "minLength": 1 },
        "voice": { "$ref": "#/$defs/voiceSpec" },
        "bargeIn": { "type": "boolean" },
        "ssml": { "type": "boolean" },
        "onSuccess": { "type": "string" },
        "onError": { "type": "string" },
        "metadata": { "type": "object" }
      }
    },
    "captureStep": {
      "type": "object",
      "required": ["type", "input", "onSuccess", "onError"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "capture" },
        "input": { "enum": ["dtmf", "speech", "dtmf+speech"] },
        "prompt": {
          "type": "object",
          "properties": {
            "text": { "type": "string" },
            "src": { "type": "string", "format": "uri" },
            "bargeIn": { "type": "boolean" }
          }
        },
        "grammar": { "$ref": "#/$defs/dtmfGrammar" },
        "hints": { "type": "array", "items": { "type": "string" } },
        "noInputTimeout": { "$ref": "#/$defs/duration" },
        "noMatchTimeout": { "$ref": "#/$defs/duration" },
        "maxRetries": { "type": "integer", "minimum": 0, "maximum": 10 },
        "onMaxRetries": { "type": "string" },
        "onSuccess": { "type": "string" },
        "onError": { "type": "string" },
        "metadata": { "type": "object" }
      }
    },
    "dtmfGrammar": {
      "type": "object",
      "required": ["pattern", "terminatingDigit", "minDigits", "maxDigits"],
      "additionalProperties": false,
      "properties": {
        "pattern": { "type": "string" },
        "terminatingDigit": { "type": "string", "maxLength": 1 },
        "minDigits": { "type": "integer", "minimum": 1 },
        "maxDigits": { "type": "integer", "minimum": 1 }
      }
    },
    "authStep": {
      "type": "object",
      "required": ["type", "method", "credentials", "maxAttempts", "onLockout", "onSuccess", "onError"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "authenticate" },
        "method": { "enum": ["credential", "voiceprint", "credential+voiceprint"] },
        "credentials": {
          "type": "array",
          "minItems": 1,
          "items": {
            "type": "object",
            "required": ["field", "capture"],
            "properties": {
              "field": { "type": "string" },
              "capture": { "$ref": "#/$defs/captureStep" }
            }
          }
        },
        "maxAttempts": { "type": "integer", "minimum": 1, "maximum": 5 },
        "onLockout": { "type": "string" },
        "onSuccess": { "type": "string" },
        "onError": { "type": "string" },
        "metadata": { "type": "object" }
      }
    },
    "transferStep": {
      "type": "object",
      "required": ["type", "destination", "reason"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "transfer" },
        "destination": { "type": "string", "minLength": 1 },
        "reason": { "type": "string", "minLength": 1 },
        "announceToAgent": { "type": "string" }
      }
    },
    "hangupStep": {
      "type": "object",
      "required": ["type", "reason"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "hangup" },
        "reason": { "type": "string", "minLength": 1 }
      }
    },
    "branchStep": {
      "type": "object",
      "required": ["type", "conditions", "default"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "branch" },
        "conditions": {
          "type": "array",
          "minItems": 1,
          "items": {
            "type": "object",
            "required": ["expression", "target"],
            "properties": {
              "expression": { "type": "string" },
              "target": { "type": "string" }
            }
          }
        },
        "default": { "type": "string" }
      }
    },
    "gotoStep": {
      "type": "object",
      "required": ["type", "target"],
      "additionalProperties": false,
      "properties": {
        "type": { "const": "goto" },
        "target": { "type": "string" }
      }
    }
  }
}
```

---

## 9. Open Questions and Known Contradictions

### 9.1 Stateless IVR with Local Fallback (Contradiction)

The IVR is defined as stateless (Section 1), but fallback flows require the IVR to maintain a local flow graph and execute it stateully (Section 4). This is resolved by defining two distinct modes, but it means the IVR engine is not truly stateless — it must always carry the fallback flow in memory. Any implementation that treats the IVR as a pure function of the last directive is incorrect.

### 9.2 Branch Expression Safety

Branch expressions (Section 3.7) come from either the static flow definition or the AI backend at runtime. If the AI backend can send arbitrary expressions, this is a code injection vector. The grammar must either:
- Define a safe expression subset (comparisons and boolean logic only, no function calls)
- Restrict branch steps to static flow definitions only (AI backend cannot send branch steps)

Neither is decided. This is a blocking issue for production use.

### 9.3 Caller Hangup During Authentication

If the caller hangs up after entering their account number but before entering their PIN, the outcome is `hangup`. The AI backend receives the partial credential data in the outcome. The grammar does not define whether the AI backend should persist, discard, or audit this data. This is a compliance concern (PCI-DSS, GDPR).

**Recommendation:** The outcome for a `hangup` during an `authenticate` step must NOT include any collected credentials. The IVR engine must strip credential data from the outcome before sending it.

### 9.4 Prompt Audio Failure

If a `play` step's `src` URI is unreachable, the step fails. But what if it returns audio in an unsupported format? Or audio that is 0 bytes? Or audio that is 45 minutes long? The grammar needs size and duration limits for audio resources. Deferred.

### 9.5 AI Backend Sends Unknown Step Type

If the AI backend sends `{"type": "foobar"}`, the IVR engine must treat this as an error and execute the step's `onError` handler. But if the unknown step type doesn't have `onError` (because it's malformed), the IVR must enter fallback mode.

### 9.6 Turn ID Overflow

`turnId` is defined as a monotonically increasing integer. For long-running calls (hours), this is fine. But the grammar does not define the maximum value or what happens at overflow. Recommendation: use 64-bit integers. A call would need 9.2 quintillion turns to overflow.

---

## 10. Complete Example

```json
{
  "id": "billing-inquiry-flow",
  "version": "1.0.0",
  "description": "Self-service billing inquiry with AI-powered natural language",
  "defaults": {
    "noInputTimeout": "PT5S",
    "noMatchTimeout": "PT3S",
    "maxRetries": 2,
    "onMaxRetries": "max_retries_transfer",
    "bargeIn": true,
    "voice": { "engine": "neural", "name": "en-US-Aria", "language": "en-US" },
    "maxStepCount": 200,
    "aiBackendTimeout": "PT10S"
  },
  "fallback": "fallback_entry",
  "entryPoint": "welcome",
  "steps": {
    "welcome": {
      "type": "say",
      "text": "Welcome to Acme Billing. How can I help you today?",
      "bargeIn": true,
      "onSuccess": "main_capture",
      "onError": "fallback_entry"
    },
    "main_capture": {
      "type": "capture",
      "input": "speech",
      "prompt": {
        "text": "You can say things like 'check my balance' or 'pay my bill'.",
        "bargeIn": true
      },
      "hints": ["check balance", "pay bill", "dispute charge", "speak to agent"],
      "onSuccess": "_respond",
      "onError": "system_error"
    },
    "auth_step": {
      "type": "authenticate",
      "method": "credential",
      "credentials": [
        {
          "field": "accountNumber",
          "capture": {
            "type": "capture",
            "input": "dtmf",
            "prompt": { "text": "Please enter your 10-digit account number followed by the pound sign." },
            "grammar": { "pattern": "^[0-9]{10}$", "terminatingDigit": "#", "minDigits": 10, "maxDigits": 10 },
            "onSuccess": "_next",
            "onError": "system_error"
          }
        },
        {
          "field": "pin",
          "capture": {
            "type": "capture",
            "input": "dtmf",
            "prompt": { "text": "Now enter your 4-digit PIN." },
            "grammar": { "pattern": "^[0-9]{4}$", "terminatingDigit": "#", "minDigits": 4, "maxDigits": 4 },
            "onSuccess": "_next",
            "onError": "system_error"
          }
        }
      ],
      "maxAttempts": 3,
      "onLockout": "lockout_transfer",
      "onSuccess": "_respond",
      "onError": "system_error"
    },
    "max_retries_transfer": {
      "type": "transfer",
      "destination": "queue://support/general",
      "reason": "max_retries_exceeded"
    },
    "lockout_transfer": {
      "type": "transfer",
      "destination": "queue://security/fraud",
      "reason": "authentication_lockout"
    },
    "system_error": {
      "type": "say",
      "text": "I'm sorry, we're experiencing a system issue.",
      "bargeIn": false,
      "onSuccess": "error_transfer",
      "onError": "error_transfer"
    },
    "error_transfer": {
      "type": "transfer",
      "destination": "queue://support/general",
      "reason": "system_error"
    },
    "goodbye": {
      "type": "say",
      "text": "Thank you for calling Acme. Goodbye.",
      "bargeIn": false,
      "onSuccess": "end_call",
      "onError": "end_call"
    },
    "end_call": {
      "type": "hangup",
      "reason": "caller_completed_self_service"
    },
    "fallback_entry": {
      "type": "say",
      "text": "We are experiencing technical difficulties. Let me connect you to an agent who can help.",
      "bargeIn": false,
      "onSuccess": "fallback_transfer",
      "onError": "fallback_hangup"
    },
    "fallback_transfer": {
      "type": "transfer",
      "destination": "queue://support/general",
      "reason": "ai_backend_unreachable"
    },
    "fallback_hangup": {
      "type": "hangup",
      "reason": "fallback_transfer_failed"
    }
  }
}
```
