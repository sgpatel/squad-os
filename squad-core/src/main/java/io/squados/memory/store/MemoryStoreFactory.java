package io.squados.memory.store;

import io.squados.memory.annotation.MemoryType;
import io.squados.memory.retrieval.MemoryRouter;

import javax.sql.DataSource;
import java.util.EnumMap;
import java.util.Map;

/**
 * Wires the correct MemoryStore implementation per environment.
 *
 * Development (no infrastructure):
 *   All four tiers → InProcessMemoryStore (in-RAM, fast, resets on restart)
 *
 * Production (PostgreSQL + Redis available):
 *   WORKING   → RedisWorkingStore  (2h TTL, auto-expires)
 *   EPISODIC  → PgVectorEpisodicStore (persistent, decay, HNSW index)
 *   SEMANTIC  → InProcessMemoryStore (Phase 3: PostgreSQL KV store)
 *   PROCEDURAL→ InProcessMemoryStore (Phase 3: PostgreSQL confidence table)
 *
 * Usage in Spring Boot app:
 * <pre>
 * // In your @Configuration class:
 *
 * // Development (no infra needed)
 * {@literal @}Bean
 * public MemoryRouter memoryRouter() {
 *     return MemoryStoreFactory.inProcess();
 * }
 *
 * // Production (with Postgres + Redis)
 * {@literal @}Bean
 * public MemoryRouter memoryRouter(DataSource ds,
 *         RedisWorkingStore.RedisCommands redis) {
 *     return MemoryStoreFactory.production(ds, redis, 64); // 64 = MockEmbeddingPort dims
 * }
 * </pre>
 */
public class MemoryStoreFactory {

    private MemoryStoreFactory() {}

    /**
     * All four tiers backed by in-process stores.
     * No infrastructure required. Data lost on restart.
     * Default for tests and local development.
     */
    public static MemoryRouter inProcess() {
        return new MemoryRouter(); // default constructor already does this
    }

    /**
     * Production wiring:
     *   WORKING  → RedisWorkingStore
     *   EPISODIC → PgVectorEpisodicStore
     *   SEMANTIC + PROCEDURAL → InProcessMemoryStore (upgraded in Phase 3)
     *
     * @param dataSource  PostgreSQL DataSource with pgvector extension enabled
     * @param redis       Redis commands implementation (Jedis or Lettuce)
     * @param dimensions  Embedding vector dimensions (64 for mock, 1536 for OpenAI)
     */
    public static MemoryRouter production(DataSource dataSource,
                                          RedisWorkingStore.RedisCommands redis,
                                          int dimensions) {
        Map<MemoryType, MemoryStore> stores = new EnumMap<>(MemoryType.class);
        stores.put(MemoryType.WORKING,    new RedisWorkingStore(redis));
        stores.put(MemoryType.EPISODIC,   new PgVectorEpisodicStore(dataSource, dimensions));
        stores.put(MemoryType.SEMANTIC,   new InProcessMemoryStore(MemoryType.SEMANTIC));
        stores.put(MemoryType.PROCEDURAL, new InProcessMemoryStore(MemoryType.PROCEDURAL));
        return new MemoryRouter(stores);
    }

    /**
     * Hybrid wiring — pgvector for episodic, in-process for rest.
     * Use when you have PostgreSQL but not Redis.
     */
    public static MemoryRouter withPgVector(DataSource dataSource, int dimensions) {
        Map<MemoryType, MemoryStore> stores = new EnumMap<>(MemoryType.class);
        stores.put(MemoryType.WORKING,    new InProcessMemoryStore(MemoryType.WORKING));
        stores.put(MemoryType.EPISODIC,   new PgVectorEpisodicStore(dataSource, dimensions));
        stores.put(MemoryType.SEMANTIC,   new InProcessMemoryStore(MemoryType.SEMANTIC));
        stores.put(MemoryType.PROCEDURAL, new InProcessMemoryStore(MemoryType.PROCEDURAL));
        return new MemoryRouter(stores);
    }
}
