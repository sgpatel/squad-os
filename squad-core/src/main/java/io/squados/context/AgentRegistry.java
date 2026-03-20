package io.squados.context;

import io.squados.annotation.AgentRole;
import java.util.*;

/**
 * Runtime registry of all active agents in the squad.
 *
 * Analogous to Spring's BeanFactory — holds all managed agent instances,
 * keyed by role. Supports lookup by role, retrieval of the lead agent,
 * and iteration over all registered agents.
 *
 * Thread-safe for read access after boot. Boot itself is single-threaded.
 */
public class AgentRegistry {

    /** Primary store: role → wrapper */
    private final Map<AgentRole, AgentWrapper> byRole = new LinkedHashMap<>();

    /** Ordered list for iteration (registration order) */
    private final List<AgentWrapper> ordered = new ArrayList<>();

    // ── Registration ──────────────────────────────────────────────────

    /**
     * Register an agent wrapper.
     * If a wrapper for the same role already exists, it is replaced
     * and a warning is logged.
     */
    public void register(AgentWrapper wrapper) {
        AgentRole role = wrapper.getRole();
        if (byRole.containsKey(role)) {
            System.out.printf(
                "[SquadOS] WARNING: Duplicate @Agent role %s — "
                + "%s is replacing %s. "
                + "Each role should have at most one agent per squad.%n",
                role,
                wrapper.getName(),
                byRole.get(role).getName()
            );
            ordered.removeIf(w -> w.getRole() == role);
        }
        byRole.put(role, wrapper);
        ordered.add(wrapper);
    }

    // ── Lookup ────────────────────────────────────────────────────────

    /**
     * Get the agent for a specific role.
     *
     * @return The AgentWrapper, or null if no agent is registered for this role.
     */
    public AgentWrapper getByRole(AgentRole role) {
        return byRole.get(role);
    }

    /**
     * Get the lead agent — the one that should handle incoming tasks first.
     *
     * Priority: STRATEGIST > ANALYST > EXECUTOR > first registered.
     * This mirrors Spring's @Primary — one agent is designated the entry point.
     */
    public AgentWrapper getLead() {
        // Prefer strategist-tier roles as the task entry point
        for (AgentRole preferred : List.of(
                AgentRole.STRATEGIST,
                AgentRole.ANALYST,
                AgentRole.EXECUTOR,
                AgentRole.WRITER,
                AgentRole.RESEARCHER
        )) {
            if (byRole.containsKey(preferred)) return byRole.get(preferred);
        }
        // Fall back to first registered agent
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    /**
     * Check if a role is registered.
     */
    public boolean hasRole(AgentRole role) {
        return byRole.containsKey(role);
    }

    /**
     * All registered wrappers in registration order.
     */
    public List<AgentWrapper> all() {
        return Collections.unmodifiableList(ordered);
    }

    /**
     * Number of registered agents.
     */
    public int count() {
        return ordered.size();
    }

    /**
     * True if no agents are registered.
     */
    public boolean isEmpty() {
        return ordered.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AgentRegistry{");
        for (AgentWrapper w : ordered) {
            sb.append("\n  ").append(w.getRole())
              .append(" → ").append(w.getName());
        }
        sb.append("\n}");
        return sb.toString();
    }
}
