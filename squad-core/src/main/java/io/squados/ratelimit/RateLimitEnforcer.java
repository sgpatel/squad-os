package io.squados.ratelimit;

import io.squados.annotation.AgentRole;
import io.squados.annotation.RateLimit;
import io.squados.exception.RateLimitExceededException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces per-agent @RateLimit constraints.
 *
 * Tracks call counts (per minute sliding window) and token usage (per hour).
 * Thread-safe; uses ConcurrentHashMap + AtomicInteger.
 */
public class RateLimitEnforcer {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS   = 3_600_000L;

    private final Map<String, CallWindow>  callWindows  = new ConcurrentHashMap<>();
    private final Map<String, TokenWindow> tokenWindows = new ConcurrentHashMap<>();

    /**
     * Check and consume rate limit for an agent.
     *
     * @throws RateLimitExceededException if any limit is exceeded
     */
    public void checkAndConsume(AgentRole role, String agentName,
                                RateLimit limit, int estimatedTokens) {
        if (limit == null) return;

        if (limit.callsPerMinute() > 0) {
            CallWindow w = callWindows.computeIfAbsent(agentName, k -> new CallWindow());
            w.tick();
            if (w.countInWindow(MINUTE_MS) > limit.callsPerMinute()) {
                throw new RateLimitExceededException(agentName, "calls",
                    agentName + " exceeded " + limit.callsPerMinute() + " calls/min");
            }
        }

        if (limit.tokensPerHour() > 0) {
            TokenWindow w = tokenWindows.computeIfAbsent(agentName, k -> new TokenWindow());
            w.add(estimatedTokens);
            if (w.sumInWindow(HOUR_MS) > limit.tokensPerHour()) {
                throw new RateLimitExceededException(agentName, "tokens",
                    agentName + " exceeded " + limit.tokensPerHour() + " tokens/hour");
            }
        }
    }

    /** Record actual token usage after a successful LLM call. */
    public void recordTokens(String agentName, int tokens) {
        tokenWindows.computeIfAbsent(agentName, k -> new TokenWindow()).add(tokens);
    }

    public void reset(String agentName) {
        callWindows.remove(agentName);
        tokenWindows.remove(agentName);
    }

    // ── Internal window types ─────────────────────────────────────────

    private static class CallWindow {
        private final java.util.Deque<Long> timestamps = new java.util.ArrayDeque<>();

        synchronized void tick() {
            timestamps.addLast(System.currentTimeMillis());
        }

        synchronized int countInWindow(long windowMs) {
            long cutoff = System.currentTimeMillis() - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            return timestamps.size();
        }
    }

    private static class TokenWindow {
        private final java.util.Deque<long[]> entries = new java.util.ArrayDeque<>(); // [timestamp, tokens]

        synchronized void add(int tokens) {
            entries.addLast(new long[]{System.currentTimeMillis(), tokens});
        }

        synchronized long sumInWindow(long windowMs) {
            long cutoff = System.currentTimeMillis() - windowMs;
            while (!entries.isEmpty() && entries.peekFirst()[0] < cutoff) {
                entries.pollFirst();
            }
            return entries.stream().mapToLong(e -> e[1]).sum();
        }
    }
}
