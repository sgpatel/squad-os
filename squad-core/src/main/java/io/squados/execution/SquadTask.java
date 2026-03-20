package io.squados.execution;

import io.squados.annotation.AgentRole;
import java.util.*;

/**
 * A task submitted to the squad for parallel execution.
 *
 * Instead of routing to a single lead agent, a SquadTask
 * specifies which roles should work on it simultaneously.
 * All named roles receive the same input and run in parallel
 * via CompletableFuture.allOf().
 *
 * Usage:
 * <pre>
 * SquadTask task = SquadTask.of("Analyse this PR diff:\n" + diff)
 *     .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR)
 *     .withLabel("PR Review");
 *
 * SquadResult result = ctx.execute(task);
 * result.get(AgentRole.ANALYST).content()  // security analysis
 * result.get(AgentRole.CRITIC).content()   // code quality review
 * result.get(AgentRole.EXECUTOR).content() // suggested fixes
 * </pre>
 */
public class SquadTask {

    private final String         input;
    private final List<AgentRole> roles;
    private       String         label;
    private       long           timeoutMs = 60_000; // 60s default

    private SquadTask(String input) {
        this.input = input;
        this.roles = new ArrayList<>();
        this.label = "squad-task";
    }

    /** Create a new SquadTask with the given input */
    public static SquadTask of(String input) {
        return new SquadTask(input);
    }

    /** Assign specific roles to this task (all run in parallel) */
    public SquadTask assignTo(AgentRole... roles) {
        this.roles.addAll(Arrays.asList(roles));
        return this;
    }

    /** Human-readable label for logging and observability */
    public SquadTask withLabel(String label) {
        this.label = label;
        return this;
    }

    /** Per-task timeout in milliseconds (default: 60s) */
    public SquadTask withTimeout(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public String         getInput()     { return input; }
    public List<AgentRole> getRoles()    { return Collections.unmodifiableList(roles); }
    public String         getLabel()     { return label; }
    public long           getTimeoutMs() { return timeoutMs; }

    public boolean hasRoles() { return !roles.isEmpty(); }
}
