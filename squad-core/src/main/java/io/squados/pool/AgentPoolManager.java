package io.squados.pool;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.context.AgentWrapper;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages a pool of identical AgentWrapper instances for high-throughput load balancing.
 *
 * Created by SquadContext.boot() when an agent class is annotated with @AgentPool.
 * All instances share the same @Agent metadata and LlmOptions.
 *
 * Strategies:
 *   ROUND_ROBIN  — cyclic selection (default, lowest overhead)
 *   LEAST_BUSY   — selects the instance with fewest active calls
 *   RANDOM       — uniform random selection
 */
public class AgentPoolManager {

    private final List<AgentWrapper> pool;
    private final String             strategy;
    private final int                maxQueueSize;

    private final AtomicInteger  roundRobinIdx  = new AtomicInteger(0);
    private final AtomicLong[]   inflightCounts;

    public AgentPoolManager(List<AgentWrapper> pool, String strategy, int maxQueueSize) {
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("AgentPool must have at least one instance");
        }
        this.pool          = List.copyOf(pool);
        this.strategy      = strategy != null ? strategy.toUpperCase() : "ROUND_ROBIN";
        this.maxQueueSize  = maxQueueSize;
        this.inflightCounts = new AtomicLong[pool.size()];
        for (int i = 0; i < pool.size(); i++) inflightCounts[i] = new AtomicLong(0);
    }

    /**
     * Execute a task against the next selected agent in the pool.
     * Tracks inflight count for LEAST_BUSY routing.
     */
    public AgentResponse execute(TaskContext ctx) {
        int idx = selectIndex();
        if (inflightCounts[idx].get() >= maxQueueSize) {
            // Fallback: find any instance below limit
            idx = findAvailable();
        }
        inflightCounts[idx].incrementAndGet();
        try {
            return pool.get(idx).execute(ctx);
        } finally {
            inflightCounts[idx].decrementAndGet();
        }
    }

    /** Select the next instance (public for testing). */
    public AgentWrapper select() {
        return pool.get(selectIndex());
    }

    public List<AgentWrapper> getPool()        { return pool; }
    public int                size()           { return pool.size(); }
    public String             getStrategy()    { return strategy; }
    public long               getInflight(int i) { return inflightCounts[i].get(); }

    // ── Selection strategies ──────────────────────────────────────────

    private int selectIndex() {
        return switch (strategy) {
            case "LEAST_BUSY" -> leastBusyIndex();
            case "RANDOM"     -> ThreadLocalRandom.current().nextInt(pool.size());
            default           -> roundRobinIndex();
        };
    }

    private int roundRobinIndex() {
        // Use Math.abs to guard against negative overflow
        return Math.abs(roundRobinIdx.getAndIncrement() % pool.size());
    }

    private int leastBusyIndex() {
        int  minIdx   = 0;
        long minCount = Long.MAX_VALUE;
        for (int i = 0; i < inflightCounts.length; i++) {
            long count = inflightCounts[i].get();
            if (count < minCount) { minCount = count; minIdx = i; }
        }
        return minIdx;
    }

    private int findAvailable() {
        for (int i = 0; i < inflightCounts.length; i++) {
            if (inflightCounts[i].get() < maxQueueSize) return i;
        }
        // All full — use round-robin as overflow fallback
        return roundRobinIndex();
    }
}
