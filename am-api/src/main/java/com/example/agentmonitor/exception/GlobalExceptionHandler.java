package com.example.agentmonitor.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NodeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNodeNotFound(NodeNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "NODE_NOT_FOUND");
        body.put("message", ex.getMessage());
        body.put("nodeId", ex.getNodeId());
        body.put("registrationUrl", "/api/v1/nodes/register");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConnection(InvalidConnectionException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "INVALID_CONNECTION");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_ERROR");
        body.put("message", "Request validation failed");
        body.put("fieldErrors", fieldErrors);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RecordingNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRecordingNotFound(RecordingNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "RECORDING_NOT_FOUND");
        body.put("message", ex.getMessage());
        body.put("filename", ex.getFilename());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidRecordingException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRecording(InvalidRecordingException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "INVALID_RECORDING");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IvrFlowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleIvrFlowNotFound(IvrFlowNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "IVR_FLOW_NOT_FOUND");
        body.put("message", ex.getMessage());
        body.put("flowId", ex.getFlowId().toString());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IvrFlowValidationException.class)
    public ResponseEntity<Map<String, Object>> handleIvrFlowValidation(IvrFlowValidationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "IVR_FLOW_VALIDATION_FAILED");
        body.put("message", ex.getMessage());
        body.put("validationIssues", ex.getValidationIssues());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IvrFlowConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIvrFlowConflict(IvrFlowConflictException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "IVR_FLOW_CONFLICT");
        body.put("message", ex.getMessage());
        body.put("flowId", ex.getFlowId().toString());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(TestScenarioNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTestScenarioNotFound(
            TestScenarioNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "TEST_SCENARIO_NOT_FOUND");
        body.put("message", ex.getMessage());
        body.put("scenarioId", ex.getScenarioId().toString());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(TestScenarioConflictException.class)
    public ResponseEntity<Map<String, Object>> handleTestScenarioConflict(
            TestScenarioConflictException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "TEST_SCENARIO_CONFLICT");
        body.put("message", ex.getMessage());
        body.put("scenarioId", ex.getScenarioId().toString());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "FILE_TOO_LARGE");
        body.put("message", "File exceeds maximum upload size");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "NOT_FOUND");
        body.put("message", "Resource not found: " + ex.getResourcePath());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", "An internal error occurred");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
