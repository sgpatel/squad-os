package io.squados.durable;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting durable workflow state.
 *
 * Implementations:
 *   InProcessDurableStore — heap-backed, for tests and single-JVM
 *   RedisDurableStore     — Redis-backed, survives JVM restarts
 */
public interface DurableStore {

    /** Save or update a workflow state. */
    void save(WorkflowState state);

    /** Load a workflow by ID. Returns empty if not found. */
    Optional<WorkflowState> load(String workflowId);

    /** Load all workflows (for dashboard). */
    List<WorkflowState> loadAll();

    /** Delete a workflow state. */
    void delete(String workflowId);

    /** True if a workflow with this ID exists. */
    default boolean exists(String workflowId) {
        return load(workflowId).isPresent();
    }
}
