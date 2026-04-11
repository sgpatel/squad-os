package io.squados.durable;

/**
 * Lifecycle states for a durable workflow.
 */
public enum WorkflowStatus {
    PENDING,    // Created but not yet started
    RUNNING,    // Currently executing a step
    PAUSED,     // Manually paused by ctx.pauseWorkflow()
    COMPLETED,  // All steps finished successfully
    FAILED      // A step failed and failFast=true (or unrecoverable error)
}
