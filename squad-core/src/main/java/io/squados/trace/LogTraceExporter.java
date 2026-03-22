package io.squados.trace;

/**
 * Default TraceExporter — prints spans to stdout.
 * Zero configuration. Swap for JaegerTraceExporter in production.
 */
public class LogTraceExporter implements TraceExporter {

    @Override
    public void export(AgentSpan span) {
        System.out.printf("[Trace] %s | %s[%s] | %dms | %s | tokens=%d (p=%d c=%d) | in=%d out=%d%s%n",
            span.getSpanName(),
            span.getAgentName(),
            span.getAgentRole(),
            span.getDurationMs(),
            span.getStatus(),
            span.getTotalTokens(),
            span.getPromptTokens(),
            span.getCompletionTokens(),
            span.getInputLength(),
            span.getOutputLength(),
            span.isError() ? " ERROR: " + span.getErrorMessage() : "");
    }

    @Override public String name() { return "log"; }
}