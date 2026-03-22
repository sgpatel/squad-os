package io.squados.tests;

import io.squados.annotation.*;
import io.squados.trace.*;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Phase 17 — v2.8 @Traced OpenTelemetry observability
 *
 * T01 — AgentSpan.builder builds span with all fields
 * T02 — AgentSpan.totalTokens = promptTokens + completionTokens
 * T03 — AgentSpan.isSuccess() true for OK status
 * T04 — AgentSpan.isError() true for ERROR status
 * T05 — InMemoryTraceExporter stores exported spans
 * T06 — InMemoryTraceExporter.last() returns most recent span
 * T07 — InMemoryTraceExporter.getByName() filters by spanName
 * T08 — InMemoryTraceExporter.getErrors() returns only error spans
 * T09 — InMemoryTraceExporter.totalTokens() sums all spans
 * T10 — SquadTracer.record captures duration &gt; 0
 * T11 — SquadTracer.record status OK on success
 * T12 — SquadTracer.record status ERROR on exception
 * T13 — SquadTracer.record captures input and output length
 * T14 — SquadTracer.recordMethod uses @Traced spanName
 * T15 — SquadTracer.recordMethod defaults to class.method spanName
 * T16 — SquadTracer.newTrace changes traceId
 * T17 — InMemoryTraceExporter.avgDurationMs works correctly
 */
public class SquadOsPhase17Tests {

    static class AnalysisAgent {
        @Traced(spanName = "loan-analysis", trackTokens = true)
        public String analyse(String input) { return "result"; }

        @Traced(spanName = "strict-analysis", traceOnError = true)
        public String failingMethod() { throw new RuntimeException("boom"); }

        public String noAnnotation() { return "plain"; }
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase17Tests();
        String[] tests = {
            "T01_spanBuilderSetsAllFields",
            "T02_totalTokensSum",
            "T03_isSuccessForOK",
            "T04_isErrorForERROR",
            "T05_inMemoryExporterStoresSpans",
            "T06_inMemoryExporterLast",
            "T07_inMemoryExporterGetByName",
            "T08_inMemoryExporterGetErrors",
            "T09_inMemoryExporterTotalTokens",
            "T10_tracerRecordCapturesDuration",
            "T11_tracerRecordStatusOK",
            "T12_tracerRecordStatusError",
            "T13_tracerRecordInputOutputLength",
            "T14_tracerRecordMethodUsesAnnotationSpanName",
            "T15_tracerRecordMethodDefaultsToClassMethod",
            "T16_newTraceChangesTraceId",
            "T17_avgDurationMs",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 17 — v2.8 @Traced            ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 17 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 17 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.8 @Traced observability operational.\n"); }
    }

    void T01_spanBuilderSetsAllFields() {
        AgentSpan span = AgentSpan.builder("test-span")
            .agentRole(AgentRole.ANALYST)
            .agentName("RiskBot")
            .durationMs(150)
            .promptTokens(100)
            .completionTokens(50)
            .inputLength(200)
            .outputLength(300)
            .build();
        assertEquals("test-span",        span.getSpanName(),    "span name");
        assertEquals(AgentRole.ANALYST,  span.getAgentRole(),   "agent role");
        assertEquals("RiskBot",          span.getAgentName(),   "agent name");
        assertEquals(150L,               span.getDurationMs(),  "duration");
        assertEquals(200,                span.getInputLength(), "input length");
        assertEquals(300,                span.getOutputLength(),"output length");
    }

    void T02_totalTokensSum() {
        AgentSpan span = AgentSpan.builder("s")
            .promptTokens(100).completionTokens(50).build();
        assertEquals(150, span.getTotalTokens(), "total = 100 + 50");
    }

    void T03_isSuccessForOK() {
        AgentSpan span = AgentSpan.builder("s")
            .status(AgentSpan.Status.OK).build();
        assertTrue(span.isSuccess(), "OK -> isSuccess");
        assertFalse(span.isError(), "OK -> not isError");
    }

    void T04_isErrorForERROR() {
        AgentSpan span = AgentSpan.builder("s")
            .status(AgentSpan.Status.ERROR)
            .errorMessage("something went wrong").build();
        assertTrue(span.isError(), "ERROR -> isError");
        assertEquals("something went wrong", span.getErrorMessage(), "error message");
    }

    void T05_inMemoryExporterStoresSpans() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        exp.export(AgentSpan.builder("s1").build());
        exp.export(AgentSpan.builder("s2").build());
        assertEquals(2, exp.size(), "2 spans stored");
    }

