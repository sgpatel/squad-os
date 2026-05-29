package io.squados.dashboard.api.model;

import java.time.Instant;
import java.util.List;

/**
 * A durable workflow record — GET /api/v1/workflows.
 */
public record WorkflowItem(
        String       workflowId,
        String       agentName,
        String       state,          // PENDING, RUNNING, PAUSED, COMPLETED, FAILED
        Instant      createdAt,
        Instant      updatedAt,
        long         elapsedMs,
        int          checkpointCount,
        List<String> completedSteps,
        String       lastStep,
        String       error           // null if not failed
) {}
