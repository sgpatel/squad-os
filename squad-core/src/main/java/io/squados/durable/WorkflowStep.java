package io.squados.durable;

import java.time.Instant;

/**
 * A checkpoint record for one completed step in a durable workflow.
 */
public record WorkflowStep(
        int     stepIndex,
        String  stepName,
        String  output,
        boolean success,
        Instant completedAt
) {
    public static WorkflowStep success(int index, String name, String output) {
        return new WorkflowStep(index, name, output, true, Instant.now());
    }

    public static WorkflowStep failure(int index, String name, String errorMsg) {
        return new WorkflowStep(index, name, errorMsg, false, Instant.now());
    }
}
