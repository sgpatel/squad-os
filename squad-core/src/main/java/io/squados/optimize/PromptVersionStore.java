package io.squados.optimize;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process store for prompt versions produced by {@link PromptOptimizerEngine}.
 *
 * Keyed by agent name. Each entry maintains the full version history so
 * callers can roll back to any previous version.
 */
public class PromptVersionStore {

    private final Map<String, List<PromptVersion>> history = new ConcurrentHashMap<>();

    /** Save a new version for the given agent. */
    public void save(String agentName, PromptVersion version) {
        history.computeIfAbsent(agentName, k -> new ArrayList<>()).add(version);
    }

    /** Latest (highest-score) version for the agent, or empty. */
    public Optional<PromptVersion> best(String agentName) {
        List<PromptVersion> versions = history.getOrDefault(agentName, List.of());
        return versions.stream().max(Comparator.comparingDouble(PromptVersion::score));
    }

    /** All versions for the agent in iteration order. */
    public List<PromptVersion> all(String agentName) {
        return Collections.unmodifiableList(
            history.getOrDefault(agentName, List.of()));
    }

    /** Most recent version saved (regardless of score). */
    public Optional<PromptVersion> latest(String agentName) {
        List<PromptVersion> versions = history.getOrDefault(agentName, List.of());
        return versions.isEmpty() ? Optional.empty()
            : Optional.of(versions.get(versions.size() - 1));
    }

    /** Clear history for an agent (start fresh optimization). */
    public void clear(String agentName) { history.remove(agentName); }

    public boolean hasHistory(String agentName) {
        return history.containsKey(agentName) && !history.get(agentName).isEmpty();
    }
}
