package io.squados.trace;

import java.util.List;

/**
 * Pluggable exporter for agent spans.
 *
 * Built-in implementations:
 *   LogTraceExporter     — prints spans to stdout (default)
 *   InMemoryTraceExporter — stores spans in memory (testing)
 *
 * Pluggable implementations (wire in your app module):
 *   JaegerTraceExporter  — sends to Jaeger via OTLP/HTTP
 *   DatadogTraceExporter — sends to Datadog APM
 *   GrafanaTraceExporter — sends to Grafana Tempo
 */
public interface TraceExporter {
    /** Export a single span. Called immediately after each agent call. */
    void export(AgentSpan span);

    /** Export a batch of spans. Default: calls export() for each. */
    default void exportBatch(List<AgentSpan> spans) {
        spans.forEach(this::export);
    }

    /** Flush any buffered spans. Called on graceful shutdown. */
    default void flush() {}

    /** Exporter name for logging. */
    String name();
}