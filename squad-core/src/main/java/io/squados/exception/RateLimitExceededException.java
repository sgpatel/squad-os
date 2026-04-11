package io.squados.exception;

/**
 * Thrown when an agent exceeds its @RateLimit constraints
 * (calls/min or tokens/hour). Not retried by RetryEngine.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String agentName;
    private final String limitType; // "calls" | "tokens"

    public RateLimitExceededException(String agentName, String limitType, String message) {
        super(message);
        this.agentName = agentName;
        this.limitType = limitType;
    }

    public String getAgentName() { return agentName; }
    public String getLimitType() { return limitType; }
}
