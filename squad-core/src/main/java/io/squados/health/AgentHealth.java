package io.squados.health;

import io.squados.annotation.AgentRole;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snapshot of an agent's current health state.
 * Thread-safe: AtomicInteger for counters, volatile for status and timestamps.
 */
public class AgentHealth {

    public enum Status { HEALTHY, DEGRADED, CIRCUIT_OPEN }

    private final AgentRole       role;
    private final String          name;
    private volatile Status       status         = Status.HEALTHY;
    private final AtomicInteger   failureCount   = new AtomicInteger(0);
    private final AtomicInteger   callCount      = new AtomicInteger(0);
    private final AtomicLong      totalLatencyMs = new AtomicLong(0);
    private volatile Instant      lastCallAt;
    private volatile String       lastError;

    public AgentHealth(AgentRole role, String name) {
        this.role = role;
        this.name = name;
    }

    public void recordSuccess(long latencyMs) {
        callCount.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        lastCallAt     = Instant.now();
        failureCount.set(0);
        if (status == Status.DEGRADED) status = Status.HEALTHY;
    }

    public void recordFailure(String error) {
        callCount.incrementAndGet();
        failureCount.incrementAndGet();
        lastCallAt = Instant.now();
        lastError  = error;
    }

    public AgentRole getRole()         { return role; }
    public String    getName()         { return name; }
    public Status    getStatus()       { return status; }
    public int       getFailureCount() { return failureCount.get(); }
    public int       getCallCount()    { return callCount.get(); }
    public String    getLastError()    { return lastError; }
    public Instant   getLastCallAt()   { return lastCallAt; }

    public long avgLatencyMs() {
        int c = callCount.get();
        return c == 0 ? 0 : totalLatencyMs.get() / c;
    }

    public void setStatus(Status s) { this.status = s; }

    @Override
    public String toString() {
        return "AgentHealth{name='" + name + "', status=" + status
               + ", failures=" + failureCount.get()
               + ", calls=" + callCount.get()
               + ", avgLatency=" + avgLatencyMs() + "ms}";
    }
}
