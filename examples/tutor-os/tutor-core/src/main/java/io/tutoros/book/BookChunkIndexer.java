package io.tutoros.book;

import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.Importance;
import io.squados.memory.annotation.Memory;
import io.squados.memory.annotation.MemoryOp;
import io.squados.memory.annotation.MemoryScope;
import io.squados.memory.annotation.MemoryType;
import io.squados.memory.store.MemoryRecord;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BookChunkIndexer — embedding-based RAG for textbook chapters.
 *
 * <p>The previous lexical retrieval ({@code BookController.findRelevantExcerpt})
 * works only when the active concept tag matches a literal substring
 * of the chapter body. It silently misses when:
 *
 * <ul>
 *   <li>concept tag is "univariate gaussian" but Murphy writes "normal
 *       distribution"</li>
 *   <li>concept tag is "Bayes' rule" but the body uses "Bayes' theorem"</li>
 *   <li>concept tag is "ATP synthesis" but the chapter discusses
 *       "chemiosmosis" without ever naming ATP synthesis</li>
 * </ul>
 *
 * <p>This indexer slots into the existing M1 memory subsystem: each
 * chapter body is split into overlapping ~500-char windows and written
 * to the SEMANTIC tier of {@code MemoryManager}, which embeds the
 * chunk via the configured {@code EmbeddingPort} and stores the
 * vector. At query time the concept tag is embedded the same way and
 * cosine-similarity retrieval pulls the most relevant chunks
 * regardless of literal phrasing.
 *
 * <h2>Scoping</h2>
 * Each chunk carries two tags:
 * <ul>
 *   <li>{@code book:&lt;bookId&gt;}</li>
 *   <li>{@code chapter:&lt;chapterNumber&gt;}</li>
 * </ul>
 * Plus the chunk's {@code sessionId} is set to {@code bookId} so
 * {@code deleteBySession(bookId)} cleans up everything when a book
 * is deleted. The agent id {@code "book-rag"} keeps these chunks
 * separate from per-learner-turn memories written by {@link MemoryManager#write}
 * during a tutoring session.
 *
 * <h2>Graceful degradation</h2>
 * Indexer accepts a {@code null} {@link MemoryManager} and treats
 * every method as a no-op in that case. Same for embedder errors
 * during write — callers get a clean fall-through to the lexical
 * path. This is the same robustness contract M1 uses for the
 * {@code @AgentMemory} read path.
 */
public class BookChunkIndexer {

    /** Target chunk size — ~half a paragraph of dense prose. */
    static final int CHUNK_SIZE = 500;

    /**
     * Overlap between consecutive chunks. Helps concepts that span
     * a paragraph boundary still get matched — without overlap, a
     * concept right at the cut point loses retrieval signal.
     */
    static final int CHUNK_OVERLAP = 100;

    /** Top-K passed to the retrieval query. */
    static final int RETRIEVAL_TOP_K = 6;

    /** Char budget for the joined excerpt returned to the agent. */
    public static final int EXCERPT_MAX_CHARS = 2000;

    /** Cosine-similarity floor — drop chunks below this. */
    static final float MIN_SCORE = 0.20f;

    /**
     * Agent id used for all book-RAG writes. Keeps chunks separate
     * from per-turn agent memories (which use the agent's @Agent
     * name as the id).
     */
    static final String AGENT_ID = "book-rag";

    private final MemoryManager memoryManager;

    /** Squad id used for writes — typically resolves to "tutor-os". */
    private final String squadId;

    /**
     * Construct with the M1 manager. Pass {@code null} when M1 is
     * disabled or unavailable — every method becomes a clean no-op
     * and the caller's lexical fallback kicks in.
     */
    public BookChunkIndexer(MemoryManager memoryManager, String squadId) {
        this.memoryManager = memoryManager;
        this.squadId       = squadId != null ? squadId : "tutor-os";
    }

    /** True when this indexer is wired to a live memory manager. */
    public boolean isEnabled() { return memoryManager != null; }

    // ── Indexing ────────────────────────────────────────────────────

    /**
     * Index every chapter of a book. Re-indexing the same book is
     * safe but produces duplicate rows — callers should
     * {@link #deindexBook} first if the book content changed.
     *
     * <p>Best-effort: any per-chunk write failure is logged to
     * stderr and skipped, the rest of the book still indexes. Same
     * contract M1 uses for memory writes generally — never let a
     * background failure block the user-visible path.
     */
    public void indexBook(Book book) {
        if (memoryManager == null || book == null || book.chapters() == null) return;
        for (Chapter ch : book.chapters()) {
            indexChapter(book.id, ch);
        }
    }

    /** Index one chapter — exposed for incremental re-index later. */
    public void indexChapter(String bookId, Chapter chapter) {
        if (memoryManager == null || chapter == null || chapter.body == null) return;
        List<String> chunks = chunk(chapter.body, CHUNK_SIZE, CHUNK_OVERLAP);
        for (String content : chunks) {
            try {
                Memory ann = semanticAnnotation(new String[] {
                    "book:"    + bookId,
                    "chapter:" + chapter.number,
                });
                memoryManager.write(ann, AGENT_ID, squadId, /*sessionId*/ bookId, content);
            } catch (RuntimeException badEmbed) {
                // Embedder failure (rate limit, mocked port producing
                // bad vectors, etc.) — keep going. Lexical fallback
                // will cover this concept at query time.
                System.err.println(
                    "[TutorOS] book chunk index write failed (skipped): " +
                    badEmbed.getMessage());
            }
        }
    }

    /**
     * Best-effort cleanup of indexed chunks for a deleted book.
     *
     * <p>TODO — {@link MemoryManager#closeSession} only flushes the
     * WORKING tier through {@code MemoryRouter.promoteAndFlush}; it
     * does NOT delete SEMANTIC records where book chunks live. The
     * SquadOS MemoryStore SPI does carry {@code deleteBySession} but
     * MemoryManager doesn't expose a path to it without modifying
     * squad-core. Until that lands, this method is essentially a
     * no-op for SEMANTIC chunks.
     *
     * <p>Practical impact: zero. Each upload generates a fresh
     * {@code bookId} via {@code BookExtractionService.freshBookId()},
     * so chunks from a deleted book never collide with a future
     * upload's retrievals — they're stranded in the store but
     * unreachable. Worth fixing for storage hygiene; not blocking
     * any user-visible bug.
     */
    public void deindexBook(String bookId) {
        if (memoryManager == null || bookId == null) return;
        try {
            memoryManager.closeSession(bookId, squadId);
        } catch (RuntimeException ignored) { /* best-effort */ }
    }

    // ── Retrieval ───────────────────────────────────────────────────

    /**
     * Returns up to {@code maxChars} of joined chunk content from the
     * given chapter that semantically matches the concept query. The
     * matches come from {@link MemoryManager#read} which embeds the
     * concept and runs cosine similarity over the stored vectors.
     *
     * <p>Result is intentionally a single concatenated string — same
     * shape as the lexical {@code findRelevantExcerpt} so the
     * controller can swap them transparently. Multiple chunks are
     * separated by a blank line so the LLM sees natural paragraph
     * boundaries.
     *
     * <p>Returns empty string when:
     * <ul>
     *   <li>indexer is disabled (no memory manager)</li>
     *   <li>no chunks are indexed for this book/chapter yet</li>
     *   <li>no chunks meet {@link #MIN_SCORE}</li>
     * </ul>
     */
    public String findRelevantExcerptSemantic(
            String bookId, int chapterNumber, String conceptQuery, int maxChars) {
        if (memoryManager == null) return "";
        if (conceptQuery == null || conceptQuery.isBlank()) return "";
        if (bookId == null || bookId.isBlank()) return "";

        Memory readAnn = semanticReadAnnotation(RETRIEVAL_TOP_K);
        List<MemoryRecord> hits;
        try {
            hits = memoryManager.read(readAnn, AGENT_ID, squadId, bookId, conceptQuery);
        } catch (RuntimeException e) {
            return "";
        }
        if (hits == null || hits.isEmpty()) return "";

        // Filter to the active chapter only — multi-chapter retrievals
        // can produce a chunk from chapter 11 when the learner is on
        // chapter 2. Embedding cosine alone isn't a strong enough
        // scope.
        String chapterTag = "chapter:" + chapterNumber;
        List<MemoryRecord> chapterHits = hits.stream()
            .filter(r -> hasTag(r, chapterTag))
            .collect(Collectors.toList());
        if (chapterHits.isEmpty()) return "";

        // Concat top chunks until we hit the char budget. The records
        // are already ordered by relevance from the retrieval call.
        StringBuilder sb = new StringBuilder(maxChars + 64);
        for (MemoryRecord r : chapterHits) {
            String c = r.getContent();
            if (c == null || c.isBlank()) continue;
            if (sb.length() > 0 && sb.length() + c.length() + 2 > maxChars) break;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(c.trim());
            if (sb.length() >= maxChars) break;
        }
        return sb.toString();
    }

    private static boolean hasTag(MemoryRecord r, String wanted) {
        if (r == null) return false;
        String[] tags = r.getTags();
        if (tags == null) return false;
        for (String t : tags) if (wanted.equals(t)) return true;
        return false;
    }

    // ── Chunking ───────────────────────────────────────────────────

    /**
     * Sliding-window chunking with overlap. Tries to break at a
     * sentence boundary inside the last ~80 chars before the hard
     * cut so chunks don't end mid-clause. Pure function; covered by
     * BookChunkIndexerTest without any memory subsystem.
     */
    static List<String> chunk(String body, int chunkSize, int overlap) {
        List<String> out = new ArrayList<>();
        if (body == null || body.isBlank()) return out;
        if (chunkSize <= 0) chunkSize = CHUNK_SIZE;
        if (overlap < 0)    overlap   = 0;
        if (overlap >= chunkSize) overlap = chunkSize / 5; // sanity

        int len = body.length();
        int from = 0;
        while (from < len) {
            int to = Math.min(len, from + chunkSize);
            if (to < len) {
                // Try to retreat to the last sentence boundary in the
                // last ~80 chars so chunks end cleanly.
                int boundary = findSentenceBreak(body, Math.max(from + chunkSize - 80, from + chunkSize / 2), to);
                if (boundary > 0) to = boundary;
            }
            String slice = body.substring(from, to).trim();
            if (!slice.isEmpty()) out.add(slice);
            if (to >= len) break;
            // Slide forward by chunkSize - overlap.
            int next = to - overlap;
            if (next <= from) next = from + Math.max(1, chunkSize - overlap);
            from = next;
        }
        return out;
    }

    /** Last "[.?!] " in [start, end]; returns the index just after, or -1. */
    private static int findSentenceBreak(String s, int start, int end) {
        int best = -1;
        int upper = Math.min(end, s.length() - 1);
        for (int i = start; i < upper; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '?' || c == '!') && Character.isWhitespace(s.charAt(i + 1))) {
                best = i + 1;
            }
        }
        return best;
    }

    // ── Annotation synthesis ───────────────────────────────────────

    /**
     * Build a {@link Memory} annotation suitable for indexing one
     * chunk. SEMANTIC type (general facts, not session-bound).
     * SQUAD scope so cross-agent retrieval works. Tags carry the
     * book + chapter identifiers.
     */
    private static Memory semanticAnnotation(String[] tags) {
        return synthesizeAnnotation(MemoryType.SEMANTIC, MemoryScope.SQUAD,
            MemoryOp.WRITE, Importance.MEDIUM, tags, /*topK*/ 0, /*minScore*/ 0f);
    }

    /** Read-side annotation — same scope as the write so retrieval matches. */
    private static Memory semanticReadAnnotation(int topK) {
        return synthesizeAnnotation(MemoryType.SEMANTIC, MemoryScope.SQUAD,
            MemoryOp.READ, Importance.MEDIUM, new String[0], topK, MIN_SCORE);
    }

    /**
     * Synthesise a {@link Memory} annotation via {@link Proxy}.
     * Same trick as {@code io.tutoros.pipeline.MemoryHelper} — the
     * framework's primary write path is method-level annotation
     * processing (Phase 3 AOP, not yet implemented), so explicit
     * writers have to hand it an annotation instance. We don't reuse
     * MemoryHelper because it's package-private to {@code .pipeline.}.
     */
    private static Memory synthesizeAnnotation(MemoryType type, MemoryScope scope,
                                               MemoryOp op, Importance importance,
                                               String[] tags, int topK, float minScore) {
        return (Memory) Proxy.newProxyInstance(
            Memory.class.getClassLoader(),
            new Class<?>[]{ Memory.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "type"           -> type;
                case "scope"          -> scope;
                case "op"             -> op;
                case "topK"           -> topK;
                case "minScore"       -> minScore;
                case "tags"           -> tags;
                case "importance"     -> importance;
                case "promote"        -> false;
                case "annotationType" -> Memory.class;
                case "toString"       -> "@Memory(book-rag, type=" + type + ", op=" + op + ")";
                case "hashCode"       -> System.identityHashCode(proxy);
                case "equals"         -> proxy == args[0];
                default -> {
                    Object def = method.getDefaultValue();
                    if (def != null) yield def;
                    throw new UnsupportedOperationException(method.getName());
                }
            });
    }
}
