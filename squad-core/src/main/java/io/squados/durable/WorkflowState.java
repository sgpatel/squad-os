package io.squados.durable;

import io.squados.annotation.AgentRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Complete state of a durable workflow — persisted after every step.
 */
public class WorkflowState {

    private final String         workflowId;
    private final AgentRole      role;
    private final String         initialInput;
    private       WorkflowStatus status;
    private final Instant        createdAt;
    private       Instant        updatedAt;
    private final List<WorkflowStep> steps;
    private       String         finalOutput;
    private       String         errorMessage;

    public WorkflowState(String workflowId, AgentRole role, String initialInput) {
        this.workflowId   = workflowId;
        this.role         = role;
        this.initialInput = initialInput;
        this.status       = WorkflowStatus.PENDING;
        this.createdAt    = Instant.now();
        this.updatedAt    = Instant.now();
        this.steps        = new ArrayList<>();
    }

    // ── Mutation ──────────────────────────────────────────────────────

    public void start()  { this.status = WorkflowStatus.RUNNING;  touch(); }
    public void pause()  { this.status = WorkflowStatus.PAUSED;   touch(); }
    public void resume() { this.status = WorkflowStatus.RUNNING;  touch(); }

    public void complete(String output) {
        this.status      = WorkflowStatus.COMPLETED;
        this.finalOutput = output;
        touch();
    }

    public void fail(String error) {
        this.status       = WorkflowStatus.FAILED;
        this.errorMessage = error;
        touch();
    }

    public void addStep(WorkflowStep step) {
        steps.add(step);
        touch();
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public String          getWorkflowId()    { return workflowId; }
    public AgentRole       getRole()          { return role; }
    public String          getInitialInput()  { return initialInput; }
    public WorkflowStatus  getStatus()        { return status; }
    public Instant         getCreatedAt()     { return createdAt; }
    public Instant         getUpdatedAt()     { return updatedAt; }
    public List<WorkflowStep> getSteps()      { return Collections.unmodifiableList(steps); }
    public String          getFinalOutput()   { return finalOutput; }
    public String          getErrorMessage()  { return errorMessage; }
    public int             getNextStepIndex() { return steps.size(); }

    public boolean isCompleted() { return status == WorkflowStatus.COMPLETED; }
    public boolean isFailed()    { return status == WorkflowStatus.FAILED; }
    public boolean isPaused()    { return status == WorkflowStatus.PAUSED; }
    public boolean isRunning()   { return status == WorkflowStatus.RUNNING; }

    private void touch() { this.updatedAt = Instant.now(); }
}
