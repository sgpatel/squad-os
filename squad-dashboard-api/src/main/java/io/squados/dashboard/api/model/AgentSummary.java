package io.squados.dashboard.api.model;

/**
 * Summary of a registered agent's runtime state — returned by GET /api/v1/agents.
 */
public record AgentSummary(
        String  name,
        String  role,
        String  description,
        String  status,         // ACTIVE, IDLE, ERROR, RATE_LIMITED
        long    totalCalls,
        long    successCalls,
        long    errorCalls,
        double  avgLatencyMs,
        long    totalTokens,
        long    totalPromptTokens,
        long    totalCompletionTokens,
        double  successRate,    // 0.0–1.0
        String  lastCallAt,     // ISO-8601 or null
        String  annotations     // comma-separated active annotations
) {}
