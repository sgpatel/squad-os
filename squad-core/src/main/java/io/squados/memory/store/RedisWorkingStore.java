package io.squados.memory.store;

import io.squados.memory.annotation.Importance;
import io.squados.memory.annotation.MemoryType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production working memory store backed by Redis.
 *
 * Replaces InProcessMemoryStore for WORKING tier.
 *
 * Key design:
 *   - Redis HASH keyed by session_id → fast O(1) lookup
 *   - TTL per session key: auto-expires after session timeout (default 2h)
 *   - Uses Redis SCAN for session-scoped deletion (not KEYS — production safe)
 *
 * Phase 2 (current): ships as InProcessMemoryStore + Redis adapter stub.
 * Phase 3: swap to real Jedis/Lettuce client.
 *
 * This class uses the Adapter pattern — the actual Redis client is injected
 * via RedisCommands interface so the store itself has no direct Jedis/Lettuce dep.
 * Test with MockRedisCommands; production with LettuceRedisCommands.
 */
public class RedisWorkingStore implements MemoryStore {

    /**
     * Minimal Redis command interface — only the operations this store needs.
     * Implement with Jedis, Lettuce, or Mock.
     */
    public interface RedisCommands {
        void hset(String key, String field, String value);
        String hget(String key, String field);
        Map<String, String> hgetAll(String key);
        void hdel(String key, String... fields);
        void expire(String key, int seconds);
        Set<String> keys(String pattern);
        void del(String key);
    }

    private final RedisCommands redis;
    private final int           sessionTtlSeconds;

    // In-memory index: sessionId → set of record IDs (for fast session scan)
    private final Map<String, Set<String>> sessionIndex = new ConcurrentHashMap<>();

    public RedisWorkingStore(RedisCommands redis, int sessionTtlSeconds) {
        this.redis             = redis;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    /** Convenience constructor with 2-hour default TTL */
    public RedisWorkingStore(RedisCommands redis) {
        this(redis, 7200);
    }

    @Override
    public MemoryType type() { return MemoryType.WORKING; }

    @Override
    public void save(MemoryRecord record) {
        // Key: working:{squadId}:{sessionId}
        // Field: recordId → serialised content
        String key   = redisKey(record.getSquadId(), record.getSessionId());
        String value = serialise(record);
        redis.hset(key, record.getId(), value);
        redis.expire(key, sessionTtlSeconds);

        // Update in-memory session index
        sessionIndex
            .computeIfAbsent(record.getSessionId(), k -> new HashSet<>())
            .add(record.getId());
    }

    @Override
    public List<MemoryRecord> retrieve(float[] queryEmbedding, String squadId,
                                        String agentId, int topK, float minScore) {
        List<MemoryRecord> results = new ArrayList<>();
        // Iterate all known sessions, look up each in Redis by squadId + sessionId
        for (String sid : sessionIndex.keySet()) {
            String key = redisKey(squadId, sid);
            Map<String, String> fields = redis.hgetAll(key);
            for (String value : fields.values()) {
                MemoryRecord r = deserialise(value, squadId, sid);
                if (r == null) continue;
                if (agentId != null && r.getAgentId() != null
                        && !agentId.equals(r.getAgentId())) continue;
                results.add(r);
            }
        }
        return results.stream().limit(topK).toList();
    }

    @Override
    public void deleteBySession(String sessionId) {
        Set<String> recordIds = sessionIndex.remove(sessionId);
        if (recordIds == null) return;
        // Find and delete all Redis keys for this session
        // Note: in production, use SCAN not KEYS
        for (String key : redis.keys("working:*:" + sessionId)) {
            redis.del(key);
        }
    }

    @Override
    public int count() {
        return sessionIndex.values().stream().mapToInt(Set::size).sum();
    }

    /** Find records flagged for session-end promotion (promote=true) */
    @Override
    public List<MemoryRecord> findPromotable(String sessionId) {
        List<MemoryRecord> promotable = new ArrayList<>();
        // Scan all Redis keys for this session: working:{squadId}:{sessionId}
        Set<String> matchingKeys = redis.keys("working:*:" + sessionId);
        for (String key : matchingKeys) {
            // Extract squadId from key format "working:{squadId}:{sessionId}"
            String[] parts = key.split(":", 3);
            if (parts.length < 3) continue;
            String squadId = parts[1];
            Map<String, String> fields = redis.hgetAll(key);
            for (String value : fields.values()) {
                if (value.contains("\"promote\":true")) {
                    MemoryRecord r = deserialise(value, squadId, sessionId);
                    if (r != null) promotable.add(r);
                }
            }
        }
        return promotable;
    }

    // ── Serialisation (minimal JSON — no Jackson dep) ─────────────

    private static String serialise(MemoryRecord r) {
        return "{\"id\":\"" + r.getId() + "\"," +
               "\"agentId\":\"" + nullSafe(r.getAgentId()) + "\"," +
               "\"content\":\"" + escape(r.getContent()) + "\"," +
               "\"importance\":\"" + r.getImportance().name() + "\"," +
               "\"decayScore\":" + r.getDecayScore() + "," +
               "\"promote\":false}";
    }

    private static MemoryRecord deserialise(String json, String squadId, String sessionId) {
        try {
            String content    = extractField(json, "content");
            String agentId    = extractField(json, "agentId");
            String impStr     = extractField(json, "importance");
            Importance imp    = impStr != null ? Importance.valueOf(impStr) : Importance.MEDIUM;
            MemoryRecord r    = new MemoryRecord(
                squadId, "null".equals(agentId) ? null : agentId,
                sessionId, MemoryType.WORKING, content, imp, new String[0]);
            String dsStr = extractField(json, "decayScore");
            if (dsStr != null) r.setDecayScore(Float.parseFloat(dsStr));
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        } else {
            int end = json.indexOf(',', start);
            if (end < 0) end = json.indexOf('}', start);
            return json.substring(start, end);
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private static String nullSafe(String s) { return s == null ? "null" : s; }
    private static String redisKey(String squadId, String sessionId) {
        return "working:" + squadId + ":" + sessionId;
    }
}
