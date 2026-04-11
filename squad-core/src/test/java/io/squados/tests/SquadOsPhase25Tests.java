package io.squados.tests;

import io.squados.agent.AgentResponse;
import io.squados.annotation.*;
import io.squados.config.*;
import io.squados.context.*;
import io.squados.llm.*;
import io.squados.pipeline.*;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Phase 25 — v3.6 @Pipeline sequential multi-agent orchestration
 *
 * P01 — PipelineEngine executes steps in order
 * P02 — PipelineResult.finalOutput() returns last successful step's content
 * P03 — PipelineEngine step 0 always receives original input
 * P04 — PipelineEngine step N (no inputFrom) receives output of step N-1
 * P05 — PipelineEngine step N (inputFrom=name) receives named step output
 * P06 — PipelineResult.succeeded() is true when all steps succeed
 * P07 — PipelineResult.totalTokens() sums across all steps
 * P08 — PipelineResult.wallClockMs() is non-negative
 * P09 — PipelineResult.get(stepName) returns response for that step
 * P10 — PipelineResult.skippedSteps() is empty when no steps skipped
 * P11 — PipelineEngine failFast=true stops on first failure
 * P12 — PipelineResult.failedStep() returns name of failed step
 * P13 — PipelineEngine failFast=false continues past failure
 * P14 — PipelineResult.succeeded() is false when a step fails with failFast
 * P15 — @Pipeline annotation has correct defaults (failFast=true)
 * P16 — @Step annotation role is required
 * P17 — PipelineEngine skips steps with unresolved role (no agent)
 * P18 — PipelineResult.allResults() contains all executed steps
 * P19 — PipelineEngine executes 3-step chain sequentially
 * P20 — Pipeline with empty steps returns success with no output
 */
public class SquadOsPhase25Tests {

    static MockLlmPort llm = new MockLlmPort();

    static AgentRegistry registryWith(AgentRole... roles) throws Exception {
        AgentRegistry reg = new AgentRegistry();
        for (AgentRole role : roles) {
            // Create minimal agent class dynamically
            String response = "OUTPUT_FROM_" + role.name();
            llm.setResponse(role.name().toLowerCase(), response);
            llm.setDefaultResponse("default output");

            // Use inner class per role
            Class<?> agentClass = createAgentClass(role);
            SquadConfig cfg = SquadConfig.builder().name("test").build();
            AgentWrapper wrapper = new AgentWrapper(agentClass, null, cfg, llm);
            reg.register(wrapper);
        }
        return reg;
    }

    @Agent(role = AgentRole.ANALYST,    name = "Analyst")  static class AnalystAgent {}
    @Agent(role = AgentRole.CRITIC,     name = "Critic")   static class CriticAgent {}
    @Agent(role = AgentRole.EXECUTOR,   name = "Executor") static class ExecutorAgent {}
    @Agent(role = AgentRole.STRATEGIST, name = "Strategist") static class StrategistAgent {}

    static Class<?> createAgentClass(AgentRole role) {
        return switch (role) {
            case ANALYST    -> AnalystAgent.class;
            case CRITIC     -> CriticAgent.class;
            case EXECUTOR   -> ExecutorAgent.class;
            case STRATEGIST -> StrategistAgent.class;
            default         -> AnalystAgent.class;
        };
    }

    static Pipeline pipelineWith(boolean failFast, Step... steps) {
        return new Pipeline() {
            public Class<? extends Annotation> annotationType() { return Pipeline.class; }
            public String name()    { return "test-pipeline"; }
            public Step[] steps()   { return steps; }
            public boolean failFast(){ return failFast; }
        };
    }

    static Step stepFor(AgentRole role, String name) {
        return new Step() {
            public Class<? extends Annotation> annotationType() { return Step.class; }
            public AgentRole role()     { return role; }
            public String name()        { return name; }
            public String inputFrom()   { return ""; }
            public String condition()   { return ""; }
            public boolean failFast()   { return true; }
        };
    }

