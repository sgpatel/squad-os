package io.squados.memory;

import io.squados.memory.annotation.*;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.store.MemoryRecord;

import java.util.List;

/**
 * Central memory coordinator.
 * Called by agents directly (Phase 2) or by MemoryInterceptor AOP (Phase 3).
 *
 * Responsibilities:
 *   1. Embed query text before retrieval
 *   2. Route reads/writes to the correct MemoryStore via MemoryRouter
 *   3. Handle session-end promotion (WORKING -> EPISODIC)
 */
public class MemoryManager {

    private final MemoryRouter  router;
    private final EmbeddingPort embedder;

    public MemoryManager(MemoryRouter router, EmbeddingPort embedder) {
        this.router   = router;
        this.embedder = embedder;
    }

    // ── READ ──────────────────────────────────────────────────────────

    public List<MemoryRecord> read(Memory memAnn,
                                   String agentId, String squadId,
                                   String sessionId, String queryText) {
        float[] vec = (memAnn.type() == MemoryType.WORKING)
            ? null : embedder.embed(queryText);
        return router.retrieve(
            memAnn.type(), vec,
            squadId, resolveAgentId(memAnn, agentId),
            memAnn.topK(), memAnn.minScore());
    }

    // ── WRITE ─────────────────────────────────────────────────────────

    public void write(Memory memAnn,
                      String agentId, String squadId,
                      String sessionId, String content) {
        MemoryRecord record = new MemoryRecord(
            squadId, resolveAgentId(memAnn, agentId), sessionId,
            memAnn.type(), content, memAnn.importance(), memAnn.tags());

        if (memAnn.type() == MemoryType.EPISODIC
         || memAnn.type() == MemoryType.SEMANTIC) {
            record.setEmbedding(embedder.embed(content));
        }
        router.save(record);
    }

    // ── Session lifecycle ─────────────────────────────────────────────

    public void closeSession(String sessionId, String squadId) {
        router.promoteAndFlush(sessionId, squadId);
    }

    // ── Stats ─────────────────────────────────────────────────────────

    public int totalMemories()             { return router.totalCount(); }
    public int memoriesOf(MemoryType type) { return router.countFor(type); }

    // ── Internals ─────────────────────────────────────────────────────

    private static String resolveAgentId(Memory memAnn, String agentId) {
        return (memAnn.scope() == MemoryScope.SQUAD) ? null : agentId;
    }
}
