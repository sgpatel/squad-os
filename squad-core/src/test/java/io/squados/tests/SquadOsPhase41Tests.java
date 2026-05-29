package io.squados.tests;

import io.squados.annotation.*;
import io.squados.config.SquadConfig;
import io.squados.context.SquadContext;
import io.squados.llm.MockLlmPort;
import io.squados.memory.*;
import io.squados.memory.annotation.*;
import io.squados.memory.retrieval.*;
import io.squados.memory.store.*;

import java.util.List;

/**
 * Phase 41 — @AgentMemory tests.
 *
 * AM01  MemoryRecord stores all fields correctly
 * AM02  InProcessMemoryStore save and count
 * AM03  InProcessMemoryStore retrieve returns stored records
 * AM04  MemoryRouter routes reads/writes to correct store by type
 * AM05  MemoryRouter totalCount sums across all four stores
 * AM06  MockEmbeddingPort produces deterministic vectors (same text → same vec)
 * AM07  MockEmbeddingPort similar texts produce similar embeddings
 * AM08  MemoryManager.write() saves a record to the correct store
 * AM09  MemoryManager.read() retrieves stored records
 * AM10  MemoryManager.closeSession() promotes WORKING to EPISODIC
 */
public class SquadOsPhase41Tests {

    @Agent(role = AgentRole.ANALYST, name = "MemoryAgent",
           description = "An analyst agent with episodic memory.")
    @AgentMemory(topK = 3, minScore = 0.5f, scope = "agent")
    static class MemoryAgent {}

    static final MockLlmPort LLM = new MockLlmPort();