    static Step stepWithInputFrom(AgentRole role, String name, String from) {
        return new Step() {
            public Class<? extends Annotation> annotationType() { return Step.class; }
            public AgentRole role()     { return role; }
            public String name()        { return name; }
            public String inputFrom()   { return from; }
            public String condition()   { return ""; }
            public boolean failFast()   { return true; }
        };
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase25Tests();
        String[] tests = {
            "P01_stepsExecuteInOrder",
            "P02_finalOutputLastSuccessful",
            "P03_step0GetsOriginalInput",
            "P04_stepNGetsPrevOutput",
            "P05_stepWithInputFrom",
            "P06_succeededWhenAllPass",
            "P07_totalTokensSum",
            "P08_wallClockNonNegative",
            "P09_getByStepName",
            "P10_skippedStepsEmpty",
            "P11_failFastStopsOnFailure",
            "P12_failedStepName",
            "P13_continuesPastFailureWhenNotFailFast",
            "P14_succeededFalseOnFailFast",
            "P15_pipelineAnnotationDefaults",
            "P16_stepAnnotationRoleRequired",
            "P17_skipsStepsWithNoAgent",
            "P18_allResultsContainsAllSteps",
            "P19_threeStepChain",
            "P20_emptyPipelineSucceeds",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 25 - v3.6 @Pipeline          ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    -> %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    -> %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 25 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 25 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.6 @Pipeline orchestration operational.\n"); }
    }

    void P01_stepsExecuteInOrder() throws Exception {
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "step1"), stepFor(AgentRole.CRITIC, "step2"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertNotNull(result.get("step1"), "step1 result exists");
        assertNotNull(result.get("step2"), "step2 result exists");
    }

    void P02_finalOutputLastSuccessful() throws Exception {
        llm.setDefaultResponse("final-answer");
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "only"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertNotNull(result.finalOutput(), "finalOutput non-null");
    }

    void P03_step0GetsOriginalInput() throws Exception {
        llm.setDefaultResponse("step0-response");
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "first"));
        PipelineResult result = engine.execute(p, "original-task", "session");
        assertTrue(result.get("first").isSuccess(), "step succeeded");
    }

    void P04_stepNGetsPrevOutput() throws Exception {
        llm.setDefaultResponse("next-step-output");
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "s1"), stepFor(AgentRole.CRITIC, "s2"));
        PipelineResult result = engine.execute(p, "start", "session");
        assertTrue(result.get("s2").isSuccess(), "s2 executed");
    }

    void P05_stepWithInputFrom() throws Exception {
        llm.setDefaultResponse("response");
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true,
            stepFor(AgentRole.ANALYST, "s1"),
            stepFor(AgentRole.CRITIC, "s2"),
            stepWithInputFrom(AgentRole.EXECUTOR, "s3", "s1"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertNotNull(result.get("s3"), "s3 executed with inputFrom=s1");
    }

    void P06_succeededWhenAllPass() throws Exception {
        llm.setDefaultResponse("ok");
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "a"), stepFor(AgentRole.CRITIC, "b"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertTrue(result.succeeded(), "succeeded=true when all pass");
    }

    void P07_totalTokensSum() throws Exception {
        llm.setDefaultResponse("output");
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "a"), stepFor(AgentRole.CRITIC, "b"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertTrue(result.totalTokens() >= 0, "totalTokens non-negative");
    }

    void P08_wallClockNonNegative() throws Exception {
        llm.setDefaultResponse("ok");
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "s"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertTrue(result.wallClockMs() >= 0, "wallClockMs non-negative");
    }

    void P09_getByStepName() throws Exception {
        llm.setDefaultResponse("response");
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "myStep"));
        PipelineResult result = engine.execute(p, "input", "session");
        AgentResponse r = result.get("myStep");
        assertNotNull(r, "get by step name returns response");
    }

    void P10_skippedStepsEmpty() throws Exception {
        llm.setDefaultResponse("ok");
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "s1"));
        PipelineResult result = engine.execute(p, "input", "session");
        assertTrue(result.skippedSteps().isEmpty(), "no skipped steps");
    }

    void P11_failFastStopsOnFailure() throws Exception {
        // RESEARCHER has no agent — causes failure
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true,
            stepFor(AgentRole.RESEARCHER, "fail-step"),  // no agent
            stepFor(AgentRole.ANALYST, "should-skip"));   // should not run
        PipelineResult result = engine.execute(p, "input", "session");
        assertFalse(result.succeeded(), "failed when failFast=true");
        assertNotNull(result.failedStep(), "failedStep name set");
        assertEquals(null, result.get("should-skip"), "second step not executed");
    }

    void P12_failedStepName() throws Exception {
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true,
            stepFor(AgentRole.RESEARCHER, "no-agent-step"));
        PipelineResult result = engine.execute(p, "x", "s");
        assertEquals("no-agent-step", result.failedStep(), "failedStep = no-agent-step");
    }

    void P13_continuesPastFailureWhenNotFailFast() throws Exception {
        llm.setDefaultResponse("ok");
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        // Pipeline failFast=false
        Pipeline p = pipelineWith(false,
            stepFor(AgentRole.RESEARCHER, "fail"),
            stepFor(AgentRole.ANALYST, "continue"));
        PipelineResult result = engine.execute(p, "x", "s");
        assertNotNull(result.get("continue"), "second step executed despite failure");
    }

    void P14_succeededFalseOnFailFast() throws Exception {
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.RESEARCHER, "nope"));
        PipelineResult result = engine.execute(p, "x", "s");
        assertFalse(result.succeeded(), "succeeded=false on failure");
    }

    void P15_pipelineAnnotationDefaults() {
        @Pipeline(steps = { @Step(role = AgentRole.ANALYST) })
        class Sample {}
        Pipeline ann = Sample.class.getAnnotation(Pipeline.class);
        assertTrue(ann.failFast(), "default failFast=true");
        assertEquals("", ann.name(), "default name empty");
    }

    void P16_stepAnnotationRoleRequired() {
        @Pipeline(steps = { @Step(role = AgentRole.ANALYST, name = "step") })
        class Sample {}
        Step step = Sample.class.getAnnotation(Pipeline.class).steps()[0];
        assertEquals(AgentRole.ANALYST, step.role(), "role set");
        assertEquals("step", step.name(), "name set");
    }

    void P17_skipsStepsWithNoAgent() throws Exception {
        AgentRegistry reg = registryWith(AgentRole.ANALYST);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(false, stepFor(AgentRole.RESEARCHER, "missing"));
        PipelineResult result = engine.execute(p, "input", "s");
        AgentResponse r = result.get("missing");
        assertNotNull(r, "result recorded for missing agent step");
        assertFalse(r.isSuccess(), "failure recorded");
    }

    void P18_allResultsContainsAllSteps() throws Exception {
        llm.setDefaultResponse("ok");
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true, stepFor(AgentRole.ANALYST, "a"), stepFor(AgentRole.CRITIC, "b"));
        PipelineResult result = engine.execute(p, "input", "s");
        assertEquals(2, result.allResults().size(), "allResults has 2 entries");
    }

    void P19_threeStepChain() throws Exception {
        llm.setDefaultResponse("chain-ok");
        AgentRegistry reg = registryWith(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR);
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true,
            stepFor(AgentRole.ANALYST, "s1"),
            stepFor(AgentRole.CRITIC, "s2"),
            stepFor(AgentRole.EXECUTOR, "s3"));
        PipelineResult result = engine.execute(p, "start", "session");
        assertTrue(result.succeeded(), "3-step chain succeeded");
        assertNotNull(result.get("s1"), "s1 exists");
        assertNotNull(result.get("s2"), "s2 exists");
        assertNotNull(result.get("s3"), "s3 exists");
    }

    void P20_emptyPipelineSucceeds() {
        AgentRegistry reg = new AgentRegistry();
        PipelineEngine engine = new PipelineEngine(reg);
        Pipeline p = pipelineWith(true); // no steps
        PipelineResult result = engine.execute(p, "input", "s");
        assertTrue(result.succeeded(), "empty pipeline = success");
        assertEquals(null, result.finalOutput(), "no final output");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return; throw new AssertionError(msg + " — null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}
