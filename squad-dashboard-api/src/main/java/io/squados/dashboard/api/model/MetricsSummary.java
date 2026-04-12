package io.squados.dashboard.api.model;

import java.util.List;
import java.util.Map;

/**
 * Aggregate metrics snapshot — returned by GET /api/v1/metrics.
 */
public record MetricsSummary(
        // Totals
        long   totalAgentCalls,
        long   totalSuccessCalls,
        long   totalErrorCalls,
        double overallSuccessRate,

        // Tokens
        long   totalTokensConsumed,
        long   totalPromptTokens,
        long   totalCompletionTokens,

        // Latency
        double avgLatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,

        // Per-agent breakdown
        List<AgentMetricEntry> perAgent,

        // Time-series (last 60 minutes, 1-min buckets)
        List<TimeSeriesPoint>  callsOverTime,
        List<TimeSeriesPoint>  tokensOverTime,
        List<TimeSeriesPoint>  latencyOverTime,

        // Error breakdown
        Map<String, Long>      errorsByType,

        // Active rate limits
        int   activeRateLimits,
        int   rateLimitBreach24h
) {

    public record AgentMetricEntry(
            String name,
            String role,
            long   calls,
            long   errors,
            long   tokens,
            double avgLatencyMs
    ) {}

    public record TimeSeriesPoint(
            String label,    // "HH:mm"
            long   value
    ) {}
}
