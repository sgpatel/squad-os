package io.squados.cache;

import java.time.Instant;

/**
 * Single entry in the CacheEngine store.
 * Holds the cached response content, its expiry, and an optional embedding
 * (used by SEMANTIC mode for cosine-similarity matching).
 */
public class CacheEntry {

    private final String  content;
    private final Instant expiry;
    private final float[] embedding; // null in EXACT mode

    public CacheEntry(String content, int ttlSeconds, float[] embedding) {
        this.content   = content;
        this.expiry    = Instant.now().plusSeconds(ttlSeconds);
        this.embedding = embedding;
    }

    public boolean isExpired()    { return Instant.now().isAfter(expiry); }
    public String  getContent()   { return content; }
    public float[] getEmbedding() { return embedding; }
}
