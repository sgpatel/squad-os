package io.squados.health;

import io.squados.annotation.AgentRole;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-agent circuit breaker.
 *
 * States:
 *   HEALTHY     — agent is operating normally
 *   DEGRADED    — failure threshold approaching, warnings logged
 *   CIRCUIT_OPEN— failures exceeded threshold; bus event fired, fallback activated
 *
 * Reset: circuit closes automatically after successThreshold successes.
 *
 * Analogous to Resilience4j CircuitBreaker, but applied per AgentRole
 * rather than per HTTP endpoint.
 */
public class AgentCircuitBreaker {

    private final int              failureThreshold;
    private final int              successThreshold;
    private final AgentMessageBus  bus;
    private final Map<AgentRole, AgentHealth> health = new ConcurrentHashMap<>();

    public AgentCircuitBreaker(AgentMessageBus bus) {
        this(3, 2, bus); // defaults: open after 3 failures, reset after 2 successes
    }

    public AgentCircuitBreaker(int failureThreshold, int successThreshold,
                               AgentMessageBus bus) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.bus              = bus;
    }

    /** Register an agent for health tracking */
    public void register(AgentRole role, String name) {
        health.put(role, new AgentHealth(role, name));
    }

    /** Call before executing an agent task — returns false if circuit is open */
    public boolean allowCall(AgentRole role) {
        AgentHealth h = health.get(role);
        return h == null || h.getStatus() != AgentHealth.Status.CIRCUIT_OPEN;
    }

    /** Record a successful agent execution */
    public void onSuccess(AgentRole role, long latencyMs) {
        AgentHealth h = health.get(role);
        if (h == null) return;
        h.recordSuccess(latencyMs);
        if (h.getStatus() == AgentHealth.Status.CIRCUIT_OPEN
            && h.getFailureCount() == 0) {
            h.setStatus(AgentHealth.Status.HEALTHY);
            System.out.printf("[SquadOS] Circuit CLOSED for %s — recovering%n", role);
        }
    }

    /** Record a failed agent execution */
    public void onFailure(AgentRole role, String error) {
        AgentHealth h = health.get(role);
        if (h == null) return;
        h.recordFailure(error);

        if (h.getFailureCount() >= failureThreshold
            && h.getStatus() != AgentHealth.Status.CIRCUIT_OPEN) {
            h.setStatus(AgentHealth.Status.CIRCUIT_OPEN);
            System.out.printf("[SquadOS] Circuit OPEN for %s after %d failures%n",
                role, h.getFailureCount());
            // Publish CIRCUIT_OPEN event so SquadContext can activate fallback
            if (bus != null) {
                bus.publish(new AgentMessage(role, MessageType.CIRCUIT_OPEN,
                    "Circuit opened after " + h.getFailureCount() + " failures"));
            }
        } else if (h.getFailureCount() >= failureThreshold / 2) {
            h.setStatus(AgentHealth.Status.DEGRADED);
        }
    }

    public AgentHealth getHealth(AgentRole role) { return health.get(role); }
    public Map<AgentRole, AgentHealth> allHealth()  { return health; }

    public void printSummary() {
        System.out.println("[SquadOS] Agent health summary:");
        health.values().forEach(h -> System.out.println("  " + h));
    }
}
