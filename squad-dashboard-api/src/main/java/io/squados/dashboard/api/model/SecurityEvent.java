package io.squados.dashboard.api.model;

import java.time.Instant;

/**
 * A security / audit event — GET /api/v1/security.
 */
public record SecurityEvent(
        String  id,
        Instant timestamp,
        String  type,       // AUTH_FAILURE, GUARDRAIL_BLOCK, RATE_LIMIT, INJECTION_DETECTED, PII_REDACTED, ACCESS_DENIED
        String  agentName,
        String  user,       // principal or null
        String  severity,   // LOW, MEDIUM, HIGH, CRITICAL
        String  message,
        String  detail      // full payload (truncated)
) {}
