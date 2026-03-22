package io.squados.exception;
import io.squados.eval.EvalScore;

/** Thrown when @Eval score stays below minScore after all retries. */
public class EvalFailedException extends RuntimeException {
    private final EvalScore bestScore;
    private final float     minScore;
    private final int       attempts;

    public EvalFailedException(String methodName, EvalScore bestScore,
                               float minScore, int maxRetries) {
        super(String.format("@Eval failed for %s: best score %.2f < %.2f after %d attempts",
            methodName,
            bestScore != null ? bestScore.overall() : 0f,
            minScore, maxRetries + 1));
        this.bestScore = bestScore;
        this.minScore  = minScore;
        this.attempts  = maxRetries + 1;
    }

    public EvalScore getBestScore() { return bestScore; }
    public float     getMinScore()  { return minScore; }
    public int       getAttempts()  { return attempts; }
}