    void T06_inMemoryExporterLast() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        exp.export(AgentSpan.builder("first").build());
        exp.export(AgentSpan.builder("last").build());
        assertEquals("last", exp.last().getSpanName(), "last span returned");
    }

    void T07_inMemoryExporterGetByName() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        exp.export(AgentSpan.builder("fraud-check").build());
        exp.export(AgentSpan.builder("loan-check").build());
        exp.export(AgentSpan.builder("fraud-check").build());
        List<AgentSpan> fraud = exp.getByName("fraud-check");
        assertEquals(2, fraud.size(), "2 fraud-check spans");
    }

    void T08_inMemoryExporterGetErrors() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        exp.export(AgentSpan.builder("ok").status(AgentSpan.Status.OK).build());
        exp.export(AgentSpan.builder("err").status(AgentSpan.Status.ERROR).build());
        exp.export(AgentSpan.builder("ok2").status(AgentSpan.Status.OK).build());
        assertEquals(1, exp.getErrors().size(), "1 error span");
        assertEquals("err", exp.getErrors().get(0).getSpanName(), "error span name");
    }

    void T09_inMemoryExporterTotalTokens() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        exp.export(AgentSpan.builder("s1").promptTokens(100).completionTokens(50).build());
        exp.export(AgentSpan.builder("s2").promptTokens(200).completionTokens(100).build());
        assertEquals(450L, exp.totalTokens(), "total tokens = 150 + 300 = 450");
    }

    void T10_tracerRecordCapturesDuration() throws Exception {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        SquadTracer.record("test", AgentRole.ANALYST, "bot", "input", () -> {
            try { Thread.sleep(10); } catch (InterruptedException e) {}
            return "output";
        });
        assertTrue(exp.last().getDurationMs() >= 10, "duration >= 10ms");
    }

    void T11_tracerRecordStatusOK() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        SquadTracer.record("test", AgentRole.ANALYST, "bot", "in", () -> "out");
        assertTrue(exp.last().isSuccess(), "status OK on success");
    }

    void T12_tracerRecordStatusError() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        try {
            SquadTracer.record("test", AgentRole.ANALYST, "bot", "in", () -> {
                throw new RuntimeException("fail");
            });
        } catch (RuntimeException ignored) {}
        assertTrue(exp.last().isError(), "status ERROR on exception");
        assertTrue(exp.last().getErrorMessage().contains("fail"), "error message captured");
    }

    void T13_tracerRecordInputOutputLength() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        String input = "hello world";
        String output = "goodbye world!";
        SquadTracer.record("test", AgentRole.ANALYST, "bot", input, () -> output);
        assertEquals(input.length(),  exp.last().getInputLength(),  "input length");
        assertEquals(output.length(), exp.last().getOutputLength(), "output length");
    }

    void T14_tracerRecordMethodUsesAnnotationSpanName() throws Exception {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        Method m = AnalysisAgent.class.getDeclaredMethod("analyse", String.class);
        SquadTracer.recordMethod(m, AgentRole.ANALYST, "bot", "input", () -> "output");
        assertEquals("loan-analysis", exp.last().getSpanName(),
            "@Traced spanName used");
    }

    void T15_tracerRecordMethodDefaultsToClassMethod() throws Exception {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        SquadTracer.configure(exp);
        Method m = AnalysisAgent.class.getDeclaredMethod("noAnnotation");
        SquadTracer.recordMethod(m, AgentRole.ANALYST, "bot", "input", () -> "output");
        assertTrue(exp.last().getSpanName().contains("AnalysisAgent"),
            "class name in default span name");
        assertTrue(exp.last().getSpanName().contains("noAnnotation"),
            "method name in default span name");
    }

    void T16_newTraceChangesTraceId() {
        String id1 = SquadTracer.getTraceId();
        SquadTracer.newTrace();
        String id2 = SquadTracer.getTraceId();
        assertFalse(id1.equals(id2), "new trace generates new traceId");
    }

    void T17_avgDurationMs() {
        InMemoryTraceExporter exp = new InMemoryTraceExporter();
        exp.export(AgentSpan.builder("s1").durationMs(100).build());
        exp.export(AgentSpan.builder("s2").durationMs(200).build());
        exp.export(AgentSpan.builder("s3").durationMs(300).build());
        assertEquals(200.0, exp.avgDurationMs(), "avg = (100+200+300)/3 = 200");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}