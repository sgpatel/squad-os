package io.squados.annotation;
/** What to do when @Eval score stays below minScore after all retries. */
public enum EvalFailPolicy {
    /** Throw EvalFailedException with the scores and attempts. */
    THROW,
    /** Return the highest-scoring attempt even if below threshold. */
    RETURN_BEST
}