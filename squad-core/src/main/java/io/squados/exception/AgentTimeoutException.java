package io.squados.exception;

/**
 * Thrown when an @Timeout deadline is exceeded for an agent call.
 */
public class AgentTimeoutException extends RuntimeException {

    private final String agentName;
    private final long   timeoutMs;

    public AgentTimeoutException(String agentName, long timeoutMs) {
        super("Agent '" + agentName + "' timed out after " + timeoutMs + "ms");
        this.agentName = agentName;
        this.timeoutMs = timeoutMs;
    }

    public String getAgentName() { return agentName; }
    public long   getTimeoutMs() { return timeoutMs; }
}
