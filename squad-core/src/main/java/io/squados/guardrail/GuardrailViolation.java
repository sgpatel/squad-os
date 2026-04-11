package io.squados.guardrail;

import java.time.Instant;

/**
 * Records a single guardrail violation for audit log purposes.
 */
public record GuardrailViolation(
        String         filterName,
        String         violationType,
        String         matchedValue,
        float          confidence,
        GuardrailAction action,
        Instant        detectedAt
) {
    public GuardrailViolation(String filterName, String violationType,
                               String matchedValue, float confidence, GuardrailAction action) {
        this(filterName, violationType, matchedValue, confidence, action, Instant.now());
    }
}
