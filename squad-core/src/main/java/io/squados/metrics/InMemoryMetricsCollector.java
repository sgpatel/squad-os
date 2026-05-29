package io.squados.metrics;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory MetricsPort implementation.
 *
 * Thread-safe counters backed by ConcurrentHashMap.
 * Suitable for testing and single-JVM deployments.
 * For production Prometheus/Micrometer metrics, use the
 * MicrometerMetricsAdapter in squad-spring-boot-starter.
 *
 * Queryable via getCounters() and avgLatencyMs().
 */
public class InMemoryMetricsCollector implements MetricsPort {

    private final Map<String, AtomicLong> counters   = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> latencySum = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> callCount  = new ConcurrentHashMap<>();

    @Override
    public void recordCall(String agent, String role, String status,
                           long latencyMs, String namespace, String[] tags) {
        inc(namespace + ".agent.calls.total." + agent + "." + status);
        inc(namespace + ".agent.calls.total." + role  + "." + status);
        latencySum.computeIfAbsent(agent, k -> new AtomicLong(0)).addAndGet(latencyMs);
        callCount .computeIfAbsent(agent, k -> new AtomicLong(0)).incrementAndGet();
    }

    @Override
    public void recordTokens(String agent, String role,
                             String type, int count,
                             String namespace, String[] tags) {
        inc(namespace + ".agent.tokens." + type + "." + agent, count);
        inc(namespace + ".agent.tokens." + type + "." + role,  count);
    }

    @Override
    public void recordError(String agent, String role,
                            String errorType, String namespace, String[] tags) {
        inc(namespace + ".agent.errors." + errorType + "." + agent);
        inc(namespace + ".agent.errors." + errorType + "." + role);
    }

    // ── Query API ─────────────────────────────────────────────────────

    /** All counters as an unmodifiable snapshot. */
    public Map<String, AtomicLong> getCounters() {
        return Collections.unmodifiableMap(counters);
    }

    /** Average call latency in milliseconds for the named agent. */
    public double avgLatencyMs(String agent) {
        AtomicLong total = latencySum.get(agent);
        AtomicLong count = callCount.get(agent);
        if (total == null || count == null || count.get() == 0) return 0.0;
        return (double) total.get() / count.get();
    }

    /** Total call count for the named agent. */
    public long totalCalls(String agent) {
        AtomicLong c = callCount.get(agent);
        return c != null ? c.get() : 0L;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void inc(String key)             { inc(key, 1L); }
    private void inc(String key, long delta) {
        counters.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(delta);
    }
}
