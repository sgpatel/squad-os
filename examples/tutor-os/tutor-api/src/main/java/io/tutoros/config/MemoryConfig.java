package io.tutoros.config;

import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.MemoryType;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.InProcessMemoryStore;
import io.squados.memory.store.MemoryStore;
import io.squados.memory.store.PgVectorEpisodicStore;
import io.tutoros.memory.TutorOsMemoryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.EnumMap;
import java.util.Map;

/**
 * MemoryConfig — wires the SquadOS memory subsystem for TutorOS.
 *
 * Why this lives in tutor-api (not in squad-spring-boot-starter):
 *   The starter creates the {@link EmbeddingPort} bean when
 *   {@code squad.memory.enabled=true}, but does NOT create
 *   {@link MemoryRouter} or {@link MemoryManager} — it pulls them via
 *   {@code ObjectProvider}, so the application supplies them. We wire
 *   them here so the framework comments ("Phase 2: agents directly,
 *   Phase 3: MemoryInterceptor AOP") are honoured without forking the
 *   framework. A follow-up upstream PR will move this autoconfig into
 *   {@code SquadAutoConfiguration} so every SquadOS user gets it free.
 *
 * Backend selection ({@code squad.memory.episodic-store}):
 *   - {@code in-process} (default) — {@link InProcessMemoryStore} for all
 *     four tiers. Survives within a JVM, lost on restart. Zero-config.
 *   - {@code pgvector} — episodic tier upgraded to
 *     {@link PgVectorEpisodicStore}. Persists across restarts. Requires
 *     a {@link DataSource} bean and a Postgres + pgvector extension.
 *
 * Failure mode: if {@code pgvector} is requested but no {@link DataSource}
 * is on the context, we log and fall back to in-process — dev never breaks
 * because Postgres isn't running locally.
 *
 * Read path: AgentWrapper auto-injects retrieved memories into the
 * system prompt for any agent annotated with {@code @AgentMemory}.
 *
 * Write path: explicit {@code memoryManager.write(...)} call at end of
 * {@code TutoringPipeline} turn (no AOP yet — that's a future framework PR).
 */
@Configuration
@ConditionalOnProperty(prefix = "squad.memory", name = "enabled",
                        havingValue = "true", matchIfMissing = false)
public class MemoryConfig {

    /**
     * MemoryRouter — routes reads/writes to the right store by MemoryType.
     *
     * EPISODIC tier is configurable. WORKING / SEMANTIC / PROCEDURAL
     * stay in-process for v1; flip them to Redis / pgvector / PostgreSQL in
     * follow-up PRs (the SPI is the same — drop the bean here and it routes).
     */
    @Bean
    @ConditionalOnMissingBean
    public MemoryRouter memoryRouter(
            @Value("${squad.memory.episodic-store:in-process}") String episodicBackend,
            @Value("${squad.memory.embedding-dimensions:1536}") int embeddingDimensions,
            ObjectProvider<DataSource> dataSourceProvider) {

        Map<MemoryType, MemoryStore> stores = new EnumMap<>(MemoryType.class);

        // WORKING / SEMANTIC / PROCEDURAL — in-process default.
        // (Working is intentionally ephemeral; semantic/task can move to
        // pgvector later via the same conditional pattern below.)
        stores.put(MemoryType.WORKING,       new InProcessMemoryStore(MemoryType.WORKING));
        stores.put(MemoryType.SEMANTIC,      new InProcessMemoryStore(MemoryType.SEMANTIC));
        stores.put(MemoryType.PROCEDURAL, new InProcessMemoryStore(MemoryType.PROCEDURAL));

        // EPISODIC — the tier that benefits most from persistence.
        MemoryStore episodic = chooseEpisodicStore(
            episodicBackend, embeddingDimensions, dataSourceProvider);
        stores.put(MemoryType.EPISODIC, episodic);

        System.out.printf(
            "[TutorOS] MemoryRouter ready (working=in-process, episodic=%s, semantic=in-process)%n",
            episodic.getClass().getSimpleName().equals("PgVectorEpisodicStore")
                ? "pgvector" : "in-process");
        return new MemoryRouter(stores);
    }

    /**
     * MemoryManager — coordinator pulled into {@link io.squados.context.SquadContext}
     * by the starter's {@code ObjectProvider<MemoryManager>}. Once attached,
     * AgentWrapper's {@code @AgentMemory} read path lights up automatically.
     *
     * The {@link EmbeddingPort} is optional. The starter creates a Spring AI
     * adapter only when an {@code EmbeddingModel} bean exists (i.e. an LLM
     * provider is configured). When it doesn't — local dev without
     * {@code OPENAI_API_KEY}, smoke tests, etc. — we fall back to
     * {@link MockEmbeddingPort}: deterministic 64-dim vectors derived from
     * the text hash, so memory writes/reads still work and similar phrases
     * still cluster. Memory recall quality is obviously lower than with a
     * real model, but the subsystem is alive instead of preventing boot.
     */
    @Bean
    @ConditionalOnMissingBean
    public MemoryManager memoryManager(
            MemoryRouter router,
            ObjectProvider<EmbeddingPort> embedderProvider) {
        EmbeddingPort embedder = embedderProvider.getIfAvailable();
        if (embedder == null) {
            System.out.println(
                "[TutorOS] No EmbeddingPort bean — falling back to MockEmbeddingPort " +
                "(64-dim deterministic). Set OPENAI_API_KEY (or another Spring AI " +
                "embedding provider) for production-quality recall.");
            embedder = new MockEmbeddingPort();
        }
        System.out.println(
            "[TutorOS] MemoryManager wired — subject/topic isolation active " +
            "(read path filters by TurnContext tags)");
        return new TutorOsMemoryManager(router, embedder);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static MemoryStore chooseEpisodicStore(
            String backend, int dimensions, ObjectProvider<DataSource> dsProvider) {

        String b = backend == null ? "in-process" : backend.trim().toLowerCase();

        if ("pgvector".equals(b)) {
            DataSource ds = dsProvider.getIfAvailable();
            if (ds == null) {
                System.err.println(
                    "[TutorOS] squad.memory.episodic-store=pgvector but no DataSource " +
                    "bean is present — falling back to in-process. Add a JDBC driver + " +
                    "spring.datasource.* config to enable persistent memory.");
                return new InProcessMemoryStore(MemoryType.EPISODIC);
            }
            try {
                return new PgVectorEpisodicStore(ds, dimensions);
            } catch (RuntimeException e) {
                System.err.println(
                    "[TutorOS] PgVectorEpisodicStore init failed (" + e.getMessage() +
                    ") — falling back to in-process.");
                return new InProcessMemoryStore(MemoryType.EPISODIC);
            }
        }

        return new InProcessMemoryStore(MemoryType.EPISODIC);
    }
}
