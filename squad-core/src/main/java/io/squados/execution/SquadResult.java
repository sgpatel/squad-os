package io.squados.execution;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The combined result of a parallel squad execution.
 *
 * Contains one AgentResponse per role that was assigned to the task.
 * All responses were produced concurrently — the wall-clock time is
 * roughly the slowest individual agent, not the sum of all agents.
 *
 * Usage:
 * <pre>
 * SquadResult result = ctx.execute(task);
 *
 * // Get a specific agent's response
 * String analysis = result.get(AgentRole.ANALYST).content();
 *
 * // Iterate all responses
 * result.all().forEach(r -> System.out.println(r.agentName() + ": " + r.content()));
 *
 * // Check if everything succeeded
 * if (result.allSucceeded()) { ... }
 *
 * // Wall-clock time (parallel, not sum)
 * System.out.println("Done in " + result.wallClockMs() + "ms");
 * </pre>
 */
public class SquadResult {

    private final Map<AgentRole, AgentResponse> responses;
    private final Instant                       startedAt;
    private final Instant                       completedAt;
    private final String                        taskLabel;

    public SquadResult(Map<AgentRole, AgentResponse> responses,
                       Instant startedAt, String taskLabel) {
        this.responses   = Collections.unmodifiableMap(new LinkedHashMap<>(responses));
        this.startedAt   = startedAt;
        this.completedAt = Instant.now();
        this.taskLabel   = taskLabel;
    }

    // ── Access ────────────────────────────────────────────────────

    /** Get the response from a specific role. Returns null if not present. */
    public AgentResponse get(AgentRole role) {
        return responses.get(role);
    }

    /** Get all responses in execution order */
    public Collection<AgentResponse> all() {
        return responses.values();
    }

    /** All roles that produced a response */
    public Set<AgentRole> roles() {
        return responses.keySet();
    }

    // ── Status ────────────────────────────────────────────────────

    public boolean allSucceeded() {
        return responses.values().stream().allMatch(AgentResponse::isSuccess);
    }

    public boolean anyFailed() {
        return responses.values().stream().anyMatch(r -> !r.isSuccess());
    }

    public List<AgentResponse> failures() {
        return responses.values().stream()
            .filter(r -> !r.isSuccess())
            .collect(Collectors.toList());
    }

    // ── Timing ────────────────────────────────────────────────────

    /** Wall-clock time for the whole parallel execution */
    public long wallClockMs() {
        return Duration.between(startedAt, completedAt).toMillis();
    }

    /** Total tokens used across all agents */
    public int totalTokens() {
        return responses.values().stream().mapToInt(AgentResponse::totalTokens).sum();
    }

    /** Sum of all individual agent latencies (for comparison with wallClockMs) */
    public long sumOfLatenciesMs() {
        return responses.values().stream()
            .mapToLong(r -> r.latency().toMillis())
            .sum();
    }

    /** Speedup ratio: sum of latencies / wall clock (>1 means parallelism helped) */
    public double speedupRatio() {
        long wall = wallClockMs();
        return wall == 0 ? 1.0 : (double) sumOfLatenciesMs() / wall;
    }

    public String getTaskLabel() { return taskLabel; }

    @Override
    public String toString() {
        return "SquadResult{task='" + taskLabel
               + "', agents=" + responses.size()
               + ", wallClock=" + wallClockMs() + "ms"
               + ", speedup=" + String.format("%.1f", speedupRatio()) + "x"
               + ", allSucceeded=" + allSucceeded() + "}";
    }
}
