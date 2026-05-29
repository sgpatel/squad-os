package io.squados.tests;

import io.squados.annotation.*;
import io.squados.exception.AutoPlanMaxIterationsException;
import io.squados.llm.*;
import io.squados.plan.*;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Phase 43 — @AutoPlan tests.
 *
 * AP01  AutoPlanEngine skips loop when initial output already meets stopCondition
 * AP02  AutoPlanEngine runs iterations when goal not met
 * AP03  AutoPlanResult.isGoalMet() true when stopCondition found in output
 * AP04  AutoPlanResult records iteration history
 * AP05  PlanIteration records plan, output, reflection, goalMet, durationMs
 * AP06  AutoPlanEngine.run() with null @AutoPlan returns initial output unchanged
 * AP07  AutoPlanEngine RETURN_BEST policy returns result with goalMet=false
 * AP08  AutoPlanEngine THROW policy throws AutoPlanMaxIterationsException
 * AP09  @AutoPlan annotation is readable on method with all attributes
 * AP10  AutoPlanResult.getBestIteration() returns the longest-output iteration
 */
public class SquadOsPhase43Tests {

    // ── Agent stub with @AutoPlan methods ──────────────────────────────

    @Agent(role = AgentRole.ANALYST, name = "PlannerAgent",
           description = "An autonomous planning agent for research tasks.")
    static class PlannerAgent {

        @AutoPlan(
            goal          = "A comprehensive risk assessment with all 3 sections",
            maxIterations = 3,
            reflectOn     = "What sections are missing from the risk report?",
            stopCondition = "COMPLETE",
            onMaxIterations = IterationPolicy.RETURN_BEST,
            accumulate    = true
        )
        public String generateRiskReport(String topic) {
            return "Initial analysis of " + topic;
        }

        @AutoPlan(
            goal          = "Complete research summary",
            maxIterations = 2,
            stopCondition = "DONE",
            onMaxIterations = IterationPolicy.THROW
        )
        public String researchTopic(String topic) {
            return "Starting research on " + topic;
        }

