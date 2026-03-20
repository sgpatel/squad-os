package io.squados.memory.retrieval;

import io.squados.memory.annotation.MemoryType;
import io.squados.memory.store.InProcessMemoryStore;
import io.squados.memory.store.MemoryRecord;
import io.squados.memory.store.MemoryStore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes memory reads and writes to the correct backend by MemoryType.
 * Phase 2: all four tiers use InProcessMemoryStore.
 * Phase 3: swap each entry to Redis / pgvector / PostgreSQL.
 */
public class MemoryRouter {

    private final Map<MemoryType, MemoryStore> stores = new EnumMap<>(MemoryType.class);

    public MemoryRouter() {
        for (MemoryType t : MemoryType.values()) {
            stores.put(t, new InProcessMemoryStore(t));
        }
    }

    public MemoryRouter(Map<MemoryType, MemoryStore> stores) {
        this.stores.putAll(stores);
    }

    public void save(MemoryRecord record) {
        storeFor(record.getType()).save(record);
    }

    public List<MemoryRecord> retrieve(MemoryType type, float[] queryEmbedding,
                                       String squadId, String agentId,
                                       int topK, float minScore) {
        return storeFor(type).retrieve(queryEmbedding, squadId, agentId, topK, minScore);
    }

    /** Session-end: promote flagged WORKING memories to EPISODIC, then flush WORKING. */
    public void promoteAndFlush(String sessionId, String squadId) {
        InProcessMemoryStore ws = (InProcessMemoryStore) storeFor(MemoryType.WORKING);
        for (MemoryRecord wm : ws.findPromotable(sessionId)) {
            MemoryRecord ep = new MemoryRecord(
                wm.getSquadId(), wm.getAgentId(), wm.getSessionId(),
                MemoryType.EPISODIC, wm.getContent(), wm.getImportance(), wm.getTags());
            ep.setEmbedding(wm.getEmbedding());
            storeFor(MemoryType.EPISODIC).save(ep);
        }
        storeFor(MemoryType.WORKING).deleteBySession(sessionId);
    }

    public int totalCount() { return stores.values().stream().mapToInt(MemoryStore::count).sum(); }
    public int countFor(MemoryType type) { return storeFor(type).count(); }

    private MemoryStore storeFor(MemoryType type) {
        MemoryStore s = stores.get(type);
        if (s == null) throw new IllegalStateException("[SquadOS] No MemoryStore for: " + type);
        return s;
    }
}
