package io.squados.dashboard.api.model;

import java.time.Instant;
import java.util.Map;

/**
 * A trace span record — GET /api/v1/traces.
 */
public record TraceSpan(
        String              traceId,
        String              spanId,
        String              spanName,
        String              agentName,
        String              role,
        Instant             startTime,
        Instant             endTime,
        long                durationMs,
        String              status,         // OK, ERROR
        String              errorMessage,
        int                 promptTokens,
        int                 completionTokens,
        int                 totalTokens,
        int                 inputLength,
        int                 outputLength,
        Map<String, String> attributes
) {}
