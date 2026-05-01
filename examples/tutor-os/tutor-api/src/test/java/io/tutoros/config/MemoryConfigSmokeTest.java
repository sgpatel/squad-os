package io.tutoros.config;

import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.MemoryType;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.store.MemoryRecord;
import io.tutoros.memory.TurnContext;
import io.tutoros.memory.TutorOsMemoryManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryConfigSmokeTest — verifies M1 wiring without booting the full
 * Spring context (no OPENAI key required).
 *
 * Exercises:
 *   1. MemoryConfig.memoryRouter() builds a router with all four tiers.
 *   2. MemoryConfig.memoryManager() returns a usable manager.
 *   3. End-to-end roundtrip: synthetic @Memory annotation → write →
 *      retrieve → record comes back.
 *
 * Uses a fake {@link EmbeddingPort} (deterministic 8-dim vector) so the
 * cosine-similarity path runs without contacting Spring AI / OpenAI.
 */
class MemoryConfigSmokeTest {

    @Test
    void wiresRouterAndManager_andRoundtripsAnEpisodicRecord() {
        // ── Build the config beans directly (no Spring context). ──────
        MemoryConfig cfg = new MemoryConfig();

        // No DataSource → falls back to in-process for episodic.
        ObjectProvider<DataSource> noDs = emptyProvider();

        MemoryRouter router = cfg.memoryRouter("in-process", 8, noDs);
        assertNotNull(router, "router bean must not be null");
        assertEquals(0, router.totalCount(), "router starts empty");
        assertEquals(0, router.countFor(MemoryType.EPISODIC));

        // Tiny deterministic embedder — same vector for every input is
        // fine for this test because we only care that retrieval finds
        // the record we just wrote.
        EmbeddingPort fakeEmbedder = new EmbeddingPort() {
            @Override public float[] embed(String text) {
                return new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f};
            }
            @Override public int dimensions() { return 8; }
        };

        MemoryManager manager = cfg.memoryManager(router, singletonProvider(fakeEmbedder));
        assertNotNull(manager, "manager bean must not be null");

        // ── Write one EPISODIC record via the synthetic annotation. ────
        // We can't import MemoryHelper here (package-private to tutor-core's
        // pipeline package) so we build the annotation inline using the
        // same Proxy trick. This also doubles as a guard that future
        // refactors of MemoryHelper keep the contract stable.
        io.squados.memory.annotation.Memory ann = synthAnnotation();

        String squadId   = "tutor-os";
        String sessionId = "session-" + UUID.randomUUID();
        String content   = "Learner asked: photosynthesis equation. Tutor (DIRECT) replied: 6CO2 + 6H2O → C6H12O6 + 6O2";

        manager.write(ann, /*agentId*/ null, squadId, sessionId, content);

        assertEquals(1, router.countFor(MemoryType.EPISODIC), "one record after write");
        assertEquals(1, manager.totalMemories());

