package io.squados.benchmark;

import io.squados.annotation.Benchmark;
import io.squados.annotation.EvalCriteria;
import io.squados.context.SquadContext;
import io.squados.eval.EvalJudge;
import io.squados.eval.EvalScore;
import io.squados.exception.BenchmarkRegressionException;
import io.squados.llm.LlmPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a benchmark for all {@literal @}Benchmark-annotated agents registered
 * in a {@link SquadContext} (or directly against a provided LLM + agent class).
 *
 * Usage:
 * <pre>
 *   BenchmarkRunner runner = new BenchmarkRunner(ctx, llm);
 *   BenchmarkReport report = runner.run(SentimentAnalyserAgent.class);
 *   report.print();
 *
 *   // Or run inline dataset:
 *   BenchmarkDataset ds = BenchmarkDataset.builder()
 *       .add("Analyse: 'Amazing!'", "POSITIVE")
 *       .add("Analyse: 'Terrible.'", "NEGATIVE")
 *       .build();
 *   BenchmarkReport report = runner.run(SentimentAnalyserAgent.class, ds, 0.8f);
 * </pre>
 */
public class BenchmarkRunner {

    private final SquadContext ctx;
    private final LlmPort      judgePort;

    public BenchmarkRunner(SquadContext ctx, LlmPort judgePort) {
        this.ctx       = ctx;
        this.judgePort = judgePort;
    }

    /**
     * Run the benchmark declared via {@literal @}Benchmark on {@code agentClass}.
     * Loads the dataset from the annotation's {@code dataset} path.
     */
    public BenchmarkReport run(Class<?> agentClass) {
        Benchmark ann = agentClass.getAnnotation(Benchmark.class);
        if (ann == null) throw new IllegalArgumentException(
            agentClass.getSimpleName() + " is not annotated with @Benchmark");

        BenchmarkDataset dataset = BenchmarkDataset.load(ann.dataset());
        return run(agentClass, dataset, ann);
    }

    /**
     * Run against a provided dataset with explicit options.
     * Useful for programmatic benchmarks without the annotation.
     */
    public BenchmarkReport run(Class<?> agentClass, BenchmarkDataset dataset, float minScore) {
        Benchmark ann = agentClass.getAnnotation(Benchmark.class);
        EvalCriteria[] criteria = ann != null ? ann.criteria()
            : new EvalCriteria[]{EvalCriteria.FAITHFULNESS, EvalCriteria.CORRECTNESS};
        String version = ann != null ? ann.version() : "current";
        int maxCases   = ann != null ? ann.maxCases() : 0;
        return runInternal(agentClass, dataset, minScore, criteria, version, maxCases,
            ann != null && ann.failOnRegression());
    }

    // ── Internals ─────────────────────────────────────────────────────

    private BenchmarkReport run(Class<?> agentClass, BenchmarkDataset dataset, Benchmark ann) {
        return runInternal(agentClass, dataset, ann.minScore(), ann.criteria(),
            ann.version(), ann.maxCases(), ann.failOnRegression());
    }

    private BenchmarkReport runInternal(Class<?> agentClass,
                                        BenchmarkDataset dataset,
                                        float minScore,
                                        EvalCriteria[] criteria,
                                        String version,
                                        int maxCases,
                                        boolean failOnRegression) {
        String agentName = resolveAgentName(agentClass);
        EvalJudge judge  = new EvalJudge(judgePort);

        List<BenchmarkCase> cases = dataset.cases();
        if (maxCases > 0 && cases.size() > maxCases) {
            cases = cases.subList(0, maxCases);
        }

        System.out.printf("[Benchmark] Running %d cases for %s v%s...%n",
            cases.size(), agentName, version);

        List<BenchmarkCaseResult> results = new ArrayList<>();
        long startAll = System.currentTimeMillis();

        for (int i = 0; i < cases.size(); i++) {
            BenchmarkCase bc = cases.get(i);
            long caseStart = System.currentTimeMillis();
            String actual;
            try {
                var response = ctx.submit(bc.input());
                actual = response.isSuccess() && response.content() != null
                    ? response.content() : "(agent returned no output)";
            } catch (Exception e) {
                actual = "(error: " + e.getMessage() + ")";
            }
            long latency  = System.currentTimeMillis() - caseStart;
            EvalScore score = judge.evaluate(bc.input(), actual, criteria, i + 1);
            boolean passed  = score.overall() >= minScore;

            results.add(new BenchmarkCaseResult(bc, actual, score, passed, latency));
            System.out.printf("[Benchmark] Case %d/%d — score=%.2f %s%n",
                i + 1, cases.size(), score.overall(), passed ? "PASS" : "FAIL");
        }

        long totalMs = System.currentTimeMillis() - startAll;
        BenchmarkReport report = new BenchmarkReport(agentName, version, results, minScore, totalMs);

        if (failOnRegression && !report.meetsThreshold()) {
            throw new BenchmarkRegressionException(agentName, version,
                report.passRate(), minScore);
        }
        return report;
    }

    private static String resolveAgentName(Class<?> cls) {
        var ann = cls.getAnnotation(io.squados.annotation.Agent.class);
        return ann != null && !ann.name().isBlank() ? ann.name() : cls.getSimpleName();
    }
}
