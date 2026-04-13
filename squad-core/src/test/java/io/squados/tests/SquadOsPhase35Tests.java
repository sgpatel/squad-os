package io.squados.tests;

import io.squados.annotation.*;
import io.squados.benchmark.BenchmarkCase;
import io.squados.config.SquadConfig;
import io.squados.context.SquadContext;
import io.squados.llm.MockLlmPort;
import io.squados.optimize.*;

import java.util.List;
import java.util.Optional;

/**
 * Phase 35 — Prompt Optimizer tests.
 *
 * PO01  PromptVersionStore saves and retrieves versions
 * PO02  PromptVersionStore returns best (highest-score) version
 * PO03  PromptVersionStore latest() returns most recent
 * PO04  PromptVersion.betterThan() respects minImprovement
 * PO05  PromptOptimizerEngine terminates when threshold reached
 * PO06  PromptOptimizerEngine stores each iteration in PromptVersionStore
 * PO07  PromptOptimizationResult.improvement() calculates delta
 * PO08  PromptOptimizerEngine runs at least one iteration
 * PO09  PromptOptimizerEngine handles empty training set gracefully
 * PO10  @OptimizePrompt annotation is readable on agent class
 */
public class SquadOsPhase35Tests {

    @Agent(role = AgentRole.ANALYST, name = "SentimentOpt",
           description = "Optimizable sentiment analyser.")
    @OptimizePrompt(scoreThreshold = 0.85f, maxIterations = 3,
                    criteria = {EvalCriteria.FAITHFULNESS, EvalCriteria.CORRECTNESS},
                    minImprovement = 0.01f)
    static class SentimentOptAgent {}

    static final MockLlmPort LLM = new MockLlmPort();

