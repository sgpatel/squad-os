package io.squados.guardrail;

import java.util.List;

/**
 * Aggregated result of running all guardrail filters for one agent call.
 */
public record GuardrailResult(
        boolean                 passed,
        String                  processedText,
        List<GuardrailViolation> violations
) {
    public static GuardrailResult clean(String text) {
        return new GuardrailResult(true, text, List.of());
    }

    public static GuardrailResult blocked(String text, List<GuardrailViolation> violations) {
        return new GuardrailResult(false, text, violations);
    }

    public static GuardrailResult withViolations(String text, List<GuardrailViolation> violations) {
        // Blocked if any violation has BLOCK_AND_LOG action
        boolean blocked = violations.stream()
            .anyMatch(v -> v.action() == GuardrailAction.BLOCK_AND_LOG);
        return new GuardrailResult(!blocked, text, violations);
    }

    public boolean hasViolations() { return !violations.isEmpty(); }
    public int     violationCount(){ return violations.size(); }
}