        // ── Retrieve it back. ──────────────────────────────────────────
        List<MemoryRecord> hits = router.retrieve(
            MemoryType.EPISODIC,
            fakeEmbedder.embed("anything"),  // same fake vector → cosine = 1.0
            squadId,
            /*agentId*/ null,
            /*topK*/    5,
            /*minScore*/ 0.0f
        );
        assertFalse(hits.isEmpty(), "retrieval must find the written record");
        assertEquals(content, hits.get(0).getContent());
        assertEquals(squadId, hits.get(0).getSquadId());
        assertEquals(sessionId, hits.get(0).getSessionId());
    }

    /**
     * PR-A behavioural test: cross-subject memories must NOT leak.
     *
     * Writes one record tagged subject:math and one tagged subject:biology
     * into the same store, then sets the per-turn TurnContext to math and
     * reads through the manager. Only the math record should come back —
     * the biology record is filtered out by tag, even though both have
     * the same embedding (perfect cosine match).
     */
    @Test
    void subjectFilterIsolatesMemories() {
        MemoryConfig cfg = new MemoryConfig();
        ObjectProvider<DataSource> noDs = emptyProvider();
        MemoryRouter router = cfg.memoryRouter("in-process", 8, noDs);

        EmbeddingPort fakeEmbedder = new EmbeddingPort() {
            @Override public float[] embed(String text) {
                return new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f};
            }
            @Override public int dimensions() { return 8; }
        };

        MemoryManager manager = cfg.memoryManager(router, singletonProvider(fakeEmbedder));
        assertTrue(manager instanceof TutorOsMemoryManager,
            "MemoryConfig must wire the TutorOsMemoryManager subclass");

        String squadId   = "tutor-os";
        String sessionId = "session-" + UUID.randomUUID();

        // Two records, distinct subjects, identical embeddings.
        manager.write(synthAnnotationWithTags("subject:math"),
            null, squadId, sessionId, "Maths memory: derivative of x^2 is 2x");
        manager.write(synthAnnotationWithTags("subject:biology"),
            null, squadId, sessionId, "Biology memory: photosynthesis splits water");

        assertEquals(2, router.countFor(MemoryType.EPISODIC), "both records written");

        // ── Read with TurnContext bound to MATH. ────────────────────────
        TutorOsMemoryManager.CTX.set(TurnContext.of(squadId, "math", "calculus", sessionId));
        try {
            List<MemoryRecord> hits = manager.read(
                synthAnnotationWithTags("subject:math"),  // op=WRITE/READ doesn't matter for read
                /*agentId*/ null,
                /*squadId*/ null,        // simulates AgentWrapper passing null — TurnContext fills it in
                /*sessionId*/ null,
                /*query*/ "anything");
            assertEquals(1, hits.size(), "subject filter must drop the biology record");
            assertTrue(hits.get(0).getContent().toLowerCase().contains("maths"),
                "the surviving record must be the maths one");
        } finally {
            TutorOsMemoryManager.CTX.remove();
        }

        // ── Read with TurnContext bound to BIOLOGY. ─────────────────────
        TutorOsMemoryManager.CTX.set(TurnContext.of(squadId, "biology", null, sessionId));
        try {
            List<MemoryRecord> hits = manager.read(
                synthAnnotationWithTags("subject:biology"),
                null, null, null, "anything");
            assertEquals(1, hits.size(), "biology turn must see only biology memories");
            assertTrue(hits.get(0).getContent().toLowerCase().contains("biology"));
        } finally {
            TutorOsMemoryManager.CTX.remove();
        }

        // ── Read with NO TurnContext → behaves like vanilla framework. ─
        // squadId=null means the in-process store filter rejects everything,
        // demonstrating exactly the bug we're working around.
        List<MemoryRecord> raw = manager.read(
            synthAnnotationWithTags("subject:math"),
            null, null, null, "anything");
        assertEquals(0, raw.size(),
            "without TurnContext, AgentWrapper's null squadId hits the framework bug");
    }

    // ── helpers ───────────────────────────────────────────────────────

    /** Variant of {@link #synthAnnotation()} that carries explicit tags. */
    private static io.squados.memory.annotation.Memory synthAnnotationWithTags(String... tags) {
        return (io.squados.memory.annotation.Memory) java.lang.reflect.Proxy.newProxyInstance(
            io.squados.memory.annotation.Memory.class.getClassLoader(),
            new Class<?>[]{ io.squados.memory.annotation.Memory.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "type"           -> MemoryType.EPISODIC;
                case "scope"          -> io.squados.memory.annotation.MemoryScope.SQUAD;
                case "op"             -> io.squados.memory.annotation.MemoryOp.WRITE;
                case "topK"           -> 10;
                case "minScore"       -> 0.0f;
                case "tags"           -> tags;
                case "importance"     -> io.squados.memory.annotation.Importance.MEDIUM;
                case "promote"        -> false;
                case "annotationType" -> io.squados.memory.annotation.Memory.class;
                case "toString"       -> "@Memory(test, tags=" + java.util.Arrays.toString(tags) + ")";
                case "hashCode"       -> 0;
                case "equals"         -> proxy == args[0];
                default -> {
                    Object def = method.getDefaultValue();
                    if (def != null) yield def;
                    throw new UnsupportedOperationException(method.getName());
                }
            });
    }

    private static io.squados.memory.annotation.Memory synthAnnotation() {
        return (io.squados.memory.annotation.Memory) java.lang.reflect.Proxy.newProxyInstance(
            io.squados.memory.annotation.Memory.class.getClassLoader(),
            new Class<?>[]{ io.squados.memory.annotation.Memory.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "type"           -> MemoryType.EPISODIC;
                case "scope"          -> io.squados.memory.annotation.MemoryScope.SQUAD;
                case "op"             -> io.squados.memory.annotation.MemoryOp.WRITE;
                case "topK"           -> 3;
                case "minScore"       -> 0.0f;
                case "tags"           -> new String[]{"smoke-test"};
                case "importance"     -> io.squados.memory.annotation.Importance.MEDIUM;
                case "promote"        -> false;
                case "annotationType" -> io.squados.memory.annotation.Memory.class;
                case "toString"       -> "@Memory(test)";
                case "hashCode"       -> 0;
                case "equals"         -> proxy == args[0];
                default -> {
                    Object def = method.getDefaultValue();
                    if (def != null) yield def;
                    throw new UnsupportedOperationException(method.getName());
                }
            });
    }

    /** ObjectProvider that returns the supplied bean. */
    private static <T> ObjectProvider<T> singletonProvider(T bean) {
        return new ObjectProvider<T>() {
            @Override public T getObject() { return bean; }
            @Override public T getObject(Object... args) { return bean; }
            @Override public T getIfAvailable() { return bean; }
            @Override public T getIfUnique() { return bean; }
            @Override public void ifAvailable(Consumer<T> dependencyConsumer) { dependencyConsumer.accept(bean); }
            @Override public void ifUnique(Consumer<T> dependencyConsumer) { dependencyConsumer.accept(bean); }
        };
    }

    /** ObjectProvider that always says "no bean available". */
    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<T>() {
            @Override public T getObject() { throw new IllegalStateException(); }
            @Override public T getObject(Object... args) { throw new IllegalStateException(); }
            @Override public T getIfAvailable() { return null; }
            @Override public T getIfUnique() { return null; }
            @Override public void ifAvailable(Consumer<T> dependencyConsumer) { /* no-op */ }
            @Override public void ifUnique(Consumer<T> dependencyConsumer) { /* no-op */ }
        };
    }
}
