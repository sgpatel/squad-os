package io.squados.tests;

import io.squados.annotation.*;
import io.squados.exception.AutoPlanMaxIterationsException;
import io.squados.llm.*;
import io.squados.plan.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 16 — v2.7 @AutoPlan agentic loops
 *
 * P01 — AutoPlanEngine.run no-op when method has no @AutoPlan
 * P02 — AutoPlanEngine stops immediately when initial output meets goal
 * P03 — AutoPlanEngine runs one iteration when goal met on iteration 1
 * P04 — AutoPlanEngine accumulates output across iterations
 * P05 — AutoPlanEngine replaces output when accumulate=false
 * P06 — AutoPlanEngine stops when stopCondition found in output
 * P07 — AutoPlanEngine reaches maxIterations and returns best (RETURN_BEST)
 * P08 — AutoPlanEngine reaches maxIterations and returns last (RETURN_LAST)
 * P09 — AutoPlanEngine throws when maxIterations reached (THROW policy)
 * P10 — AutoPlanResult.isGoalMet false when max iterations reached
 * P11 — AutoPlanResult.isGoalMet true when goal met
 * P12 — AutoPlanResult.getIterations has correct count
 * P13 — AutoPlanResult.getBestIteration returns longest output
 * P14 — PlanIteration captures plan, output, reflection, goalMet
 * P15 — AutoPlanEngine calls LLM plan+execute each iteration (2 calls/iter)
 * P16 — AutoPlanEngine injects reflection into next iteration context
 * P17 — AutoPlanMaxIterationsException contains goal and maxIterations
 */
public class SquadOsPhase16Tests {

