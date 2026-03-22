package io.squados.trace;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory TraceExporter for testing.
 * Stores all spans in a list — assert against them in tests.
 *
 * Usage:
 * <pre>
 * InMemoryTraceExporter exporter = new InMemoryTraceExporter();
 * SquadTracer.configure(exporter);
 *
 * // After agent calls:
 * List<AgentSpan> spans = exporter.getSpans();
 * assertEquals(1, spans.size());
 * assertEquals("loan-underwriting", spans.get(0).getSpanName());
 * assertTrue(spans.get(0).getDurationMs() > 0);
 * </pre>
 */
public class InMemoryTraceExporter implements TraceExporter {

    private final List<AgentSpan> spans = new CopyOnWriteArrayList<>();

    @Override
    public void export(AgentSpan span) { spans.add(span); }

    @Override public String name() { return "in-memory"; }

    public List<AgentSpan> getSpans()          { return Collections.unmodifiableList(spans); }
    public int             size()              { return spans.size(); }
    public AgentSpan       last()              { return spans.isEmpty() ? null : spans.get(spans.size()-1); }
    public void            clear()             { spans.clear(); }
    public boolean         isEmpty()           { return spans.isEmpty(); }

    public List<AgentSpan> getByName(String name) {
        return spans.stream()
            .filter(s -> name.equals(s.getSpanName()))
            .collect(Collectors.toList());
    }

    public List<AgentSpan> getErrors() {
        return spans.stream().filter(AgentSpan::isError).collect(Collectors.toList());
    }

    public long totalTokens() {
        return spans.stream().mapToLong(AgentSpan::getTotalTokens).sum();
    }

    public double avgDurationMs() {
        return spans.stream().mapToLong(AgentSpan::getDurationMs).average().orElse(0.0);
    }
}