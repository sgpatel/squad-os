package io.squados.context;

import io.squados.annotation.AgentRole;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks and enforces token usage per agent per mission.
 * Hard cap: agent is blocked from further calls when budget exhausted.
 * Soft cap: warning logged at 80% usage.
 */
public class TokenBudget {

    private final int defaultBudget;
    private final Map<AgentRole, AtomicInteger> used  = new ConcurrentHashMap<>();
    private final Map<AgentRole, Integer>       caps  = new ConcurrentHashMap<>();

    public TokenBudget(int defaultBudget) {
        this.defaultBudget = defaultBudget;
    }

    public void setCap(AgentRole role, int cap) {
        caps.put(role, cap);
        used.putIfAbsent(role, new AtomicInteger(0));
    }

    /**
     * Record token usage after an LLM call.
     * @return false if budget is now exhausted (hard cap hit)
     */
    public boolean record(AgentRole role, int tokens) {
        used.computeIfAbsent(role, r -> new AtomicInteger(0));
        int total = used.get(role).addAndGet(tokens);
        int cap   = caps.getOrDefault(role, defaultBudget);

        if (total >= cap) {
            System.out.printf("[SquadOS] TokenBudget: %s exhausted (%d/%d tokens)%n",
                role, total, cap);
            return false;
        }
        if (total >= cap * 0.8) {
            System.out.printf("[SquadOS] TokenBudget: %s at %.0f%% (%d/%d)%n",
                role, (total * 100.0 / cap), total, cap);
        }
        return true;
    }

    public boolean hasRemaining(AgentRole role) {
        int u = used.getOrDefault(role, new AtomicInteger(0)).get();
        int c = caps.getOrDefault(role, defaultBudget);
        return u < c;
    }

    public int usedBy(AgentRole role) {
        return used.getOrDefault(role, new AtomicInteger(0)).get();
    }

    public int capFor(AgentRole role) {
        return caps.getOrDefault(role, defaultBudget);
    }

    public int remainingFor(AgentRole role) {
        return Math.max(0, capFor(role) - usedBy(role));
    }

    public void reset() {
        used.values().forEach(a -> a.set(0));
    }
}
