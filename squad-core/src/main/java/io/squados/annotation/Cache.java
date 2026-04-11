package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Semantic response caching for this agent.
 *
 * mode      — "EXACT" (hash-based) | "SEMANTIC" (cosine similarity)
 * ttlSeconds — How long to cache a response (default: 300s = 5 minutes)
 * minScore  — Minimum cosine similarity to consider a cache hit (SEMANTIC mode only)
 *
 * EXACT mode: caches based on MD5 hash of (systemPrompt + userMessage).
 * SEMANTIC mode: retrieves cached response if a similar input was seen (cosine ≥ minScore).
 *
 * Cache hits bypass the LLM entirely — zero tokens consumed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cache {
    String mode()       default "EXACT";    // "EXACT" | "SEMANTIC"
    int    ttlSeconds() default 300;
    float  minScore()   default 0.92f;
}
