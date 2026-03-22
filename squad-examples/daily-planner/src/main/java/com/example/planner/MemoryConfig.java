package com.example.planner;

import io.squados.memory.MemoryManager;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.MemoryStoreFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Memory configuration for the Daily Planner.
 *
 * Two independent flags, combinable:
 *
 *   squados.memory.pgvector.enabled=false  -> in-memory store (no Docker)
 *   squados.memory.pgvector.enabled=true   -> pgvector PostgreSQL store
 *
 *   squados.memory.real-embeddings=false   -> MockEmbeddingPort (keyword similarity)
 *   squados.memory.real-embeddings=true    -> nomic-embed-text (semantic similarity)
 *
 * Recommended combinations:
 *   dev:        pgvector=false, real-embeddings=false  (no infrastructure)
 *   production: pgvector=true,  real-embeddings=true   (full semantic memory)
 */
@Configuration
public class MemoryConfig {

    @Value("${squados.memory.pgvector.enabled:false}")
    private boolean pgvectorEnabled;

    @Value("${squados.memory.real-embeddings:false}")
    private boolean realEmbeddings;

    @Value("${squados.memory.embedding.dimensions:64}")
    private int dimensions;

    // ── Embedding Port ─────────────────────────────────────────────

    /**
     * Mock embeddings — keyword-based similarity, no model needed.
     * Fast, deterministic. Used in dev/test mode.
     */
    @Bean
    @ConditionalOnProperty(name = "squados.memory.real-embeddings",
                           havingValue = "false", matchIfMissing = true)
    public EmbeddingPort mockEmbeddingPort() {
        System.out.println("[Memory] MockEmbeddingPort — keyword similarity (fast, no model)");
        System.out.println("[Memory] To enable semantic: -Dspring.profiles.active=pgvector");
        return new MockEmbeddingPort();
    }

    /**
     * Real semantic embeddings via Ollama nomic-embed-text (768 dims).
     * Requires: ollama pull nomic-embed-text
     *
     * With real embeddings, memory retrieval becomes truly semantic:
     *   "fix login bug" matches "auth service broken" (same concept)
     *   "buy groceries" does NOT match "auth service broken" (different)
     */
    @Bean
    @ConditionalOnProperty(name = "squados.memory.real-embeddings", havingValue = "true")
    public EmbeddingPort realEmbeddingPort(EmbeddingModel embeddingModel) {
        System.out.println("[Memory] Real embeddings: nomic-embed-text via Ollama");
        return new EmbeddingPort() {
            private final int dims;
            {
                float[] probe = embeddingModel.embed("warmup");
                dims = probe.length;
                System.out.printf("[Memory] Embedding dims: %d (nomic-embed-text)%n", dims);
            }
            @Override public float[] embed(String text) {
                return embeddingModel.embed(text);
            }
            @Override public int dimensions() { return dims; }
        };
    }

    // ── Memory Router ───────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "squados.memory.pgvector.enabled",
                           havingValue = "false", matchIfMissing = true)
    public MemoryRouter inProcessMemoryRouter() {
        System.out.println("[Memory] In-memory store — resets on restart");
        return MemoryStoreFactory.inProcess();
    }

    @Bean
    @ConditionalOnProperty(name = "squados.memory.pgvector.enabled", havingValue = "true")
    public MemoryRouter pgVectorMemoryRouter(DataSource dataSource) {
        int dims = realEmbeddings ? 768 : dimensions;
        System.out.printf("[Memory] pgvector store — %d dims%n", dims);
        return MemoryStoreFactory.withPgVector(dataSource, dims);
    }

    // ── Memory Manager ──────────────────────────────────────────────

    @Bean
    public MemoryManager memoryManager(MemoryRouter memoryRouter,
                                       EmbeddingPort embeddingPort) {
        return new MemoryManager(memoryRouter, embeddingPort);
    }
}