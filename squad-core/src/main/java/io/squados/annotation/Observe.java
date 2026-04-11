package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Emit Micrometer/Prometheus metrics for this agent.
 *
 * Metrics emitted per agent call:
 *   squados_agent_calls_total{agent, role, status}     — counter
 *   squados_agent_latency_seconds{agent, role}         — histogram
 *   squados_agent_tokens_total{agent, role, type}      — counter (prompt/completion)
 *   squados_agent_errors_total{agent, role, error}     — counter
 *
 * namespace — Metric name prefix (default: "squados")
 * tags      — Additional static tags as "key=value" pairs
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Observe {
    String   namespace() default "squados";
    String[] tags()      default {};
}
