package io.squados.optimize;

import java.util.List;

/**
 * The outcome of a full {@link PromptOptimizerEngine} run.
 */
public record PromptOptimizationResult(
        String            agentName,
        String            bestPrompt,
        float             bestScore,
        float             initialScore,
        int               iterations,
        boolean           thresholdReached,
        List<PromptVersion> history
) {
    public float improvement() { return bestScore - initialScore; }

    @Override
    public String toString() {
        return String.format(
            "PromptOptimizationResult{agent='%s', score=%.3f→%.3f (+%.3f), iter=%d, reached=%b}",
            agentName, initialScore, bestScore, improvement(), iterations, thresholdReached);
    }
}
