package io.squados.memory.store;

import io.squados.memory.annotation.MemoryType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-process memory store — Phase 2 implementation for all four tiers.
 * Fully correct in behaviour. Does not survive JVM restart.
 * Phase 3 replaces each instance with Redis / pgvector / PostgreSQL.
 */
public class InProcessMemoryStore implements MemoryStore {

    private final MemoryType                type;
    private final Map<String, MemoryRecord> store = new ConcurrentHashMap<>();

    public InProcessMemoryStore(MemoryType type) { this.type = type; }

    @Override public MemoryType type() { return type; }

    @Override
    public void save(MemoryRecord record) { store.put(record.getId(), record); }

    @Override
    public List<MemoryRecord> retrieve(float[] queryEmbedding,
                                       String squadId, String agentId,
                                       int topK, float minScore) {
        return store.values().stream()
            .filter(r -> !r.isEvicted())
            .filter(r -> r.getSquadId().equals(squadId))
            .filter(r -> agentId == null || r.getAgentId() == null
                      || r.getAgentId().equals(agentId))
            .map(r -> {
                float sim = (queryEmbedding != null && r.getEmbedding() != null)
                    ? cosineSimilarity(queryEmbedding, r.getEmbedding())
                    : 0.8f;
                return new ScoredRecord(r, r.rankingScore(sim));
            })
            .filter(sr -> sr.score() >= minScore)
            .sorted(Comparator.comparingDouble(ScoredRecord::score).reversed())
            .limit(topK)
            .map(sr -> { sr.record().recordAccess(); return sr.record(); })
            .collect(Collectors.toList());
    }

    @Override
    public void deleteBySession(String sessionId) {
        store.values().removeIf(r -> sessionId.equals(r.getSessionId()));
    }

    @Override
    public int count() {
        return (int) store.values().stream().filter(r -> !r.isEvicted()).count();
    }

    /** Apply decay pass — called by MemoryDecayService */
    public void applyDecay() { store.values().forEach(MemoryRecord::applyDailyDecay); }

    /** Find WORKING records flagged for session-end promotion */
    public List<MemoryRecord> findPromotable(String sessionId) {
        return store.values().stream()
            .filter(r -> r.getSessionId().equals(sessionId))
            .filter(r -> r.getType() == MemoryType.WORKING)
            .collect(Collectors.toList());
    }

    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0f;
        double dot = 0, nA = 0, nB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i]; nA += a[i]*a[i]; nB += b[i]*b[i];
        }
        return (nA == 0 || nB == 0) ? 0f : (float)(dot / (Math.sqrt(nA)*Math.sqrt(nB)));
    }

    private record ScoredRecord(MemoryRecord record, float score) {}
}
