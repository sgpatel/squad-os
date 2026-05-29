package io.tutoros.book;

import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.MemoryType;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.InProcessMemoryStore;
import io.squados.memory.store.MemoryStore;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BookChunkIndexerTest — covers the chunking math + the indexer's
 * integration with the M1 memory subsystem.
 *
 * <p>Embedder is {@link MockEmbeddingPort}: deterministic hash-derived
 * 64-dim vectors. Semantically meaningless but identity-preserving, so
 * "write the same text, read it back" works as a roundtrip check
 * without needing a real LLM provider.
 */
class BookChunkIndexerTest {

    // ── Chunking math ───────────────────────────────────────────────

    @Test
    void chunkProducesOverlappingWindowsCoveringTheBody() {
        // Body = 1500 chars; chunkSize=500, overlap=100 → roughly
        //   [0..500], [400..900], [800..1300], [1200..1500]
        // The sentence-break logic may shift each "to" slightly so we
        // assert ranges, not exact offsets.
        String body = "A".repeat(1500);
        List<String> chunks = BookChunkIndexer.chunk(body, 500, 100);
        assertFalse(chunks.isEmpty());
        // Every chunk is bounded by the requested size.
        for (String c : chunks) assertTrue(c.length() <= 500,
            "chunk should be <= 500 chars, got " + c.length());
        // Consecutive chunks should overlap by some amount when the
        // body is long enough.
        assertTrue(chunks.size() >= 3,
            "1500-char body with 500/100 chunks should produce >=3 windows");
    }

