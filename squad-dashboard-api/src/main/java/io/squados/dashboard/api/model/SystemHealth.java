package io.squados.dashboard.api.model;

import java.time.Instant;
import java.util.Map;

/**
 * System health snapshot — GET /api/v1/health.
 */
public record SystemHealth(
        String              status,         // UP, DEGRADED, DOWN
        Instant             timestamp,
        long                uptimeMs,
        int                 activeAgents,
        int                 totalAgents,
        int                 sseSubscribers,
        long                jvmHeapUsedMb,
        long                jvmHeapMaxMb,
        double              heapUsagePct,
        int                 threadCount,
        Map<String, String> components      // "redis" -> "UP", "llm" -> "UNKNOWN"
) {}
