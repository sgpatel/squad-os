package io.squados.health;

import io.squados.annotation.AgentRole;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-agent circuit breaker. Thread-safe.
 *
 * States: HEALTHY -> DEGRADED -> CIRCUIT_OPEN
 *
 * The critical section is the status transition check in onFailure():
 * without synchronization, 50 threads can ALL pass the
 * "failureCount >= threshold" check before any of them sets CIRCUIT_OPEN,
 * producing dozens of redundant open events.
 *
 * Fix: synchronize per-agent on the AgentHealth object so only one
 * thread performs the transition and publishes the bus event.
 */
public class AgentCircuitBreaker {

    private final int             failureThreshold;
    private final int             successThreshold;
    private final AgentMessageBus bus;
    private final Map<AgentRole, AgentHealth> health = new ConcurrentHashMap<>();

    public AgentCircuitBreaker(AgentMessageBus bus) {
        this(3, 2, bus);
    }

    public AgentCircuitBreaker(int failureThreshold, int successThreshold,
                               AgentMessageBus bus) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.bus              = bus;
    }

    public void register(AgentRole role, String name) {
        health.put(role, new AgentHealth(role, name));
    }

    public boolean allowCall(AgentRole role) {
        AgentHealth h = health.get(role);
        return h == null || h.getStatus() != AgentHealth.Status.CIRCUIT_OPEN;
    }

    public void onSuccess(AgentRole role, long latencyMs) {
        AgentHealth h = health.get(role);
        if (h == null) return;
        synchronized (h) {
            h.recordSuccess(latencyMs);
            if (h.getStatus() == AgentHealth.Status.CIRCUIT_OPEN
                    && h.getFailureCount() == 0) {
                h.setStatus(AgentHealth.Status.HEALTHY);
                System.out.printf("[SquadOS] Circuit CLOSED for %s — recovering%n", role);
            }
        }
    }

    public void onFailure(AgentRole role, String error) {
        AgentHealth h = health.get(role);
        if (h == null) return;

        // Synchronize on h so only ONE thread performs the status transition.
        // Without this, 50 concurrent threads all read failureCount < threshold,
        // all increment past it simultaneously, and all try to open the circuit.
        synchronized (h) {
            h.recordFailure(error);
            int failures = h.getFailureCount();

            if (failures >= failureThreshold
                    && h.getStatus() != AgentHealth.Status.CIRCUIT_OPEN) {
                h.setStatus(AgentHealth.Status.CIRCUIT_OPEN);
                System.out.printf("[SquadOS] Circuit OPEN for %s after %d failures%n",
                    role, failures);
                if (bus != null) {
                    bus.publish(new AgentMessage(role, MessageType.CIRCUIT_OPEN,
                        "Circuit opened after " + failures + " failures"));
                }
            } else if (failures >= failureThreshold / 2
                    && h.getStatus() == AgentHealth.Status.HEALTHY) {
                h.setStatus(AgentHealth.Status.DEGRADED);
            }
        }
    }

    public AgentHealth              getHealth(AgentRole role) { return health.get(role); }
    public Map<AgentRole, AgentHealth> allHealth()            { return health; }

    public void printSummary() {
        System.out.println("[SquadOS] Agent health summary:");
        health.values().forEach(h -> System.out.println("  " + h));
    }
}
