package io.squados.tests;

import io.squados.agent.TaskContext;
import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.*;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.InProcessMemoryStore;
import io.squados.memory.store.MemoryRecord;

import java.util.List;

/**
 * Phase 2 test suite — Memory layer.
 * M01-M17 cover all memory tiers, decay, embedding, routing, and manager.
 */
public class SquadOsPhase2Tests {

    @Memory(type = MemoryType.EPISODIC, scope = MemoryScope.SQUAD,
            op = MemoryOp.READ_WRITE, topK = 3, minScore = 0.5f,
            tags = {"plan"}, importance = Importance.HIGH, promote = true)
    public void fixtureMethod() {}

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase2Tests();
        String[] tests = {
            "M01_rankingScoreCombinesFactors",
            "M02_dailyDecayRatesAreCorrect",
            "M03_isEvictedWhenBelowThreshold",
            "M04_storeFiltersBySquadId",
            "M05_cosineSimilarityIdenticalVectors",
            "M06_storeFiltersEvictedRecords",
            "M07_routerSavesToCorrectTier",
            "M08_promoteAndFlush",
            "M09_mockEmbedderIsStable",
            "M10_mockEmbedderSimilarTextsCloser",
            "M11_managerWriteStoresRecord",
            "M12_managerReadRetrievesAboveMinScore",
            "M13_squadScopeSpansAgents",
            "M14_agentScopeIsolates",
            "M15_closeSessionPromotes",
            "M16_memoryAnnotationReadable",
            "M17_taskContextCarriesMemories",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║     SquadOS Phase 2 — Memory Layer Tests     ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name);
                passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n", name,
                    c.getClass().getSimpleName(), c.getMessage());
                failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage());
                failed++;
            }
        }

        System.out.printf("%n  Results: %d passed, %d failed out of %d tests%n",
            passed, failed, passed + failed);
        if (failed > 0) {
            System.out.println("\n  PHASE 2 GATE: FAILED\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 2 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v0.2.0 tag can be cut. Phase 3 planning can begin.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void M01_rankingScoreCombinesFactors() {
        MemoryRecord r   = rec("squad1", "a1", MemoryType.EPISODIC, Importance.HIGH);
        MemoryRecord low = rec("squad1", "a1", MemoryType.EPISODIC, Importance.LOW);
        float score    = r.rankingScore(0.9f);
        float lowScore = low.rankingScore(0.9f);
        // HIGH: 0.9 * 1.0 * 1.2 = 1.08
        assertTrue(score > 1.0f,    "HIGH importance boosts above 1.0: " + score);
        assertTrue(lowScore < score,"LOW importance scores less than HIGH");
    }

    void M02_dailyDecayRatesAreCorrect() {
        MemoryRecord high = rec("s", "a", MemoryType.EPISODIC, Importance.HIGH);
        MemoryRecord med  = rec("s", "a", MemoryType.EPISODIC, Importance.MEDIUM);
        MemoryRecord low  = rec("s", "a", MemoryType.EPISODIC, Importance.LOW);
        high.applyDailyDecay(); med.applyDailyDecay(); low.applyDailyDecay();
        assertEquals(0.995f, high.getDecayScore(), 0.0001f, "HIGH decay rate");
        assertEquals(0.985f, med.getDecayScore(),  0.0001f, "MED decay rate");
        assertEquals(0.970f, low.getDecayScore(),  0.0001f, "LOW decay rate");
        assertTrue(low.getDecayScore() < med.getDecayScore(),  "LOW decays faster than MED");
        assertTrue(med.getDecayScore() < high.getDecayScore(), "MED decays faster than HIGH");
    }

    void M03_isEvictedWhenBelowThreshold() {
        MemoryRecord r = rec("s", "a", MemoryType.EPISODIC, Importance.LOW);
        assertFalse(r.isEvicted(), "fresh record not evicted");
        r.setDecayScore(0.09f);
        assertTrue(r.isEvicted(), "below 0.1 is evicted");
        r.setDecayScore(0.1f);
        assertFalse(r.isEvicted(), "exactly 0.1 is NOT evicted");
    }

    void M04_storeFiltersBySquadId() {
        InProcessMemoryStore store = new InProcessMemoryStore(MemoryType.EPISODIC);
        store.save(recWith("squad-A", "agent1", MemoryType.EPISODIC, "alpha mission plan"));
        store.save(recWith("squad-B", "agent1", MemoryType.EPISODIC, "beta mission plan"));
        float[] q = new MockEmbeddingPort().embed("mission plan");
        List<MemoryRecord> results = store.retrieve(q, "squad-A", null, 10, 0.0f);
        assertEquals(1, results.size(), "only squad-A record returned");
        assertEquals("alpha mission plan", results.get(0).getContent(), "correct content");
    }

    void M05_cosineSimilarityIdenticalVectors() {
        float[] v    = {0.6f, 0.8f};
        float[] zero = {0f, 0f};
        assertEquals(1.0f, InProcessMemoryStore.cosineSimilarity(v, v), 0.0001f,
            "identical vectors cosine = 1.0");
        assertEquals(0f, InProcessMemoryStore.cosineSimilarity(zero, v), 0.0001f,
            "zero vector = 0.0");
    }

    void M06_storeFiltersEvictedRecords() {
        InProcessMemoryStore store = new InProcessMemoryStore(MemoryType.EPISODIC);
        MemoryRecord fresh   = recWith("s", "a", MemoryType.EPISODIC, "fresh memory");
        MemoryRecord evicted = recWith("s", "a", MemoryType.EPISODIC, "old memory");
        evicted.setDecayScore(0.05f);
        store.save(fresh); store.save(evicted);
        assertEquals(1, store.count(), "only 1 active record");
        List<MemoryRecord> results = store.retrieve(null, "s", null, 10, 0.0f);
        assertEquals(1, results.size(), "evicted record excluded");
        assertEquals("fresh memory", results.get(0).getContent(), "correct record");
    }

    void M07_routerSavesToCorrectTier() {
        MemoryRouter router = new MemoryRouter();
        router.save(recWith("s", "a", MemoryType.EPISODIC, "episodic fact"));
        router.save(recWith("s", "a", MemoryType.WORKING,  "working scratch"));
        assertEquals(1, router.countFor(MemoryType.EPISODIC), "episodic store has 1");
        assertEquals(1, router.countFor(MemoryType.WORKING),  "working store has 1");
        assertEquals(0, router.countFor(MemoryType.SEMANTIC), "semantic store empty");
        assertEquals(2, router.totalCount(), "total is 2");
    }

    void M08_promoteAndFlush() {
        MemoryRouter router = new MemoryRouter();
        MemoryRecord wm = new MemoryRecord(
            "squad1", "oracle", "sess-42",
            MemoryType.WORKING, "flanked north gate successfully",
            Importance.HIGH, new String[]{"flank"});
        router.save(wm);
        assertEquals(1, router.countFor(MemoryType.WORKING),  "1 working before promote");
        assertEquals(0, router.countFor(MemoryType.EPISODIC), "0 episodic before promote");
        router.promoteAndFlush("sess-42", "squad1");
        assertEquals(0, router.countFor(MemoryType.WORKING),  "0 working after flush");
        assertEquals(1, router.countFor(MemoryType.EPISODIC), "1 episodic after promote");
    }

    void M09_mockEmbedderIsStable() {
        MockEmbeddingPort emb = new MockEmbeddingPort();
        float[] v1 = emb.embed("attack the north gate");
        float[] v2 = emb.embed("attack the north gate");
        assertEquals(emb.dimensions(), v1.length, "vector length matches dimensions()");
        for (int i = 0; i < v1.length; i++)
            assertEquals(v1[i], v2[i], 0.0001f, "same text same vector at dim " + i);
    }

    void M10_mockEmbedderSimilarTextsCloser() {
        MockEmbeddingPort emb = new MockEmbeddingPort();
        float[] base      = emb.embed("attack the north gate with flanking");
        float[] similar   = emb.embed("attack the north gate");
        float[] unrelated = emb.embed("quarterly earnings report review");
        float simScore  = InProcessMemoryStore.cosineSimilarity(base, similar);
        float diffScore = InProcessMemoryStore.cosineSimilarity(base, unrelated);
        assertTrue(simScore > diffScore,
            "similar texts closer (" + simScore + ") than unrelated (" + diffScore + ")");
    }

    void M11_managerWriteStoresRecord() {
        MemoryManager mgr = manager();
        Memory ann = annotation(MemoryType.EPISODIC, MemoryScope.AGENT, MemoryOp.WRITE, Importance.HIGH);
        mgr.write(ann, "oracle", "squad1", "sess-1", "Oracle battle plan v1");
        assertEquals(1, mgr.memoriesOf(MemoryType.EPISODIC), "1 episodic after write");
        assertEquals(1, mgr.totalMemories(), "total is 1");
    }

    void M12_managerReadRetrievesAboveMinScore() {
        MemoryManager mgr = manager();
        Memory wAnn = annotation(MemoryType.EPISODIC, MemoryScope.AGENT, MemoryOp.WRITE, Importance.MEDIUM);
        mgr.write(wAnn, "oracle", "squad1", "sess-1", "attack north gate with funnel");
        mgr.write(wAnn, "oracle", "squad1", "sess-1", "defend south wall with IronVeil");
        Memory rAnn = annotation(MemoryType.EPISODIC, MemoryScope.AGENT, MemoryOp.READ, Importance.MEDIUM);
        List<MemoryRecord> results = mgr.read(rAnn, "oracle", "squad1", "sess-1", "attack north gate");
        assertTrue(results.size() >= 1, "at least 1 result returned");
        assertTrue(results.get(0).getContent().contains("north gate"), "top result about north gate");
    }

    void M13_squadScopeSpansAgents() {
        MemoryManager mgr = manager();
        Memory wAnn = annotation(MemoryType.EPISODIC, MemoryScope.SQUAD, MemoryOp.WRITE, Importance.HIGH);
        mgr.write(wAnn, "oracle",   "squad1", "sess-1", "squad mission capture north base");
        mgr.write(wAnn, "ironveil", "squad1", "sess-1", "squad defensive positions set");
        Memory rAnn = annotation(MemoryType.EPISODIC, MemoryScope.SQUAD, MemoryOp.READ, Importance.HIGH);
        List<MemoryRecord> results = mgr.read(rAnn, "nursebot", "squad1", "sess-1", "squad mission");
        assertTrue(results.size() >= 1, "nursebot can read squad-scoped memories");
    }

    void M14_agentScopeIsolates() {
        MemoryManager mgr = manager();
        Memory ann = annotation(MemoryType.EPISODIC, MemoryScope.AGENT, MemoryOp.WRITE, Importance.MEDIUM);
        mgr.write(ann, "oracle",   "squad1", "sess-1", "oracle private plan");
        mgr.write(ann, "ironveil", "squad1", "sess-1", "ironveil private notes");
        Memory rAnn = annotation(MemoryType.EPISODIC, MemoryScope.AGENT, MemoryOp.READ, Importance.MEDIUM);
        List<MemoryRecord> results = mgr.read(rAnn, "oracle", "squad1", "sess-1", "private plan");
        assertTrue(results.stream().allMatch(r -> "oracle".equals(r.getAgentId())),
            "oracle only sees its own AGENT-scoped memories");
    }

    void M15_closeSessionPromotes() {
        MemoryRouter router = new MemoryRouter();
        MemoryRecord wm = new MemoryRecord(
            "squad1", "blitz", "sess-99", MemoryType.WORKING,
            "west corridor clear at T+45s", Importance.HIGH, new String[]{"flank"});
        router.save(wm);
        assertEquals(1, router.countFor(MemoryType.WORKING),  "1 working before close");
        assertEquals(0, router.countFor(MemoryType.EPISODIC), "0 episodic before close");
        router.promoteAndFlush("sess-99", "squad1");
        assertEquals(0, router.countFor(MemoryType.WORKING),  "0 working after close");
        assertEquals(1, router.countFor(MemoryType.EPISODIC), "1 episodic after promote");
    }

    void M16_memoryAnnotationReadable() throws Exception {
        Memory ann = getClass().getMethod("fixtureMethod").getAnnotation(Memory.class);
        assertNotNull(ann,                                 "@Memory found on fixtureMethod");
        assertEquals(MemoryType.EPISODIC,  ann.type(),    "@Memory.type");
        assertEquals(MemoryScope.SQUAD,    ann.scope(),   "@Memory.scope");
        assertEquals(MemoryOp.READ_WRITE,  ann.op(),      "@Memory.op");
        assertEquals(3,                    ann.topK(),    "@Memory.topK");
        assertEquals(0.5f, ann.minScore(), 0.001f,        "@Memory.minScore");
        assertEquals(Importance.HIGH,      ann.importance(), "@Memory.importance");
        assertTrue(ann.promote(),                         "@Memory.promote");
        assertEquals(1,      ann.tags().length,           "@Memory.tags length");
        assertEquals("plan", ann.tags()[0],               "@Memory.tags[0]");
    }

    void M17_taskContextCarriesMemories() {
        TaskContext ctx = new TaskContext("test task");
        assertFalse(ctx.hasMemories(),        "no memories initially");
        assertEquals(0, ctx.getMemories().size(), "empty list initially");
        MemoryRecord r = rec("s", "a", MemoryType.EPISODIC, Importance.HIGH);
        ctx.addMemory(r);
        assertTrue(ctx.hasMemories(),         "hasMemories after add");
        assertEquals(1, ctx.getMemories().size(), "one memory after add");
        assertEquals(r.getId(), ctx.getMemories().get(0).getId(), "correct record stored");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private MemoryManager manager() {
        return new MemoryManager(new MemoryRouter(), new MockEmbeddingPort());
    }

    private MemoryRecord rec(String sq, String ag, MemoryType t, Importance imp) {
        return new MemoryRecord(sq, ag, "sess-test", t, "test content", imp, new String[0]);
    }

    private MemoryRecord recWith(String sq, String ag, MemoryType t, String content) {
        return new MemoryRecord(sq, ag, "sess-test", t, content, Importance.MEDIUM, new String[0]);
    }

    private Memory annotation(MemoryType type, MemoryScope scope, MemoryOp op, Importance imp) {
        return new Memory() {
            public Class<Memory> annotationType() { return Memory.class; }
            public MemoryType    type()           { return type; }
            public MemoryScope   scope()          { return scope; }
            public MemoryOp      op()             { return op; }
            public int           topK()           { return 3; }
            public float         minScore()       { return 0.0f; }
            public String[]      tags()           { return new String[0]; }
            public Importance    importance()     { return imp; }
            public boolean       promote()        { return true; }
        };
    }

    private static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertEquals(float e, float a, float d, String msg) {
        if (Math.abs(e - a) <= d) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
    }
    private static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
    private static void assertFalse(boolean c, String msg) {
        if (!c) return;
        throw new AssertionError(msg + " — expected false");
    }
}