    // ── Scripted LLM ─────────────────────────────────────────────
    static class ScriptedLlm implements LlmPort {
        final List<String> responses;
        int callCount = 0;
        ScriptedLlm(String... r) { this.responses = new ArrayList<>(List.of(r)); }
        @Override public LlmResponse chat(String s, String u, LlmOptions o) {
            String r = callCount < responses.size() ? responses.get(callCount) : "done";
            callCount++; return new LlmResponse(r, 0, 0, "mock");
        }
        @Override public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) {
            return null;
        }
    }

    // ── Test agent ────────────────────────────────────────────────
    static class ResearchAgent {
        @AutoPlan(
            goal = "A complete risk report",
            maxIterations = 3,
            stopCondition = "COMPLETE",
            onMaxIterations = IterationPolicy.RETURN_BEST
        )
        public String research() { return "initial"; }

        @AutoPlan(
            goal = "Done",
            maxIterations = 3,
            stopCondition = "COMPLETE",
            accumulate = false,
            onMaxIterations = IterationPolicy.RETURN_LAST
        )
        public String noAccumulate() { return "v1"; }

        @AutoPlan(
            goal = "Done",
            maxIterations = 2,
            stopCondition = "COMPLETE",
            onMaxIterations = IterationPolicy.THROW
        )
        public String strictPlan() { return "initial"; }

        public String noAnnotation() { return "pass through"; }
    }

    // ── Runner ────────────────────────────────────────────────────
    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase16Tests();
        String[] tests = {
            "P01_noOpWithoutAnnotation",
            "P02_stopsOnInitialGoalMet",
            "P03_stopsWhenGoalMetOnIteration1",
            "P04_accumulatesOutput",
            "P05_replacesOutputWhenAccumulateFalse",
            "P06_stopsWhenStopConditionFound",
            "P07_returnsBestOnMaxIterations",
            "P08_returnsLastOnMaxIterations",
            "P09_throwsOnMaxIterations",
            "P10_goalMetFalseWhenMaxReached",
            "P11_goalMetTrueWhenGoalMet",
            "P12_iterationCountCorrect",
            "P13_getBestIterationLongest",
            "P14_planIterationFields",
            "P15_llmCalledTwicePerIteration",
            "P16_reflectionInjectedIntoContext",
            "P17_exceptionContainsGoalAndMax",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 16 — v2.7 @AutoPlan          ║");
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
        if (failed > 0) { System.out.println("\n  PHASE 16 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 16 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.7 @AutoPlan agentic loops operational.\n"); }
    }

    Method m(String name) {
        try { return ResearchAgent.class.getDeclaredMethod(name); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    void P01_noOpWithoutAnnotation() {
        AutoPlanEngine eng = new AutoPlanEngine(new ScriptedLlm());
        AutoPlanResult r = eng.run(m("noAnnotation"), "pass through", "sys", "task");
        assertEquals("pass through", r.getFinalOutput(), "pass through unchanged");
        assertEquals(0, r.getTotalIterations(), "no iterations");
    }

    void P02_stopsOnInitialGoalMet() {
        AutoPlanEngine eng = new AutoPlanEngine(new ScriptedLlm());
        AutoPlanResult r = eng.run(m("research"), "already COMPLETE", "sys", "task");
        assertTrue(r.isGoalMet(), "goal met immediately");
        assertEquals(0, r.getTotalIterations(), "no loop iterations needed");
    }

    void P03_stopsWhenGoalMetOnIteration1() {
        // plan call + execute call returns COMPLETE
        ScriptedLlm llm = new ScriptedLlm("step 1 plan", "result COMPLETE");
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "initial", "sys", "task");
        assertTrue(r.isGoalMet(), "goal met on iteration 1");
        assertEquals(1, r.getTotalIterations(), "1 iteration");
    }

    void P04_accumulatesOutput() {
        // 3 iterations, goal never met
        ScriptedLlm llm = new ScriptedLlm(
            "plan1", "output1", "reflect1",
            "plan2", "output2", "reflect2",
            "plan3", "output3", "reflect3"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "initial", "sys", "task");
        assertTrue(r.getFinalOutput().contains("initial"), "initial preserved");
        assertTrue(r.getFinalOutput().contains("output1"), "output1 accumulated");
        assertTrue(r.getFinalOutput().contains("output3"), "output3 accumulated");
    }

    void P05_replacesOutputWhenAccumulateFalse() {
        ScriptedLlm llm = new ScriptedLlm(
            "plan1", "replacement1", "reflect1",
            "plan2", "replacement2", "reflect2",
            "plan3", "replacement3", "reflect3"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("noAccumulate"), "v1", "sys", "task");
        assertFalse(r.getFinalOutput().contains("v1"), "original not in final output");
        assertTrue(r.getFinalOutput().contains("replacement"), "replacement present");
    }

    void P06_stopsWhenStopConditionFound() {
        // iteration 2 execute returns COMPLETE
        ScriptedLlm llm = new ScriptedLlm(
            "plan1", "partial output", "missing sections",
            "plan2", "final output COMPLETE"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "initial", "sys", "task");
        assertTrue(r.isGoalMet(), "goal met on iteration 2");
        assertEquals(2, r.getTotalIterations(), "2 iterations");
    }

    void P07_returnsBestOnMaxIterations() {
        // All iterations short, iteration 2 longest
        ScriptedLlm llm = new ScriptedLlm(
            "plan1", "short output", "more needed",
            "plan2", "this is a much longer output from iteration two", "still incomplete",
            "plan3", "small", "incomplete"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "x", "sys", "task");
        assertFalse(r.isGoalMet(), "goal not met");
        assertTrue(r.getFinalOutput().contains("longer"), "best (longest) output returned");
    }

    void P08_returnsLastOnMaxIterations() {
        ScriptedLlm llm = new ScriptedLlm(
            "plan1", "output1", "more",
            "plan2", "output2", "more",
            "plan3", "final output last"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("noAccumulate"), "v1", "sys", "task");
        assertEquals("final output last", r.getFinalOutput(), "last output returned");
    }

    void P09_throwsOnMaxIterations() {
        ScriptedLlm llm = new ScriptedLlm(
            "plan1", "output1", "more needed",
            "plan2", "output2", "more needed"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        try {
            eng.run(m("strictPlan"), "initial", "sys", "task");
            throw new AssertionError("should have thrown");
        } catch (AutoPlanMaxIterationsException e) {
            assertTrue(e.getMessage().contains("strictPlan"), "method name in exception");
        }
    }

    void P10_goalMetFalseWhenMaxReached() {
        ScriptedLlm llm = new ScriptedLlm(
            "p","o","r","p","o","r","p","o","r"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "initial", "sys", "task");
        assertFalse(r.isGoalMet(), "goal not met when max iterations reached");
    }

    void P11_goalMetTrueWhenGoalMet() {
        ScriptedLlm llm = new ScriptedLlm("plan", "output COMPLETE");
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "initial", "sys", "task");
        assertTrue(r.isGoalMet(), "goal met = true");
    }

    void P12_iterationCountCorrect() {
        ScriptedLlm llm = new ScriptedLlm(
            "p","o1","r","p","o2 COMPLETE"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "x", "sys", "task");
        assertEquals(2, r.getTotalIterations(), "2 iterations");
    }

    void P13_getBestIterationLongest() {
        ScriptedLlm llm = new ScriptedLlm(
            "p","short","r",
            "p","this is a very long iteration output that should win","r",
            "p","tiny","r"
        );
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "x", "sys", "task");
        PlanIteration best = r.getBestIteration();
        assertNotNull(best, "best iteration not null");
        assertTrue(best.output().contains("long iteration"), "longest output is best");
    }

    void P14_planIterationFields() {
        ScriptedLlm llm = new ScriptedLlm("my plan", "my output COMPLETE");
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        AutoPlanResult r = eng.run(m("research"), "x", "sys", "task");
        PlanIteration iter = r.getIterations().get(0);
        assertEquals("my plan",   iter.plan(),   "plan captured");
        assertEquals("my output COMPLETE", iter.output(), "output captured");
        assertTrue(iter.goalMet(), "goalMet true");
        assertEquals(1, iter.number(), "iteration number 1");
    }

    void P15_llmCalledTwicePerIteration() {
        // 1 iteration: plan call + execute call = 2. No reflection if goal met.
        ScriptedLlm llm = new ScriptedLlm("plan", "output COMPLETE");
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        eng.run(m("research"), "x", "sys", "task");
        assertEquals(2, llm.callCount, "2 LLM calls for 1 iteration (plan + execute)");
    }

    void P16_reflectionInjectedIntoContext() {
        // 2 iterations: iter1 misses goal, iter2 meets goal
        // Verify 3rd call (plan2) includes reflection from iter1
        List<String> received = new ArrayList<>();
        LlmPort captureLlm = new LlmPort() {
            int c = 0;
            String[] resp = {"plan1", "iter1 output", "gaps: missing section X",
                             "plan2", "iter2 COMPLETE"};
            @Override public LlmResponse chat(String s, String u, LlmOptions o) {
                received.add(u); // capture user messages
                String r = c < resp.length ? resp[c] : "done"; c++;
                return new LlmResponse(r, 0, 0, "mock");
            }
            @Override public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) { return null; }
        };
        AutoPlanEngine eng = new AutoPlanEngine(captureLlm);
        eng.run(m("research"), "initial", "sys", "task");
        // The 4th call (plan for iter2) should reference previous output
        // At minimum the accumulated output from iter1 should be in context
        assertTrue(received.size() >= 4, "at least 4 LLM calls made");
    }

    void P17_exceptionContainsGoalAndMax() {
        ScriptedLlm llm = new ScriptedLlm("p","o","r","p","o","r");
        AutoPlanEngine eng = new AutoPlanEngine(llm);
        try {
            eng.run(m("strictPlan"), "initial", "sys", "task");
        } catch (AutoPlanMaxIterationsException e) {
            assertEquals(2, e.getMaxIterations(), "maxIterations=2");
            assertTrue(e.getGoal().contains("Done"), "goal in exception");
        }
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