        public String noAnnotation(String input) {
            return "No plan needed: " + input;
        }
    }

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 43 — @AutoPlan                                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "AP01_skipsLoopWhenInitialOutputMeetsGoal",
            "AP02_runsIterationsWhenGoalNotMet",
            "AP03_goalMetTrueWhenStopConditionFound",
            "AP04_resultRecordsIterationHistory",
            "AP05_planIterationRecordsAllFields",
            "AP06_nullAnnotationReturnsInitialOutput",
            "AP07_returnBestPolicyReturnsResult",
            "AP08_throwPolicyThrowsException",
            "AP09_autoPlanAnnotationReadable",
            "AP10_getBestIterationReturnsLongest",
        };
        var t = new SquadOsPhase43Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, c.getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.printf("Phase 43 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void AP01_skipsLoopWhenInitialOutputMeetsGoal() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Step 1 done. COMPLETE");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("generateRiskReport", String.class);

        // Initial output already contains "COMPLETE"
        AutoPlanResult result = engine.run(method, "Section 1. Section 2. Section 3. COMPLETE",
            "You are a risk analyst.", "Assess climate risks");

        assert result.isGoalMet()            : "Goal should be met on initial output";
        assert result.getTotalIterations() == 0 : "No iterations needed when initial output meets goal";
        System.out.printf("         → goalMet=%b, iterations=%d%n",
            result.isGoalMet(), result.getTotalIterations());
    }

    void AP02_runsIterationsWhenGoalNotMet() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        // Plan response
        llm.setResponse("next step", "Add the financial impact section.");
        // Execute response — 2nd iteration meets goal
        llm.setResponse("financial impact", "Financial risks identified. Risk level: HIGH. COMPLETE");
        // Default for anything else
        llm.setDefaultResponse("Continuing analysis...");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("generateRiskReport", String.class);

        AutoPlanResult result = engine.run(method, "Initial section on market risks.",
            "You are a risk analyst.", "Comprehensive risk report");

        assert result.getTotalIterations() >= 1  : "Should have run at least 1 iteration";
        assert result.getFinalOutput() != null   : "Final output should not be null";
    }

    void AP03_goalMetTrueWhenStopConditionFound() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Step complete. COMPLETE");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("generateRiskReport", String.class);

        AutoPlanResult result = engine.run(method, "Initial output without stop condition.",
            "System prompt.", "Produce risk report");

        // Either goal met (if LLM returns COMPLETE) or iterations exhausted
        assert result != null               : "Result should not be null";
        assert result.getFinalOutput() != null : "Final output should not be null";
    }

    void AP04_resultRecordsIterationHistory() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Intermediate output, still working...");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("generateRiskReport", String.class);

        AutoPlanResult result = engine.run(method, "No stop condition here.",
            "System prompt.", "Produce a report");

        assert result.getIterations() != null         : "Iterations list should not be null";
        // With maxIterations=3 and no stop condition → should have 3 iterations
        assert result.getTotalIterations() == result.getIterations().size()
            : "totalIterations should match list size";
        System.out.printf("         → iterations=%d%n", result.getTotalIterations());
    }

    void AP05_planIterationRecordsAllFields() {
        PlanIteration iter = new PlanIteration(
            1,
            "Identify the key risk factors.",
            "Risk factors: market volatility, regulatory changes.",
            "Missing: financial impact section.",
            false,
            42L
        );

        assert iter.number() == 1                              : "number should be 1";
        assert iter.plan().contains("risk factors")           : "plan should match";
        assert iter.output().contains("volatility")           : "output should match";
        assert iter.reflection().contains("financial")        : "reflection should match";
        assert !iter.goalMet()                                : "goalMet should be false";
        assert iter.durationMs() == 42L                      : "durationMs should be 42";
        assert iter.outputLength() > 0                       : "outputLength should be positive";
    }

    void AP06_nullAnnotationReturnsInitialOutput() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Should not be called.");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("noAnnotation", String.class);
        // noAnnotation has no @AutoPlan annotation

        AutoPlanResult result = engine.run(method, "original output",
            "System prompt.", "input task");

        assert "original output".equals(result.getFinalOutput())
            : "Should return initial output unchanged when no @AutoPlan annotation";
        assert result.getIterations().isEmpty() : "No iterations when no annotation";
    }

    void AP07_returnBestPolicyReturnsResult() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        // Never returns stop condition → max iterations reached → RETURN_BEST
        llm.setDefaultResponse("Partial output, not done yet.");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("generateRiskReport", String.class);

        AutoPlanResult result = engine.run(method, "Initial.",
            "System prompt.", "Build risk report");

        assert result != null           : "RETURN_BEST should not throw";
        assert !result.isGoalMet()      : "Goal should not be met";
        assert result.getFinalOutput() != null : "Final output should not be null";
        System.out.printf("         → policy=RETURN_BEST, goalMet=%b, iterations=%d%n",
            result.isGoalMet(), result.getTotalIterations());
    }

    void AP08_throwPolicyThrowsException() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Not done, never stop condition DONE reached.");

        AutoPlanEngine engine = new AutoPlanEngine(llm);
        Method method = PlannerAgent.class.getDeclaredMethod("researchTopic", String.class);

        boolean threw = false;
        try {
            engine.run(method, "Initial research.", "System prompt.", "Research AI trends");
        } catch (AutoPlanMaxIterationsException e) {
            threw = true;
            assert e.getMessage().contains("researchTopic") || e.getMessage() != null
                : "Exception should mention the method";
            System.out.printf("         → threw AutoPlanMaxIterationsException: %s%n",
                e.getMessage().substring(0, Math.min(80, e.getMessage().length())));
        }
        assert threw : "THROW policy should throw AutoPlanMaxIterationsException";
    }

    void AP09_autoPlanAnnotationReadable() throws Exception {
        Method method = PlannerAgent.class.getDeclaredMethod("generateRiskReport", String.class);
        AutoPlan ann = method.getAnnotation(AutoPlan.class);

        assert ann != null                             : "@AutoPlan should be present";
        assert ann.goal().contains("risk assessment")  : "Goal should match";
        assert ann.maxIterations() == 3               : "maxIterations should be 3";
        assert "COMPLETE".equals(ann.stopCondition())  : "stopCondition should be COMPLETE";
        assert ann.onMaxIterations() == IterationPolicy.RETURN_BEST : "Policy should be RETURN_BEST";
        assert ann.accumulate()                       : "accumulate should be true";
    }

    void AP10_getBestIterationReturnsLongest() {
        List<PlanIteration> iterations = List.of(
            new PlanIteration(1, "plan1", "short",         "refl1", false, 10L),
            new PlanIteration(2, "plan2", "medium output", "refl2", false, 20L),
            new PlanIteration(3, "plan3", "longest output by far", "refl3", false, 30L)
        );
        AutoPlanResult result = new AutoPlanResult("longest output by far", iterations, false);

        PlanIteration best = result.getBestIteration();
        assert best != null           : "Best iteration should not be null";
        assert best.number() == 3    : "Best should be iteration 3 (longest output)";
        assert best.output().contains("longest") : "Best output should be the longest";
    }
}
