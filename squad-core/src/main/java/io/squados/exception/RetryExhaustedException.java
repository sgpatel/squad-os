package io.squados.exception;

/**
 * Thrown when all @Retry attempts are exhausted without success.
 * Carries the last underlying cause, agent name, attempt count, and elapsed time.
 */
public class RetryExhaustedException extends RuntimeException {

    private final String agentName;
    private final int    attempts;
    private final long   totalElapsedMs;

    public RetryExhaustedException(String agentName, int attempts,
                                   long totalElapsedMs, Throwable cause) {
        super("Retry exhausted after " + attempts + " attempt(s) for agent '"
              + agentName + "' (" + totalElapsedMs + "ms total): " + cause.getMessage(), cause);
        this.agentName      = agentName;
        this.attempts       = attempts;
        this.totalElapsedMs = totalElapsedMs;
    }

    public String getAgentName()      { return agentName; }
    public int    getAttempts()       { return attempts; }
    public long   getTotalElapsedMs() { return totalElapsedMs; }
}
