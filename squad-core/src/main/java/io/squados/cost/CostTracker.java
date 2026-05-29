package io.squados.cost;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window token-cost tracker shared across all agents.
 *
 * Each call is recorded as a {@link TokenEntry} timestamped at the current time.
 * {@link #spentCents(String, long)} prunes entries older than the window before
 * computing the total — identical to the approach used by {@code RateLimitEnforcer}.
 *
 * Thread-safe: uses a {@link ConcurrentHashMap} of per-agent deques with
 * synchronised deque access.
 */
public class CostTracker {

    private final ConcurrentHashMap<String, Deque<TokenEntry>> windows =
        new ConcurrentHashMap<>();

    // ── Recording ─────────────────────────────────────────────────────

    /**
     * Record a completed LLM call for the given agent.
     *
     * @param agentName        Agent identifier (name, not role).
     * @param model            Model actually used (after any cost-based degradation).
     * @param promptTokens     Prompt tokens consumed.
     * @param completionTokens Completion tokens consumed.
     */
    public void record(String agentName, String model,
                       int promptTokens, int completionTokens) {
        double cents = ModelPricingTable.costCents(model, promptTokens, completionTokens);
        TokenEntry entry = new TokenEntry(System.currentTimeMillis(), cents, model);
        Deque<TokenEntry> deque = windows.computeIfAbsent(agentName, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(entry);
        }
    }

    // ── Query ─────────────────────────────────────────────────────────

    /**
     * Total cost in USD cents spent by {@code agentName} within the last
     * {@code windowMs} milliseconds.
     *
     * @param agentName Agent identifier.
     * @param windowMs  Sliding window size in milliseconds (e.g. 3_600_000 for 1 hour).
     * @return Sum of costs within the window; 0.0 if no calls recorded.
     */
    public double spentCents(String agentName, long windowMs) {
        Deque<TokenEntry> deque = windows.get(agentName);
        if (deque == null) return 0.0;
        long cutoff = System.currentTimeMillis() - windowMs;
        double total = 0.0;
        synchronized (deque) {
            // Prune stale entries from the front
            while (!deque.isEmpty() && deque.peekFirst().timestampMs() < cutoff) {
                deque.pollFirst();
            }
            for (TokenEntry entry : deque) {
                total += entry.costCents();
            }
        }
        return total;
    }

    /**
     * Convenience overload using a 1-hour window (3,600,000 ms).
     */
    public double spentCentsLastHour(String agentName) {
        return spentCents(agentName, 3_600_000L);
    }

    /**
     * Reset all recorded entries for the given agent.
     */
    public void reset(String agentName) {
        Deque<TokenEntry> deque = windows.get(agentName);
        if (deque != null) {
            synchronized (deque) {
                deque.clear();
            }
        }
    }

    /**
     * Reset entries for all agents.
     */
    public void resetAll() {
        windows.values().forEach(deque -> {
            synchronized (deque) { deque.clear(); }
        });
    }

    // ── Internal record ───────────────────────────────────────────────

    /**
     * A single cost entry in the sliding window.
     */
    public record TokenEntry(long timestampMs, double costCents, String model) {}
}
