package io.squados.durable;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;
import io.squados.agent.TaskContext;
import io.squados.exception.DurableWorkflowException;

import java.util.Optional;

/**
 * Executes durable workflows — checkpoints WorkflowState after every step.
 *
 * Behaviour:
 *   - COMPLETED workflows return cached result immediately (idempotent)
 *   - PAUSED workflows throw DurableWorkflowException
 *   - Resumes from the last completed step on retry
 *   - Records each step as WorkflowStep in the WorkflowState
 */
public class DurableEngine {

    private final DurableStore    store;
    private final AgentRegistry   registry;

    public DurableEngine(DurableStore store, AgentRegistry registry) {
        this.store    = store;
        this.registry = registry;
    }

    /**
     * Submit a durable workflow. Idempotent — same workflowId returns cached result.
     */
    public AgentResponse submit(AgentRole role, String workflowId, String input) {
        // Load or create state
        WorkflowState state = store.load(workflowId)
            .orElseGet(() -> {
                WorkflowState s = new WorkflowState(workflowId, role, input);
                store.save(s);
                return s;
            });

        // Idempotent: already completed
        if (state.isCompleted()) {
            return AgentResponse.success(role, workflowId, state.getFinalOutput());
        }

        // Paused: cannot proceed
        if (state.isPaused()) {
            throw new DurableWorkflowException(workflowId,
                "Workflow '" + workflowId + "' is PAUSED. Call resumeWorkflow() first.");
        }

        state.start();
        store.save(state);

        // Find the agent for this role
        AgentWrapper wrapper = registry.getByRole(role);
        if (wrapper == null) {
            state.fail("No agent registered for role: " + role);
            store.save(state);
            throw new DurableWorkflowException(workflowId,
                "No agent registered for role: " + role);
        }

        // Execute the step (we checkpoint the workflow as a single-step durable call)
        int stepIndex = state.getNextStepIndex();
        try {
            TaskContext ctx = new TaskContext(input, workflowId, null);
            AgentResponse response = wrapper.execute(ctx);

            WorkflowStep step = response.isSuccess()
                ? WorkflowStep.success(stepIndex, wrapper.getName(), response.content())
                : WorkflowStep.failure(stepIndex, wrapper.getName(),
                    response.errorMessage() != null ? response.errorMessage() : "Unknown error");

            state.addStep(step);

            if (response.isSuccess()) {
                state.complete(response.content());
            } else {
                state.fail(response.errorMessage());
            }
            store.save(state);
            return response;

        } catch (Exception e) {
            WorkflowStep step = WorkflowStep.failure(stepIndex, wrapper.getName(), e.getMessage());
            state.addStep(step);
            state.fail(e.getMessage());
            store.save(state);
            throw new DurableWorkflowException(workflowId,
                "Workflow step failed: " + e.getMessage(), e);
        }
    }

    public void pause(String workflowId) {
        WorkflowState state = loadOrThrow(workflowId);
        state.pause();
        store.save(state);
    }

    public void resume(String workflowId) {
        WorkflowState state = loadOrThrow(workflowId);
        if (!state.isPaused()) {
            throw new DurableWorkflowException(workflowId,
                "Workflow '" + workflowId + "' is not paused (status: " + state.getStatus() + ")");
        }
        state.resume();
        store.save(state);
    }

    public Optional<WorkflowState> getState(String workflowId) {
        return store.load(workflowId);
    }

    private WorkflowState loadOrThrow(String workflowId) {
        return store.load(workflowId).orElseThrow(() ->
            new DurableWorkflowException(workflowId,
                "Workflow not found: " + workflowId));
    }
}
