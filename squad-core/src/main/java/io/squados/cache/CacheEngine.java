package io.squados.cache;

import io.squados.memory.retrieval.EmbeddingPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process semantic response cache for @Cache-annotated agents.
 *
 * Modes:
 *   EXACT    — MD5 hash of (systemPrompt + userMessage). O(1) lookup.
 *   SEMANTIC — Cosine similarity against stored embeddings. Requires EmbeddingPort.
 *              Falls back to EXACT if no EmbeddingPort is wired.
 *
 * Thread-safe. TTL is enforced lazily (entries checked at access time).
 * One CacheEngine instance is shared per AgentWrapper.
 */
public class CacheEngine {

    private final Map<String, CacheEntry> store    = new ConcurrentHashMap<>();
    private final String                  mode;
    private final float                   minScore;
    private       EmbeddingPort           embeddingPort; // optional for SEMANTIC

    public CacheEngine(String mode, float minScore) {
        this.mode     = mode != null ? mode.toUpperCase() : "EXACT";
        this.minScore = minScore;
    }

    public void setEmbeddingPort(EmbeddingPort ep) { this.embeddingPort = ep; }

    /**
     * Look up a cached response for the given input key.
     * Returns empty on miss or expiry.
     */
    public Optional<String> get(String inputKey) {
        evictExpired();
        if ("SEMANTIC".equals(mode) && embeddingPort != null) {
            return findSemantic(inputKey);
        }
        CacheEntry entry = store.get(hash(inputKey));
        return (entry != null && !entry.isExpired())
            ? Optional.of(entry.getContent())
            : Optional.empty();
    }

    /**
     * Store a response in the cache.
     */
    public void put(String inputKey, String content, int ttlSeconds) {
        if ("SEMANTIC".equals(mode) && embeddingPort != null) {
            float[] emb = embeddingPort.embed(inputKey);
            store.put(hash(inputKey), new CacheEntry(content, ttlSeconds, emb));
        } else {
            store.put(hash(inputKey), new CacheEntry(content, ttlSeconds, null));
        }
    }

    /**
     * Build the canonical input key from systemPrompt + userMessage.
     */
    public String buildKey(String systemPrompt, String userMessage) {
        return (systemPrompt != null ? systemPrompt : "")
             + "\u001F"   // ASCII unit separator — unlikely to appear naturally
             + (userMessage != null ? userMessage : "");
    }

    // ── Semantic matching ─────────────────────────────────────────────

    private Optional<String> findSemantic(String inputKey) {
        float[] queryEmbedding = embeddingPort.embed(inputKey);
        String bestContent = null;
        float  bestScore   = -1f;
        for (CacheEntry entry : store.values()) {
            if (entry.isExpired() || entry.getEmbedding() == null) continue;
            float score = cosineSimilarity(queryEmbedding, entry.getEmbedding());
            if (score >= minScore && score > bestScore) {
                bestScore   = score;
                bestContent = entry.getContent();
            }
        }
        return Optional.ofNullable(bestContent);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void evictExpired() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0f;
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0f || normB == 0f) return 0f;
        return dot / ((float) Math.sqrt(normA) * (float) Math.sqrt(normB));
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
