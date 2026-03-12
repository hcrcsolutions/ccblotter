package com.fmr.ec3.oscc.ivr.validation;

/**
 * Immutable validation issue found during flow validation.
 */
public class ValidationIssue {

    private final ValidationSeverity severity;
    private final String nodeId;
    private final String code;
    private final String message;

    public ValidationIssue(ValidationSeverity severity, String nodeId, String code, String message) {
        this.severity = severity;
        this.nodeId = nodeId;
        this.code = code;
        this.message = message;
    }

    public ValidationSeverity getSeverity() {
        return severity;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return severity + " [" + code + "] node=" + nodeId + ": " + message;
    }
}
