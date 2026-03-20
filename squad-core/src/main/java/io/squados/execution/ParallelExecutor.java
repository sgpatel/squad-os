package io.squados.execution;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.AgentRole;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes multiple agents simultaneously using CompletableFuture.allOf().
 *
 * This is the core of v1.1 — instead of agents running one after another,
 * ParallelExecutor fires all assigned agents at the same time and waits
 * for all of them to finish.
 *
 * Wall-clock time = slowest agent (not sum of all agents).
 * For the Daily Planner: 3 agents × 5s each → ~5s total, not 15s.
 *
 * Thread safety: each agent gets its own TaskContext so there is no
 * shared mutable state between parallel executions.
 *
 * Thread pool: uses a dedicated cached thread pool scoped to this executor.
 * Each agent call occupies one thread for the duration of the LLM call.
 * For squads with many agents, consider bounding the pool size.
 */
public class ParallelExecutor {

    private final AgentRegistry       registry;
    private final ExecutorService     pool;

    public ParallelExecutor(AgentRegistry registry) {
        this.registry = registry;
        // Virtual threads (Java 21) — one per LLM call, very lightweight
        this.pool = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Execute a SquadTask by running all assigned roles in parallel.
     *
     * @param task   The task containing input + list of roles to assign
     * @return       SquadResult with one AgentResponse per role
     */
    public SquadResult execute(SquadTask task) {
        Instant start  = Instant.now();
        List<AgentRole> roles = resolveRoles(task);

        if (roles.isEmpty()) {
            throw new IllegalStateException(
                "[SquadOS] SquadTask '" + task.getLabel()
                + "' has no assignable roles. "
                + "Call .assignTo(AgentRole...) or ensure the squad has registered agents."
            );
        }

        // Launch all agents concurrently
        Map<AgentRole, CompletableFuture<AgentResponse>> futures = new LinkedHashMap<>();
        for (AgentRole role : roles) {
            AgentWrapper wrapper = registry.getByRole(role);
            if (wrapper == null) {
                System.out.printf("[SquadOS] WARNING: No agent for role %s — skipping.%n", role);
                continue;
            }
            // Each agent gets its own TaskContext — no shared mutable state
            TaskContext ctx = new TaskContext(
                task.getInput(), "parallel-" + role.name().toLowerCase(), "default"
            );
            futures.put(role, CompletableFuture
                .supplyAsync(() -> wrapper.execute(ctx), pool)
                .orTimeout(task.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> AgentResponse.failure(
                    role, wrapper.getName(),
                    "Timeout or error: " + ex.getMessage(), start
                ))
            );
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

        // Collect results in role order
        Map<AgentRole, AgentResponse> results = new LinkedHashMap<>();
        futures.forEach((role, future) -> {
            try {
                results.put(role, future.get());
            } catch (Exception e) {
                results.put(role, AgentResponse.failure(role, role.name(),
                    "Result collection failed: " + e.getMessage(), start));
            }
        });

        SquadResult result = new SquadResult(results, start, task.getLabel());

        // Log the parallel execution summary
        System.out.printf(
            "[SquadOS] %s complete — %d agents, %dms wall-clock, %.1fx speedup%n",
            task.getLabel(), results.size(),
            result.wallClockMs(), result.speedupRatio()
        );

        return result;
    }

    /** Resolve which roles to run — use task's explicit list, else all registered agents */
    private List<AgentRole> resolveRoles(SquadTask task) {
        if (task.hasRoles()) return task.getRoles();
        // No explicit roles — assign all registered agents
        return registry.all().stream()
            .map(AgentWrapper::getRole)
            .toList();
    }

    public void shutdown() {
        pool.shutdown();
    }
}
