package io.squados.metrics;

/**
 * SPI for emitting agent call metrics.
 *
 * squad-core defines the interface; adapters in squad-spring-boot-starter
 * bridge to Micrometer/Prometheus or any other metrics system.
 *
 * Metrics emitted per @Observe-annotated agent call:
 *   {ns}_agent_calls_total{agent, role, status}    — counter
 *   {ns}_agent_latency_ms{agent, role}             — histogram (via recordCall)
 *   {ns}_agent_tokens_total{agent, role, type}     — counter (prompt/completion)
 *   {ns}_agent_errors_total{agent, role, error}    — counter
 */
public interface MetricsPort {

    /**
     * Record one completed agent call.
     *
     * @param agentName  Agent name from @Agent.name()
     * @param role       Agent role (ANALYST, PLANNER, …)
     * @param status     "success" or "error"
     * @param latencyMs  Wall-clock duration of the call in milliseconds
     * @param namespace  Metric prefix from @Observe.namespace()
     * @param tags       Additional static tags from @Observe.tags()
     */
    void recordCall(String agentName, String role, String status,
                    long latencyMs, String namespace, String[] tags);

    /**
     * Record token consumption for this call.
     *
     * @param type  "prompt" or "completion"
     * @param count Number of tokens
     */
    void recordTokens(String agentName, String role,
                      String type, int count,
                      String namespace, String[] tags);

    /**
     * Record an agent error.
     *
     * @param errorType Short error category (e.g. "timeout", "guardrail", "llm")
     */
    void recordError(String agentName, String role,
                     String errorType, String namespace, String[] tags);
}
