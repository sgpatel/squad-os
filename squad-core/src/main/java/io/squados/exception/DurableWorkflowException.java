package io.squados.exception;

/**
 * Thrown for errors in @DurableAgent workflow execution —
 * e.g. workflow not found, invalid state transitions, or store failures.
 */
public class DurableWorkflowException extends RuntimeException {

    private final String workflowId;

    public DurableWorkflowException(String workflowId, String message) {
        super(message);
        this.workflowId = workflowId;
    }

    public DurableWorkflowException(String workflowId, String message, Throwable cause) {
        super(message, cause);
        this.workflowId = workflowId;
    }

    public String getWorkflowId() { return workflowId; }
}
