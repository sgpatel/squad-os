package io.squados.boot;

import io.squados.metrics.MetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MetricsPort adapter that bridges to Micrometer/Prometheus.
 *
 * Emits the following meters per @Observe-annotated agent call:
 *
 *   {ns}_agent_calls_total{agent, role, status}     Counter
 *   {ns}_agent_latency_seconds{agent, role}         Timer
 *   {ns}_agent_tokens_total{agent, role, type}      Counter
 *   {ns}_agent_errors_total{agent, role, error}     Counter
 *
 * Registered automatically when:
 *   - io.micrometer:micrometer-core is on the classpath (transitively via spring-boot-actuator)
 *   - A MeterRegistry bean is present
 *   - No other MetricsPort bean is declared
 *
 * To customise add your own @Bean MetricsPort in your @SpringBootApplication class.
 */
public class MicrometerMetricsAdapter implements MetricsPort {

    private final MeterRegistry registry;
    // Cache meters to avoid per-call lookup overhead
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer>   timerCache   = new ConcurrentHashMap<>();

    public MicrometerMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordCall(String agentName, String role, String status,
                           long latencyMs, String namespace, String[] tags) {
        // Counter: {ns}_agent_calls_total
        String counterKey = namespace + ".agent.calls.total." + agentName + "." + status;
        counterCache.computeIfAbsent(counterKey, k ->
            Counter.builder(namespace + ".agent.calls.total")
                .tag("agent",  agentName)
                .tag("role",   role)
                .tag("status", status)
                .tags(flattenTags(tags))
                .description("Total agent LLM calls")
                .register(registry)
        ).increment();

        // Timer: {ns}_agent_latency_seconds
        String timerKey = namespace + ".agent.latency." + agentName;
        timerCache.computeIfAbsent(timerKey, k ->
            Timer.builder(namespace + ".agent.latency.seconds")
                .tag("agent", agentName)
                .tag("role",  role)
                .tags(flattenTags(tags))
                .description("Agent LLM call latency")
                .register(registry)
        ).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordTokens(String agentName, String role,
                             String type, int count,
                             String namespace, String[] tags) {
        String key = namespace + ".agent.tokens." + agentName + "." + type;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(namespace + ".agent.tokens.total")
                .tag("agent", agentName)
                .tag("role",  role)
                .tag("type",  type)
                .tags(flattenTags(tags))
                .description("Total tokens consumed by agent")
                .register(registry)
        ).increment(count);
    }

    @Override
    public void recordError(String agentName, String role,
                            String errorType, String namespace, String[] tags) {
        String key = namespace + ".agent.errors." + agentName + "." + errorType;
        counterCache.computeIfAbsent(key, k ->
            Counter.builder(namespace + ".agent.errors.total")
                .tag("agent", agentName)
                .tag("role",  role)
                .tag("error", errorType)
                .tags(flattenTags(tags))
                .description("Total agent errors")
                .register(registry)
        ).increment();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Convert "key=value,key2=value2" tag strings to Micrometer tag array.
     * Silently skips malformed entries.
     */
    private String[] flattenTags(String[] tags) {
        if (tags == null || tags.length == 0) return new String[0];
        java.util.List<String> flat = new java.util.ArrayList<>(tags.length * 2);
        for (String tag : tags) {
            int eq = tag.indexOf('=');
            if (eq > 0 && eq < tag.length() - 1) {
                flat.add(tag.substring(0, eq).trim());
                flat.add(tag.substring(eq + 1).trim());
            }
        }
        return flat.toArray(new String[0]);
    }
}
