package io.squados.exception;
/** Thrown when @AutoPlan reaches maxIterations without meeting the goal. */
public class AutoPlanMaxIterationsException extends RuntimeException {
    private final int    maxIterations;
    private final String goal;
    public AutoPlanMaxIterationsException(String method, int max, String goal) {
        super(String.format("@AutoPlan: %s reached max iterations (%d) without meeting goal: %s",
            method, max, goal));
        this.maxIterations = max;
        this.goal = goal;
    }
    public int    getMaxIterations() { return maxIterations; }
    public String getGoal()          { return goal; }
}