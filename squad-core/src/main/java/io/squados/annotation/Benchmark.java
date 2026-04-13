package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Registers an agent for automated benchmark evaluation.
 *
 * The framework runs the agent against a fixed golden dataset and
 * produces a {@link io.squados.benchmark.BenchmarkReport} with:
 *   - pass rate (% cases meeting minScore)
 *   - per-criteria average scores
 *   - per-case details (input, output, score, pass/fail)
 *   - comparison against a previous baseline version
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.ANALYST, name = "SentimentAnalyser")
 * {@literal @}Benchmark(
 *     dataset  = "classpath:benchmarks/sentiment.json",
 *     minScore = 0.80f,
 *     criteria = {EvalCriteria.CORRECTNESS, EvalCriteria.FAITHFULNESS},
 *     version  = "3.9.0"
 * )
 * public class SentimentAnalyserAgent {}
 * </pre>
 *
 * Run via:
 * <pre>
 *   BenchmarkRunner runner = new BenchmarkRunner(ctx, llm);
 *   BenchmarkReport report = runner.run(SentimentAnalyserAgent.class);
 *   report.print();
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Benchmark {

    /**
     * Path to the golden dataset.
     * Supported formats:
     *   classpath:benchmarks/sentiment.json — JSON array of {input, expected, tags[]}
     *   classpath:benchmarks/qa.csv         — CSV with columns: input,expected,tags
     */
    String dataset();

    /** Minimum overall score to count as a passing case (0.0–1.0). */
    float minScore() default 0.75f;

    /** Criteria to evaluate on each case. */
    EvalCriteria[] criteria() default {
        EvalCriteria.FAITHFULNESS,
        EvalCriteria.CORRECTNESS,
        EvalCriteria.RELEVANCE
    };

    /**
     * Version label for this benchmark run.
     * Used when comparing against stored baseline results.
     */
    String version() default "current";

    /**
     * Maximum number of cases to evaluate (useful for quick smoke tests).
     * 0 = run all cases.
     */
    int maxCases() default 0;

    /**
     * Whether to fail (throw) if pass rate is below {@code minScore}.
     * Set false to collect results without failing the build.
     */
    boolean failOnRegression() default true;
}
