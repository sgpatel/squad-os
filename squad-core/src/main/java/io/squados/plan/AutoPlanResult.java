package io.squados.plan;

import java.util.List;

/**
 * The result of an @AutoPlan loop — final output plus full iteration history.
 */
public class AutoPlanResult {

    private final String              finalOutput;
    private final List<PlanIteration> iterations;
    private final boolean             goalMet;
    private final int                 totalIterations;
    private final long                totalDurationMs;

    public AutoPlanResult(String finalOutput,
                          List<PlanIteration> iterations,
                          boolean goalMet) {
        this.finalOutput     = finalOutput;
        this.iterations      = List.copyOf(iterations);
        this.goalMet         = goalMet;
        this.totalIterations = iterations.size();
        this.totalDurationMs = iterations.stream()
            .mapToLong(PlanIteration::durationMs).sum();
    }

    public String              getFinalOutput()     { return finalOutput; }
    public List<PlanIteration> getIterations()      { return iterations; }
    public boolean             isGoalMet()          { return goalMet; }
    public int                 getTotalIterations() { return totalIterations; }
    public long                getTotalDurationMs() { return totalDurationMs; }

    /** The iteration with the longest output — used by RETURN_BEST policy. */
    public PlanIteration getBestIteration() {
        return iterations.stream()
            .max(java.util.Comparator.comparingInt(PlanIteration::outputLength))
            .orElse(null);
    }

    @Override
    public String toString() {
        return String.format(
            "AutoPlanResult{goalMet=%b, iterations=%d, duration=%dms, outputLen=%d}",
            goalMet, totalIterations, totalDurationMs,
            finalOutput == null ? 0 : finalOutput.length());
    }
}