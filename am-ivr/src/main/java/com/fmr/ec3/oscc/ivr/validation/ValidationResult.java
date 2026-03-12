package com.fmr.ec3.oscc.ivr.validation;

import java.util.List;

/**
 * Immutable result of flow validation.
 */
public class ValidationResult {

    private final boolean valid;
    private final List<ValidationIssue> issues;

    public ValidationResult(boolean valid, List<ValidationIssue> issues) {
        this.valid = valid;
        this.issues = List.copyOf(issues);
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.ERROR)
                .toList();
    }

    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.WARNING)
                .toList();
    }
}
