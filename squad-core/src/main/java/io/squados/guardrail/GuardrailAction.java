package io.squados.guardrail;

/**
 * Action to take when a guardrail filter detects a violation.
 */
public enum GuardrailAction {
    /** Log the violation and allow the request to proceed. */
    LOG_ONLY,

    /** Redact the offending content and allow modified request to proceed. */
    REDACT,

    /** Block the request and throw GuardrailException. */
    BLOCK_AND_LOG
}
