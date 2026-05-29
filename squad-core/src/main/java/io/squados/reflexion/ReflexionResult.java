package io.squados.reflexion;

import io.squados.eval.EvalScore;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The outcome of a complete Reflexion loop.
 *
 * Contains the accepted output (best-scoring iteration that crossed the
 * threshold, or the best available if the threshold was never reached),
 * the full iteration history, and scoring metadata.
 */
public class ReflexionResult {

    private final String                   finalOutput;
    private final List<ReflexionIteration> iterations;
    private final boolean                  thresholdReached;
    private final int                      totalIterations;

    public ReflexionResult(String finalOutput,
                           List<ReflexionIteration> iterations,
                           boolean thresholdReached) {
        this.finalOutput      = finalOutput;
        this.iterations       = Collections.unmodifiableList(iterations);
        this.thresholdReached = thresholdReached;
        this.totalIterations  = iterations.size();
    }

    /** The accepted agent output. */
    public String finalOutput() { return finalOutput; }

    /** Full history of critique-and-retry iterations. */
    public List<ReflexionIteration> iterations() { return iterations; }

    /** True when an iteration scored ≥ scoreThreshold. */
    public boolean thresholdReached() { return thresholdReached; }

    /** Total number of LLM calls made (initial + critique cycles). */
    public int totalIterations() { return totalIterations; }

    /** The highest overall EvalScore across all iterations. */
    public EvalScore bestScore() {
        return iterations.stream()
            .map(ReflexionIteration::score)
            .max(Comparator.comparingDouble(EvalScore::overall))
            .orElse(null);
    }

    @Override
    public String toString() {
        return String.format(
            "ReflexionResult{iterations=%d, thresholdReached=%b, bestScore=%.2f}",
            totalIterations, thresholdReached,
            bestScore() != null ? bestScore().overall() : 0f);
    }
}
