package io.squados.annotation;
/** What to do when @AutoPlan reaches maxIterations without meeting the goal. */
public enum IterationPolicy {
    /** Return the best (longest/most complete) output accumulated so far. */
    RETURN_BEST,
    /** Return the output from the final iteration regardless of quality. */
    RETURN_LAST,
    /** Throw AutoPlanMaxIterationsException. */
    THROW
}