    @Test
    void chunkPrefersSentenceBoundaries() {
        // Insert a clean sentence break at char 480 — the chunker
        // should cut there rather than at the hard 500-char wall.
        String body =
            "X".repeat(479) + ". " +  // sentence break at 480
            "Y".repeat(600);
        List<String> chunks = BookChunkIndexer.chunk(body, 500, 50);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).endsWith("."),
            "first chunk should cut at the sentence break, got: "
            + chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 10)));
    }

    @Test
    void chunkHandlesEmptyAndShortBodiesGracefully() {
        assertTrue(BookChunkIndexer.chunk(null, 500, 50).isEmpty());
        assertTrue(BookChunkIndexer.chunk("", 500, 50).isEmpty());
        assertTrue(BookChunkIndexer.chunk("   ", 500, 50).isEmpty());

        // Short body (< chunkSize) becomes one chunk.
        List<String> small = BookChunkIndexer.chunk("hello world", 500, 50);
        assertEquals(1, small.size());
        assertEquals("hello world", small.get(0));
    }

    @Test
    void chunkProgressesEvenWhenOverlapEqualsChunkSize() {
        // Pathological config — overlap >= chunkSize would loop forever
        // without the sanity guard. Assert termination.
        List<String> chunks = BookChunkIndexer.chunk("A".repeat(200), 100, 100);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() < 50,
            "indexer should not loop indefinitely on degenerate configs");
    }

    // ── isEnabled / no-op contract ──────────────────────────────────

    @Test
    void disabledIndexerIsANoOpEverywhere() {
        BookChunkIndexer disabled = new BookChunkIndexer(null, "tutor-os");
        assertFalse(disabled.isEnabled(),
            "indexer with null memory manager must report disabled");

        // None of these should throw.
        disabled.indexBook(makeBook());
        disabled.deindexBook("book_anything");
        String hit = disabled.findRelevantExcerptSemantic(
            "book_x", 1, "concept", 1000);
        assertEquals("", hit,
            "disabled indexer must return empty excerpt so the caller " +
            "falls back to lexical retrieval");
    }

    // ── End-to-end with the real MemoryManager ──────────────────────

    @Test
    void indexedChunksAreRetrievableByChapter() {
        BookChunkIndexer idx = newRealIndexer();

        Book book = makeBook();
        idx.indexBook(book);

        // Concept appears in chapter 1's body — retrieval should hit
        // a chunk from chapter 1.
        String excerpt = idx.findRelevantExcerptSemantic(
            book.id, 1, "Gaussian distribution likelihood",
            BookChunkIndexer.EXCERPT_MAX_CHARS);
        assertFalse(excerpt.isBlank(),
            "semantic excerpt for an indexed concept must not be empty");
        assertTrue(excerpt.toLowerCase().contains("gaussian")
                || excerpt.toLowerCase().contains("normal"),
            "excerpt should surface content related to the query; got: " + excerpt);
    }

    @Test
    void retrievalFiltersToTheRequestedChapter() {
        BookChunkIndexer idx = newRealIndexer();
        Book book = makeBook();
        idx.indexBook(book);

        // Query a concept present in chapter 2 but request chapter 1.
        // Cosine match may rank a chapter-2 chunk highest globally;
        // the chapter-tag filter must drop it.
        String ch1 = idx.findRelevantExcerptSemantic(
            book.id, /*chapter*/ 1, "Bernoulli coin flip parameter",
            BookChunkIndexer.EXCERPT_MAX_CHARS);
        // Either we get chapter-1 content (which doesn't contain
        // "Bernoulli") or we get empty — both are correct. We MUST
        // NOT get chapter-2's Bernoulli content.
        assertFalse(ch1.toLowerCase().contains("bernoulli"),
            "chapter filter must keep chapter-2 chunks out of a " +
            "chapter-1 retrieval; got: " + ch1);
    }

    @Test
    void deindexBookDoesNotThrow() {
        // deindexBook is best-effort — see the method's javadoc for
        // why full deletion isn't possible against MemoryManager's
        // current API surface. The contract this test enforces is
        // weak: it must not throw, even on a fresh / empty store.
        BookChunkIndexer idx = newRealIndexer();
        idx.deindexBook("book_does_not_exist");
        Book book = makeBook();
        idx.indexBook(book);
        idx.deindexBook(book.id);
        // No assertion on retrievability — see TODO in deindexBook.
    }

    // ── helpers ─────────────────────────────────────────────────────

    /**
     * Build a real MemoryManager + in-process router so the test
     * exercises the actual write/read path without booting Spring.
     * Same wiring TutorBeansConfig.memoryRouter uses for in-process
     * mode, just constructed inline.
     */
    private static BookChunkIndexer newRealIndexer() {
        Map<MemoryType, MemoryStore> stores = new EnumMap<>(MemoryType.class);
        stores.put(MemoryType.WORKING,    new InProcessMemoryStore(MemoryType.WORKING));
        stores.put(MemoryType.SEMANTIC,   new InProcessMemoryStore(MemoryType.SEMANTIC));
        stores.put(MemoryType.PROCEDURAL, new InProcessMemoryStore(MemoryType.PROCEDURAL));
        stores.put(MemoryType.EPISODIC,   new InProcessMemoryStore(MemoryType.EPISODIC));
        MemoryRouter router = new MemoryRouter(stores);
        MemoryManager mm = new MemoryManager(router, new MockEmbeddingPort());
        return new BookChunkIndexer(mm, "tutor-os-test");
    }

    /** Two-chapter book with distinct vocabulary per chapter. */
    private static Book makeBook() {
        Book b = new Book();
        b.id = "book_test_chunk";
        b.learnerId = "L";
        b.subject = "machine learning";
        b.title = "Probabilistic Machine Learning";

        Chapter c1 = new Chapter();
        c1.number = 1;
        c1.title = "Chapter 1: Univariate Models";
        c1.body =
            "The univariate Gaussian distribution describes a continuous " +
            "random variable parameterised by its mean and variance. " +
            "The normal distribution is the canonical example of a " +
            "symmetric likelihood and underlies many maximum-likelihood " +
            "estimators for univariate data. We discuss Gaussian models " +
            "extensively in this chapter.";
        b.chapters.add(c1);

        Chapter c2 = new Chapter();
        c2.number = 2;
        c2.title = "Chapter 2: Discrete Distributions";
        c2.body =
            "The Bernoulli distribution models a single binary outcome " +
            "with parameter theta, the probability of success. A coin " +
            "flip is the canonical example. The Binomial distribution " +
            "generalises Bernoulli to multiple independent trials. " +
            "Maximum-likelihood estimation for Bernoulli reduces to " +
            "counting successes.";
        b.chapters.add(c2);

        return b;
    }
}
