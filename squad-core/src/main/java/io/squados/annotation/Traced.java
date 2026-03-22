package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Records an OpenTelemetry-compatible span for every agent method call.
 *
 * Captures:
 *   - Wall-clock latency (start, end, duration)
 *   - Token usage (prompt tokens, completion tokens, total)
 *   - Agent role and name
 *   - Input length and output length
 *   - Success/failure status
 *   - Error message on exception
 *
 * Spans are exported to the configured TraceExporter:
 *   - LogTraceExporter    (stdout — default, zero config)
 *   - JaegerTraceExporter (Jaeger — production)
 *   - InMemoryTraceExporter (testing)
 *
 * Usage:
 * <pre>
 * {@literal @}Traced(spanName = "loan-underwriting", trackTokens = true)
 * public LoanDecision underwriteLoan(LoanApplication app) { ... }
 *
 * // Class-level: traces ALL methods in the agent
 * {@literal @}Agent(role = AgentRole.ANALYST)
 * {@literal @}Traced(spanName = "fraud-detection")
 * public class FraudAgent { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Traced {
    /** Span name shown in tracing UI. Defaults to class.method. */
    String spanName() default "";

    /** Whether to record token counts (prompt + completion). */
    boolean trackTokens() default true;

    /** Whether to record input/output lengths. */
    boolean trackIO() default true;

    /** Minimum duration in ms to record. Faster calls are skipped. */
    long minDurationMs() default 0L;

    /** Whether to record on exception (failed calls). */
    boolean traceOnError() default true;
}