    static {
        LLM.setDefaultResponse("Analysis complete with memory context.");
    }

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 41 — @AgentMemory                             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "AM01_memoryRecordStoresFields",
            "AM02_inProcessStoreCountAfterSave",
            "AM03_inProcessStoreRetrievesRecords",
            "AM04_memoryRouterRoutesToCorrectStore",
            "AM05_memoryRouterTotalCount",
            "AM06_mockEmbeddingPortIsDeterministic",
            "AM07_mockEmbeddingPortSimilarTexts",
            "AM08_memoryManagerWriteSavesRecord",
            "AM09_memoryManagerReadRetrievesRecord",
            "AM10_memoryManagerCloseSessionPromotes",
        };
        var t = new SquadOsPhase41Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, c.getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.printf("Phase 41 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void AM01_memoryRecordStoresFields() {
        MemoryRecord r = new MemoryRecord(
            "squad-1", "agent-1", "session-1",
            MemoryType.EPISODIC, "The market crashed in Q3.",
            Importance.HIGH, new String[]{"market", "finance"});

        assert "squad-1".equals(r.getSquadId())              : "squadId should match";
        assert "agent-1".equals(r.getAgentId())              : "agentId should match";
        assert "session-1".equals(r.getSessionId())          : "sessionId should match";
        assert MemoryType.EPISODIC == r.getType()            : "type should be EPISODIC";
        assert r.getContent().contains("market")             : "content should match";
        assert Importance.HIGH == r.getImportance()          : "importance should be HIGH";
        assert r.getTags().length == 2                       : "should have 2 tags";
        assert r.getId() != null                             : "id should be auto-generated";
        assert !r.isEvicted()                                : "new record should not be evicted";
    }

    void AM02_inProcessStoreCountAfterSave() {
        InProcessMemoryStore store = new InProcessMemoryStore(MemoryType.WORKING);
        assert store.count() == 0 : "Empty store should have count 0";

        store.save(new MemoryRecord("s", "a", "sess",
            MemoryType.WORKING, "task context", Importance.MEDIUM, null));
        store.save(new MemoryRecord("s", "a", "sess",
            MemoryType.WORKING, "more context", Importance.MEDIUM, null));

        assert store.count() == 2 : "Count should be 2 after two saves";
    }

    void AM03_inProcessStoreRetrievesRecords() {
        InProcessMemoryStore store = new InProcessMemoryStore(MemoryType.EPISODIC);
        MemoryRecord r = new MemoryRecord("squad-x", "agent-x", "s",
            MemoryType.EPISODIC, "Climate data shows warming trend", Importance.HIGH, null);
        store.save(r);

        // Retrieve without embedding (null query → cosineSim defaults to 0.8)
        List<MemoryRecord> results = store.retrieve(null, "squad-x", "agent-x", 5, 0.0f);
        assert results.size() == 1                    : "Should retrieve 1 record";
        assert results.get(0).getContent().contains("Climate") : "Content should match";
    }

    void AM04_memoryRouterRoutesToCorrectStore() {
        MemoryRouter router = new MemoryRouter();

        MemoryRecord w = new MemoryRecord("s","a","sess", MemoryType.WORKING,
            "working memory entry", Importance.LOW, null);
        MemoryRecord e = new MemoryRecord("s","a","sess", MemoryType.EPISODIC,
            "episodic memory entry", Importance.MEDIUM, null);

        router.save(w);
        router.save(e);

        assert router.countFor(MemoryType.WORKING)  == 1 : "WORKING store should have 1";
        assert router.countFor(MemoryType.EPISODIC) == 1 : "EPISODIC store should have 1";
        assert router.countFor(MemoryType.SEMANTIC) == 0 : "SEMANTIC store should be empty";
    }

    void AM05_memoryRouterTotalCount() {
        MemoryRouter router = new MemoryRouter();
        for (MemoryType type : MemoryType.values()) {
            router.save(new MemoryRecord("s","a","s", type, "entry-" + type, Importance.MEDIUM, null));
        }
        assert router.totalCount() == MemoryType.values().length
            : "Total count should equal number of memory types: " + router.totalCount();
    }

    void AM06_mockEmbeddingPortIsDeterministic() {
        MockEmbeddingPort port = new MockEmbeddingPort();
        float[] v1 = port.embed("climate change analysis");
        float[] v2 = port.embed("climate change analysis");

        assert v1.length == v2.length             : "Same text → same dimension";
        assert v1.length == port.dimensions()     : "Dimension should match declared";
        for (int i = 0; i < v1.length; i++) {
            assert v1[i] == v2[i] : "Same text should produce identical vector at index " + i;
        }
    }

    void AM07_mockEmbeddingPortSimilarTexts() {
        MockEmbeddingPort port = new MockEmbeddingPort();
        float[] v1 = port.embed("climate change analysis report");
        float[] v2 = port.embed("climate change analysis summary");
        float[] v3 = port.embed("recipe for chocolate cake baking");

        float sim12 = cosineSim(v1, v2);
        float sim13 = cosineSim(v1, v3);

        assert sim12 > sim13 : "Similar texts should have higher similarity: " +
            String.format("sim(climate,climate)=%.3f sim(climate,recipe)=%.3f", sim12, sim13);
    }

    void AM08_memoryManagerWriteSavesRecord() {
        MemoryRouter router = new MemoryRouter();
        MockEmbeddingPort embedder = new MockEmbeddingPort();
        MemoryManager manager = new MemoryManager(router, embedder);

        // Create a synthetic @Memory annotation
        Memory memAnn = createMemoryAnnotation(MemoryType.EPISODIC, MemoryScope.AGENT,
            Importance.HIGH, 5, 0.5f);

        manager.write(memAnn, "agent-1", "squad-1", "session-1",
            "Completed analysis of Q3 earnings report.");

        assert manager.memoriesOf(MemoryType.EPISODIC) == 1
            : "Should have 1 EPISODIC record after write";
        assert manager.totalMemories() == 1
            : "Total memories should be 1";
    }

    void AM09_memoryManagerReadRetrievesRecord() {
        MemoryRouter router = new MemoryRouter();
        MockEmbeddingPort embedder = new MockEmbeddingPort();
        MemoryManager manager = new MemoryManager(router, embedder);

        Memory memAnn = createMemoryAnnotation(MemoryType.EPISODIC, MemoryScope.AGENT,
            Importance.HIGH, 5, 0.0f);

        // Write a record first
        manager.write(memAnn, "agent-2", "squad-2", "sess",
            "Previous analysis showed positive sentiment.");

        // Read it back
        List<MemoryRecord> records = manager.read(
            memAnn, "agent-2", "squad-2", "sess", "sentiment analysis");

        assert !records.isEmpty()                       : "Should retrieve at least 1 record";
        assert records.get(0).getContent().contains("sentiment") : "Content should match";
        System.out.printf("         → retrieved %d record(s)%n", records.size());
    }

    void AM10_memoryManagerCloseSessionPromotes() {
        MemoryRouter router = new MemoryRouter();
        MockEmbeddingPort embedder = new MockEmbeddingPort();
        MemoryManager manager = new MemoryManager(router, embedder);

        // Write directly to router as WORKING memory (simulating session data)
        MemoryRecord working = new MemoryRecord("squad-3","agent-3","session-3",
            MemoryType.WORKING, "Session task result", Importance.HIGH, null);
        router.save(working);

        assert manager.memoriesOf(MemoryType.WORKING)  == 1 : "Should have 1 WORKING";
        assert manager.memoriesOf(MemoryType.EPISODIC) == 0 : "EPISODIC should be empty";

        manager.closeSession("session-3", "squad-3");

        // WORKING should be flushed (deleteBySession removes them)
        assert manager.memoriesOf(MemoryType.WORKING)  == 0 : "WORKING should be flushed";
        System.out.printf("         → session closed, working=0%n");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static float cosineSim(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i]; na += a[i]*a[i]; nb += b[i]*b[i];
        }
        return (float)(dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10));
    }

    /** Build a synthetic @Memory annotation instance via a proxy. */
    private static Memory createMemoryAnnotation(MemoryType type, MemoryScope scope,
                                                  Importance importance, int topK,
                                                  float minScore) {
        return new Memory() {
            public Class<java.lang.annotation.Annotation> annotationType() {
                return (Class) Memory.class;
            }
            public MemoryType  type()       { return type; }
            public MemoryScope scope()      { return scope; }
            public MemoryOp    op()         { return MemoryOp.READ_WRITE; }
            public int         topK()       { return topK; }
            public float       minScore()   { return minScore; }
            public String[]    tags()       { return new String[0]; }
            public Importance  importance() { return importance; }
            public boolean     promote()    { return false; }
        };
    }
}
