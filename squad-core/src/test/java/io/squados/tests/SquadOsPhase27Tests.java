package io.squados.tests;

import io.squados.annotation.*;
import io.squados.config.SquadConfig;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.eval.EvalJudge;
import io.squados.eval.EvalScore;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmResponse;
import io.squados.llm.MockLlmPort;
import io.squados.reflexion.ReflexionEngine;
import io.squados.reflexion.ReflexionIteration;
import io.squados.reflexion.ReflexionResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 27 — Reflexion Engine (@Reflexion annotation)
 *
 * R01 — ReflexionEngine.run() returns immediately when first output scores >= threshold
 * R02 — ReflexionEngine iterates when score < threshold
 * R03 — ReflexionResult captures full iteration history
 * R04 — maxIterations=0 means one evaluation, no critique cycle
 * R05 — Agent without @Reflexion is unaffected (reflexionEngine not injected)
 * R06 — ReflexionResult.bestScore() returns highest-scoring iteration
 * R07 — scoreThreshold=0.0 accepts any output on first pass
 * R08 — ReflexionResult.thresholdReached() is false when threshold never met
 * R09 — ReflexionResult.finalOutput() is best-scoring output when threshold not met
 * R10 — ReflexionResult.totalIterations() equals number of history entries
 */
public class SquadOsPhase27Tests {

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase27Tests();
        String[] tests = {
            "R01_acceptsHighScoringOutputImmediately",
            "R02_iteratesWhenScoreBelowThreshold",
            "R03_capturesIterationHistory",
            "R04_maxIterationsZeroMeansOneEval",
            "R05_agentWithoutReflexionUnaffected",
            "R06_bestScoreReturnsHighestIteration",
            "R07_zeroThresholdAcceptsAnyOutput",
            "R08_thresholdReachedFalseWhenNeverMet",
            "R09_finalOutputIsBestWhenThresholdNotMet",
            "R10_totalIterationsEqualsHistorySize",
        };
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s — %s%n", test, cause.getMessage());
                failed++;
            }
        }
        System.out.printf("%nPhase 27: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Test methods ──────────────────────────────────────────────────

    void R01_acceptsHighScoringOutputImmediately() {
        // Mock LLM: initial output scores high (mocked via EvalJudge)
        MockLlmPort mock = new MockLlmPort();
        // Judge call returns high score string for any input
        mock.setDefaultResponse("FAITHFULNESS: 0.9 - excellent\nCOMPLETENESS: 0.9 - good\nRELEVANCE: 0.9 - great");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(3, 0.80f,
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.RELEVANCE});

        ReflexionResult result = engine.run(ann, "A detailed and complete answer.", "System prompt", "What is X?");

        assert result.thresholdReached() : "Should have reached threshold";
        assert result.totalIterations() == 1 : "Should stop after first iteration";
        assert result.finalOutput().equals("A detailed and complete answer.") : "Output mismatch";
    }

    void R02_iteratesWhenScoreBelowThreshold() {
        MockLlmPort mock = new MockLlmPort();
        // First judge call: low score; subsequent calls produce critique then better answer
        mock.setResponse("ORIGINAL TASK", "FAITHFULNESS: 0.3 - poor\nCOMPLETENESS: 0.3 - incomplete\nRELEVANCE: 0.3 - off-topic");
        mock.setResponse("critique", "The response lacks depth. Add more detail.");
        mock.setDefaultResponse("A much improved and comprehensive answer.");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(2, 0.80f,
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.RELEVANCE});

        ReflexionResult result = engine.run(ann, "Short answer.", "System prompt", "What is X?");

        assert result.totalIterations() >= 2 : "Should have iterated at least twice, got: " + result.totalIterations();
    }

    void R03_capturesIterationHistory() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("FAITHFULNESS: 0.9 - good\nCOMPLETENESS: 0.9 - good\nRELEVANCE: 0.9 - good");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(3, 0.80f, new EvalCriteria[]{EvalCriteria.FAITHFULNESS});

        ReflexionResult result = engine.run(ann, "Good answer.", "System prompt", "Task");

        List<ReflexionIteration> history = result.iterations();
        assert !history.isEmpty() : "History should not be empty";
        assert history.get(0).iteration() == 1 : "First iteration should be 1";
        assert history.get(0).output().equals("Good answer.") : "First output mismatch";
        assert history.get(0).score() != null : "Score should not be null";
    }

    void R04_maxIterationsZeroMeansOneEval() {
        MockLlmPort mock = new MockLlmPort();
        // Returns low score — but maxIterations=0 means no retries
        mock.setDefaultResponse("FAITHFULNESS: 0.3 - poor\nCOMPLETENESS: 0.3 - poor\nRELEVANCE: 0.3 - poor");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(0, 0.90f, new EvalCriteria[]{EvalCriteria.FAITHFULNESS});

        ReflexionResult result = engine.run(ann, "Poor answer.", "System prompt", "Task");

        assert result.totalIterations() == 1 : "Should evaluate exactly once, got: " + result.totalIterations();
        assert !result.thresholdReached() : "Threshold should not be reached with low score";
    }

    void R05_agentWithoutReflexionUnaffected() throws Exception {
        // Agent with no @Reflexion — ReflexionEngine is not injected
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("Basic answer");

        SquadConfig config = SquadConfig.forTesting(List.of(PlainAgent.class));
        SquadContext ctx = new SquadContext(config, mock);
        ctx.boot();

        var response = ctx.submitTo(AgentRole.EXECUTOR, "Do something");
        assert response.isSuccess() : "Plain agent should succeed without @Reflexion";
        assert response.content().contains("Basic answer") : "Expected basic answer, got: " + response.content();
    }

    void R06_bestScoreReturnsHighestIteration() {
        MockLlmPort mock = new MockLlmPort();
        // Simulate two iterations with different scores
        mock.setDefaultResponse("FAITHFULNESS: 0.5 - ok\nCOMPLETENESS: 0.5 - ok\nRELEVANCE: 0.5 - ok");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(1, 0.90f,
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.RELEVANCE});

        ReflexionResult result = engine.run(ann, "Mediocre answer.", "System prompt", "Task");

        EvalScore best = result.bestScore();
        assert best != null : "bestScore() should not be null";
        assert best.overall() >= 0.0f && best.overall() <= 1.0f : "Score out of range: " + best.overall();
    }

    void R07_zeroThresholdAcceptsAnyOutput() {
        MockLlmPort mock = new MockLlmPort();
        // Even with zero scores this should be accepted immediately (threshold=0.0)
        mock.setDefaultResponse("FAITHFULNESS: 0.0 - terrible");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(3, 0.0f, new EvalCriteria[]{EvalCriteria.FAITHFULNESS});

        ReflexionResult result = engine.run(ann, "Terrible answer.", "System prompt", "Task");

        assert result.thresholdReached() : "Zero threshold should always be reached";
        assert result.totalIterations() == 1 : "Should stop at first iteration";
    }

    void R08_thresholdReachedFalseWhenNeverMet() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("FAITHFULNESS: 0.2 - bad\nCOMPLETENESS: 0.2 - bad\nRELEVANCE: 0.2 - bad");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(1, 0.99f,
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.RELEVANCE});

        ReflexionResult result = engine.run(ann, "Poor answer.", "System prompt", "Task");

        assert !result.thresholdReached() : "Threshold of 0.99 should not be reached with 0.2 scores";
    }

    void R09_finalOutputIsBestWhenThresholdNotMet() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("FAITHFULNESS: 0.2 - bad\nCOMPLETENESS: 0.2 - bad\nRELEVANCE: 0.2 - bad");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(1, 0.99f,
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.RELEVANCE});

        ReflexionResult result = engine.run(ann, "Initial answer.", "System prompt", "Task");

        assert result.finalOutput() != null : "finalOutput should not be null even when threshold not met";
        assert !result.finalOutput().isBlank() : "finalOutput should not be blank";
    }

    void R10_totalIterationsEqualsHistorySize() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("FAITHFULNESS: 0.9 - great\nCOMPLETENESS: 0.9 - great\nRELEVANCE: 0.9 - great");

        ReflexionEngine engine = new ReflexionEngine(mock);
        Reflexion ann = buildReflexion(3, 0.80f,
            new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS, EvalCriteria.RELEVANCE});

        ReflexionResult result = engine.run(ann, "Great answer.", "System prompt", "Task");

        assert result.totalIterations() == result.iterations().size()
            : "totalIterations() must match iterations().size()";
    }

    // ── Helpers ───────────────────────────────────────────────────────

    @Agent(role = AgentRole.EXECUTOR, name = "PlainAgent",
           description = "A plain agent without Reflexion.")
    static class PlainAgent {}

    static Reflexion buildReflexion(int maxIter, float threshold, EvalCriteria[] criteria) {
        return new Reflexion() {
            public Class<? extends java.lang.annotation.Annotation> annotationType() { return Reflexion.class; }
            public int maxIterations()    { return maxIter; }
            public String judgeAgent()    { return ""; }
            public float scoreThreshold() { return threshold; }
            public EvalCriteria[] criteria() { return criteria; }
        };
    }
}
