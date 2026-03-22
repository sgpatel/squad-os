package io.squados.trace;

import io.squados.annotation.AgentRole;
import io.squados.annotation.Traced;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Central tracing entry point for SquadOS.
 *
 * Usage:
 * <pre>
 * // Configure once at startup
 * SquadTracer.configure(new InMemoryTraceExporter()); // testing
 * SquadTracer.configure(new LogTraceExporter());       // dev
 *
 * // Record a span manually
 * AgentSpan span = SquadTracer.record(
 *     "loan-check", AgentRole.ANALYST, "RiskBot",
 *     () -> agent.analyse(application)
 * );
 *
 * // Record for a @Traced annotated method
 * SquadTracer.recordMethod(method, agentRole, agentName, input, () -> output);
 * </pre>
 */
public class SquadTracer {

    private static volatile TraceExporter exporter = new LogTraceExporter();
    private static volatile String        currentTraceId = newId();

    /** Configure the exporter. Call once at startup. */
    public static void configure(TraceExporter e) {
        exporter = e;
        System.out.printf("[SquadTracer] Configured exporter: %s%n", e.name());
    }

    public static TraceExporter getExporter() { return exporter; }

    /** Start a new trace (new request/session). */
    public static void newTrace() { currentTraceId = newId(); }

    public static String getTraceId() { return currentTraceId; }

    /**
     * Record a span for a @Traced annotated method.
     * Reads annotation config (spanName, trackTokens, minDurationMs).
     */
    public static <T> T recordMethod(Method method,
                                      AgentRole role,
                                      String agentName,
                                      String input,
                                      Supplier<T> action) {
        Traced ann = method.getAnnotation(Traced.class);
        if (ann == null) {
            // Check class-level @Traced
            ann = method.getDeclaringClass().getAnnotation(Traced.class);
        }
        String spanName = (ann != null && !ann.spanName().isEmpty())
            ? ann.spanName()
            : method.getDeclaringClass().getSimpleName() + "." + method.getName();

        final Traced finalAnn = ann;
        return record(spanName, role, agentName, input, action, finalAnn);
    }

    /**
     * Record a span for any code block.
     */
    public static <T> T record(String spanName,
                                AgentRole role,
                                String agentName,
                                String input,
                                Supplier<T> action) {
        return record(spanName, role, agentName, input, action, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T record(String spanName,
                                 AgentRole role,
                                 String agentName,
                                 String input,
                                 Supplier<T> action,
                                 Traced ann) {
        Instant start = Instant.now();
        T result = null;
        String error = null;
        AgentSpan.Status status = AgentSpan.Status.OK;

        try {
            result = action.get();
            return result;
        } catch (Exception e) {
            error  = e.getMessage();
            status = AgentSpan.Status.ERROR;
            throw e;
        } finally {
            Instant end    = Instant.now();
            long duration  = end.toEpochMilli() - start.toEpochMilli();

            // Skip if below minDurationMs or error tracing disabled
            boolean skip = (ann != null && duration < ann.minDurationMs())
                        || (ann != null && status == AgentSpan.Status.ERROR && !ann.traceOnError());
            if (!skip) {
            String output = result == null ? "" : result.toString();

            AgentSpan span = AgentSpan.builder(spanName)
                .traceId(currentTraceId)
                .agentRole(role != null ? role : AgentRole.WILDCARD)
                .agentName(agentName != null ? agentName : "unknown")
                .startTime(start)
                .endTime(end)
                .durationMs(duration)
                .status(status)
                .errorMessage(error)
                .inputLength(input != null ? input.length() : 0)
                .outputLength(output.length())
                // Token counts extracted from LlmResponse if available
                // Set via attribute for now; full token tracking in AgentWrapper
                .build();

            exporter.export(span);
            } // end if (!skip)
        }
    }

    private static String newId() {
        return java.util.UUID.randomUUID().toString().replace("-","").substring(0,16);
    }
}