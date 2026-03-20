package io.squados.memory.store;

import io.squados.memory.annotation.MemoryType;
import java.util.List;

/**
 * Common contract for all four memory backends.
 * Phase 2: InProcessMemoryStore for all four.
 * Phase 3: Redis, pgvector, PostgreSQL replacements plug in here.
 */
public interface MemoryStore {
    MemoryType type();
    void save(MemoryRecord record);
    List<MemoryRecord> retrieve(float[] queryEmbedding, String squadId,
                                String agentId, int topK, float minScore);
    void deleteBySession(String sessionId);
    int  count();
}
