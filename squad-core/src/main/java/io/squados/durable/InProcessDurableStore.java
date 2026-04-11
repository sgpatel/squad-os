package io.squados.durable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process heap-backed DurableStore.
 * Does NOT survive JVM restarts. Use for tests and single-JVM mode.
 */
public class InProcessDurableStore implements DurableStore {

    private final Map<String, WorkflowState> store = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowState state) {
        store.put(state.getWorkflowId(), state);
    }

    @Override
    public Optional<WorkflowState> load(String workflowId) {
        return Optional.ofNullable(store.get(workflowId));
    }

    @Override
    public List<WorkflowState> loadAll() {
        return List.copyOf(store.values());
    }

    @Override
    public void delete(String workflowId) {
        store.remove(workflowId);
    }
}
