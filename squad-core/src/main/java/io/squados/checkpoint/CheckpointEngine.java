package io.squados.checkpoint;

import io.squados.annotation.AgentRole;
import io.squados.durable.DurableStore;
import io.squados.durable.WorkflowState;
import io.squados.durable.WorkflowStep;

import java.util.Optional;

/**
 * Manages method-level checkpoints for @DurableAgent workflows.
 *
 * When an agent method is annotated with @Checkpoint, its return value is
 * persisted to the DurableStore keyed by workflowId + agentName + checkpointName.
 * On JVM restart (or retry), the engine checks for an existing checkpoint and
 * returns the cached result without re-executing the LLM call.
 *
 * Key format: chk:{workflowId}:{agentName}:{checkpointName}
 *
 * Usage in AgentWrapper.execute():
 * <pre>
 *   Optional&lt;String&gt; cached = checkpointEngine.getCheckpoint(workflowId, name, "validation");
 *   if (cached.isPresent()) return AgentResponse.success(role, name, cached.get());
 *   // ... run LLM ...
 *   checkpointEngine.saveCheckpoint(workflowId, name, "validation", result, role);
 * </pre>
 */
public class CheckpointEngine {

    private final DurableStore store;

    public CheckpointEngine(DurableStore store) {
        this.store = store;
    }

    /**
     * Look up a saved checkpoint result.
     *
     * @param workflowId     The durable workflow ID (from TaskContext.getTaskId())
     * @param agentName      The agent's name
     * @param checkpointName The @Checkpoint.name() value
     * @return               Cached output if checkpoint exists and is complete, empty otherwise
     */
    public Optional<String> getCheckpoint(String workflowId,
                                           String agentName,
                                           String checkpointName) {
        if (workflowId == null || workflowId.isBlank()) return Optional.empty();
        return store.load(checkpointKey(workflowId, agentName, checkpointName))
            .filter(WorkflowState::isCompleted)
            .map(WorkflowState::getFinalOutput);
    }

    /**
     * Persist a checkpoint result so the step can be skipped on retry.
     *
     * @param workflowId     The durable workflow ID
     * @param agentName      The agent's name
     * @param checkpointName The @Checkpoint.name() value
     * @param result         The output to checkpoint
     * @param role           The agent's role (for WorkflowState metadata)
     */
    public void saveCheckpoint(String workflowId,
                                String agentName,
                                String checkpointName,
                                String result,
                                AgentRole role) {
        if (workflowId == null || workflowId.isBlank() || result == null) return;
        String key = checkpointKey(workflowId, agentName, checkpointName);
        WorkflowState state = new WorkflowState(key, role, "checkpoint:" + checkpointName);
        state.start();
        state.addStep(WorkflowStep.success(0, agentName, result));
        state.complete(result);
        store.save(state);
    }

    /**
     * Clear a specific checkpoint (e.g. when re-running a workflow from scratch).
     */
    public void clearCheckpoint(String workflowId, String agentName, String checkpointName) {
        if (workflowId == null) return;
        store.delete(checkpointKey(workflowId, agentName, checkpointName));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String checkpointKey(String workflowId,
                                         String agentName,
                                         String checkpointName) {
        return "chk:" + workflowId + ":" + agentName + ":" + checkpointName;
    }
}
