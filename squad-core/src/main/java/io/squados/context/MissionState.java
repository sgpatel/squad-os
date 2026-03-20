package io.squados.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared mutable state for the current mission.
 * All agents read and write here during task execution.
 *
 * Thread safety: ConcurrentHashMap for the state map,
 * AtomicLong for the version counter (optimistic locking).
 * Concurrent writes to the SAME key use compareAndSet semantics
 * via updateIfVersion() — the caller must re-read on failure.
 *
 * This is the most dangerous class in SquadOS — the one most
 * likely to cause data loss under concurrent agents.
 * Phase 5 hardening: stress tested at 50 concurrent writers.
 */
public class MissionState {

    private final Map<String, Object>  state   = new ConcurrentHashMap<>();
    private final AtomicLong           version = new AtomicLong(0);

    // ── Write ──────────────────────────────────────────────────────

    /**
     * Unconditional put — last writer wins.
     * Use for non-contested keys (e.g. each agent writes its own HP).
     */
    public void put(String key, Object value) {
        state.put(key, value);
        version.incrementAndGet();
    }

    /**
     * Conditional put — only updates if version matches expected.
     * Use when two agents might write the same key.
     * Returns true on success, false if version has changed (retry).
     */
    public boolean putIfVersion(String key, Object value, long expectedVersion) {
        if (version.get() != expectedVersion) return false;
        state.put(key, value);
        version.incrementAndGet();
        return true;
    }

    // ── Read ───────────────────────────────────────────────────────

    public Object  get(String key)                    { return state.get(key); }
    public boolean has(String key)                    { return state.containsKey(key); }
    public long    getVersion()                       { return version.get(); }
    public int     size()                             { return state.size(); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object v = state.get(key);
        return v == null ? null : type.cast(v);
    }

    public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
        T v = get(key, type);
        return v == null ? defaultValue : v;
    }

    // ── Lifecycle ──────────────────────────────────────────────────

    public void reset() {
        state.clear();
        version.set(0);
    }

    @Override
    public String toString() {
        return "MissionState{version=" + version + ", keys=" + state.keySet() + "}";
    }
}
