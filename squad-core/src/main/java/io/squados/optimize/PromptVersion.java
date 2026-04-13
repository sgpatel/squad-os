package io.squados.optimize;

/**
 * An immutable snapshot of a prompt at a specific optimization iteration.
 */
public record PromptVersion(
        int    iteration,
        String prompt,
        float  score,
        String changeRationale,
        long   createdAt
) {
    public static PromptVersion of(int iteration, String prompt, float score, String rationale) {
        return new PromptVersion(iteration, prompt, score, rationale, System.currentTimeMillis());
    }

    public boolean betterThan(PromptVersion other, float minImprovement) {
        return score >= other.score + minImprovement;
    }

    @Override
    public String toString() {
        return String.format("PromptVersion{iter=%d, score=%.3f, rationale='%s'}",
            iteration, score, changeRationale != null
                ? changeRationale.substring(0, Math.min(60, changeRationale.length())) : "");
    }
}
