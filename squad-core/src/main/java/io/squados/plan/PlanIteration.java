package io.squados.plan;

/**
 * Immutable snapshot of one @AutoPlan iteration.
 * Captured for logging, debugging, and RETURN_BEST selection.
 */
public record PlanIteration(
    int    number,        // 1-based iteration number
    String plan,          // what the agent planned to do
    String output,        // what the agent produced
    String reflection,    // what the agent thinks is missing
    boolean goalMet,      // did stopCondition appear in output?
    long    durationMs    // wall-clock time for this iteration
) {
    public int outputLength() { return output == null ? 0 : output.length(); }
}