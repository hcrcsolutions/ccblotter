package com.fmr.ec3.oscc.ivr.server.exception;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FlowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFlowNotFound(FlowNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "FLOW_NOT_FOUND");
        body.put("message", ex.getMessage());
        body.put("flowId", ex.getFlowId());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IvrFlowException.class)
    public ResponseEntity<Map<String, Object>> handleIvrFlowException(IvrFlowException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "IVR_FLOW_ERROR");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
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
