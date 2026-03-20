package io.squados.health;

import io.squados.annotation.AgentRole;
import java.time.Instant;

/**
 * Snapshot of an agent's current health state.
 * Tracked by AgentHealthMonitor per registered agent.
 */
public class AgentHealth {

    public enum Status { HEALTHY, DEGRADED, CIRCUIT_OPEN }

    private final AgentRole role;
    private final String    name;
    private       Status    status        = Status.HEALTHY;
    private       int       failureCount  = 0;
    private       int       callCount     = 0;
    private       long      totalLatencyMs= 0;
    private       Instant   lastCallAt;
    private       String    lastError;

    public AgentHealth(AgentRole role, String name) {
        this.role = role;
        this.name = name;
    }

    public void recordSuccess(long latencyMs) {
        callCount++;
        totalLatencyMs += latencyMs;
        lastCallAt   = Instant.now();
        failureCount = 0; // reset on success
        if (status == Status.DEGRADED) status = Status.HEALTHY;
    }

    public void recordFailure(String error) {
        callCount++;
        failureCount++;
        lastCallAt = Instant.now();
        lastError  = error;
    }

    public AgentRole getRole()         { return role; }
    public String    getName()         { return name; }
    public Status    getStatus()       { return status; }
    public int       getFailureCount() { return failureCount; }
    public int       getCallCount()    { return callCount; }
    public String    getLastError()    { return lastError; }
    public Instant   getLastCallAt()   { return lastCallAt; }

    public long avgLatencyMs() {
        return callCount == 0 ? 0 : totalLatencyMs / callCount;
    }

    public void setStatus(Status s) { this.status = s; }

    @Override
    public String toString() {
        return "AgentHealth{name='" + name + "', status=" + status
               + ", failures=" + failureCount + ", calls=" + callCount
               + ", avgLatency=" + avgLatencyMs() + "ms}";
    }
}