    static {
        // High judge scores → optimizer converges quickly
        LLM.setDefaultResponse(
            "FAITHFULNESS: 0.90 - accurate\nCORRECTNESS: 0.88 - correct\nRELEVANCE: 0.89");
    }

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 35 — Prompt Optimizer                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "PO01_versionStoreSavesAndRetrieves",
            "PO02_versionStoreReturnsBestVersion",
            "PO03_versionStoreReturnsLatest",
            "PO04_promptVersionBetterThanRespectsMinImprovement",
            "PO05_optimizerTerminatesWhenThresholdReached",
            "PO06_optimizerStoresIterationsInStore",
            "PO07_optimizationResultImprovementDelta",
            "PO08_optimizerRunsAtLeastOneIteration",
            "PO09_optimizerHandlesEmptyTrainingSet",
            "PO10_optimizePromptAnnotationReadable",
        };
        var t = new SquadOsPhase35Tests();
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
        System.out.printf("Phase 35 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    void PO01_versionStoreSavesAndRetrieves() {
        PromptVersionStore store = new PromptVersionStore();
        store.save("AgentA", PromptVersion.of(0, "Initial prompt", 0.60f, "initial"));
        store.save("AgentA", PromptVersion.of(1, "Improved prompt", 0.75f, "iter1"));

        assert store.all("AgentA").size() == 2 : "Should have 2 versions";
        assert store.hasHistory("AgentA")      : "Should report history exists";
    }

    void PO02_versionStoreReturnsBestVersion() {
        PromptVersionStore store = new PromptVersionStore();
        store.save("AgentB", PromptVersion.of(0, "P0", 0.60f, "i0"));
        store.save("AgentB", PromptVersion.of(1, "P1", 0.85f, "i1"));
        store.save("AgentB", PromptVersion.of(2, "P2", 0.72f, "i2"));

        Optional<PromptVersion> best = store.best("AgentB");
        assert best.isPresent()       : "Best should be present";
        assert best.get().score() == 0.85f : "Best score should be 0.85";
        assert "P1".equals(best.get().prompt()) : "Best prompt should be P1";
    }

    void PO03_versionStoreReturnsLatest() {
        PromptVersionStore store = new PromptVersionStore();
        store.save("AgentC", PromptVersion.of(0, "P0", 0.60f, "i0"));
        store.save("AgentC", PromptVersion.of(1, "P1", 0.70f, "i1"));

        Optional<PromptVersion> latest = store.latest("AgentC");
        assert latest.isPresent()          : "Latest should be present";
        assert latest.get().iteration() == 1 : "Latest iteration should be 1";
    }

    void PO04_promptVersionBetterThanRespectsMinImprovement() {
        PromptVersion v1 = PromptVersion.of(0, "P0", 0.70f, "i0");
        PromptVersion v2 = PromptVersion.of(1, "P1", 0.75f, "i1");
        PromptVersion v3 = PromptVersion.of(2, "P2", 0.71f, "i2");

        assert  v2.betterThan(v1, 0.04f) : "v2 (0.75) should beat v1 (0.70) by 0.05 >= min 0.04";
        assert !v3.betterThan(v1, 0.04f) : "v3 (0.71) should NOT beat v1 (0.70) by only 0.01 < min 0.04";
    }

    void PO05_optimizerTerminatesWhenThresholdReached() {
        // LLM returns very high scores → should converge at iteration 1
        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentOptAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        PromptOptimizerEngine opt = new PromptOptimizerEngine(ctx, LLM);
        List<BenchmarkCase> examples = List.of(
            BenchmarkCase.of("Classify: 'Amazing product!'", "POSITIVE")
        );

        PromptOptimizationResult result = opt.optimize(SentimentOptAgent.class, examples);

        // With high scores, should converge (thresholdReached=true) quickly
        assert result.thresholdReached() : "Should reach threshold with high judge scores";
        assert result.iterations() <= 3  : "Should not exceed maxIterations";
        System.out.printf("         → iterations=%d bestScore=%.3f reached=%b%n",
            result.iterations(), result.bestScore(), result.thresholdReached());
    }

    void PO06_optimizerStoresIterationsInStore() {
        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentOptAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        PromptVersionStore store = new PromptVersionStore();
        PromptOptimizerEngine opt = new PromptOptimizerEngine(ctx, LLM, store);
        List<BenchmarkCase> examples = List.of(
            BenchmarkCase.of("Classify: 'Great!'", "POSITIVE")
        );

        opt.optimize(SentimentOptAgent.class, examples);

        // Store should have at least the initial version
        assert store.hasHistory("SentimentOpt") : "Store should have history for agent";
        assert !store.all("SentimentOpt").isEmpty() : "Should have at least one version";
    }

    void PO07_optimizationResultImprovementDelta() {
        PromptOptimizationResult result = new PromptOptimizationResult(
            "TestAgent", "new prompt", 0.85f, 0.60f, 2, true,
            List.of(
                PromptVersion.of(0, "old", 0.60f, "i0"),
                PromptVersion.of(1, "new", 0.85f, "i1")
            )
        );
        assert Math.abs(result.improvement() - 0.25f) < 0.001f
            : "Improvement should be 0.25 (0.85 - 0.60): " + result.improvement();
    }

    void PO08_optimizerRunsAtLeastOneIteration() {
        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentOptAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        PromptOptimizerEngine opt = new PromptOptimizerEngine(ctx, LLM);
        List<BenchmarkCase> examples = List.of(
            BenchmarkCase.of("Classify: 'Great!'", "POSITIVE")
        );

        PromptOptimizationResult result = opt.optimize(SentimentOptAgent.class, examples);
        assert result.history().size() >= 1 : "Should have at least 1 version in history";
        assert result.bestPrompt() != null  : "Best prompt should not be null";
    }

    void PO09_optimizerHandlesEmptyTrainingSet() {
        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentOptAgent.class));
        SquadContext ctx = new SquadContext(cfg, LLM);
        ctx.boot();

        PromptOptimizerEngine opt = new PromptOptimizerEngine(ctx, LLM);

        // Empty examples → should not throw, should return result with 0 score
        PromptOptimizationResult result = opt.optimize(
            SentimentOptAgent.class, List.of(),
            0.85f, 2, new EvalCriteria[]{EvalCriteria.FAITHFULNESS}, 0.01f);

        assert result != null              : "Should not throw on empty examples";
        assert result.bestPrompt() != null : "Should still have a prompt";
    }

    void PO10_optimizePromptAnnotationReadable() {
        OptimizePrompt ann = SentimentOptAgent.class.getAnnotation(OptimizePrompt.class);
        assert ann != null                     : "@OptimizePrompt should be present";
        assert ann.scoreThreshold() == 0.85f   : "Threshold should be 0.85";
        assert ann.maxIterations() == 3        : "Max iterations should be 3";
        assert ann.minImprovement() == 0.01f   : "Min improvement should be 0.01";
        assert ann.criteria().length == 2      : "Should have 2 criteria";
    }
}
