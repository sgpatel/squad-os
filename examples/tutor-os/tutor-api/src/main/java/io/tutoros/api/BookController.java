package io.tutoros.api;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.tutoros.agent.BookCoachAgent;
import io.tutoros.book.Book;
import io.tutoros.book.BookChunkIndexer;
import io.tutoros.book.BookExtractionService;
import io.tutoros.book.BookRepository;
import io.tutoros.book.Chapter;
import io.tutoros.mastery.MasteryGrade;
import io.tutoros.mastery.MasteryService;
import io.tutoros.model.LearningInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Book Controller — uploads + lookups for learner-supplied textbooks.
 *
 * <h2>Endpoints (all under /api/books)</h2>
 * <pre>
 *   POST /upload                                       multipart, ingest a PDF
 *   GET  /{learnerId}                                  list all books for learner
 *   GET  /{learnerId}/by-subject/{subject}             scoped list (PR-A isolation)
 *   GET  /{learnerId}/{bookId}                         book detail without chapter bodies
 *   GET  /{learnerId}/{bookId}/chapter/{n}             full chapter body + concepts
 *   DELETE /{learnerId}/{bookId}                       admin-only delete
 * </pre>
 *
 * <p>The list + book-detail endpoints intentionally omit chapter
 * {@link Chapter#body} from the response to keep the wire payload small
 * on a library page. Use {@code /chapter/{n}} when the learner opens
 * a specific chapter; that's when the body cost is worth paying.
 */
@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final BookExtractionService extractor;
    private final BookRepository        repo;
    private final SquadContext          ctx;
    private final BookCoachAgent        coach;
    private final MasteryService        mastery;
    private final BookChunkIndexer      indexer;

    public BookController(BookExtractionService extractor,
                          BookRepository repo,
                          SquadContext ctx,
                          BookCoachAgent coach,
                          MasteryService mastery,
                          BookChunkIndexer indexer) {
        this.extractor = extractor;
        this.repo      = repo;
        this.ctx       = ctx;
        this.coach     = coach;
        this.mastery   = mastery;
        this.indexer   = indexer;
    }

    // ── DTOs ─────────────────────────────────────────────────────────

    /**
     * Trimmed book view for the list + detail endpoints — chapters
     * are present but their {@code body} is stripped to keep the
     * payload small. Library pages can paint without the full text.
     */
    public record BookSummary(
        String        id,
        String        learnerId,
        String        subject,
        String        title,
        String        author,
        int           totalPages,
        String        uploadedAt,
        List<ChapterSummary> chapters
    ) {
        public static BookSummary from(Book b) {
            List<ChapterSummary> chs = b.chapters().stream()
                .map(ChapterSummary::from).collect(Collectors.toList());
            return new BookSummary(
                b.id, b.learnerId, b.subject, b.title, b.author, b.totalPages,
                b.uploadedAt != null ? b.uploadedAt.toString() : null,
                chs);
        }
    }

    public record ChapterSummary(
        int    number,
        String title,
        String summary,
        int    pageStart,
        int    pageEnd,
        List<String> concepts
    ) {
        public static ChapterSummary from(Chapter c) {
            return new ChapterSummary(
                c.number, c.title, c.summary, c.pageStart, c.pageEnd, c.concepts());
        }
    }

    // ── /upload ──────────────────────────────────────────────────────

    /**
     * POST /api/books/upload — multipart upload of a textbook PDF.
     *
     * Form parts:
     * <ul>
     *   <li>{@code file}      — the PDF (required)</li>
     *   <li>{@code learnerId} — required; sets ownership</li>
     *   <li>{@code subject}   — required; PR-A scope key</li>
     *   <li>{@code title}     — required; learner-facing label</li>
     *   <li>{@code author}    — optional</li>
     * </ul>
     *
     * Responses
     * <ul>
     *   <li>200 — {@link BookSummary} of the ingested book</li>
     *   <li>400 — empty / oversized / unreadable PDF, or missing field</li>
     *   <li>502 — extraction succeeded but persistence failed</li>
     * </ul>
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<BookSummary> upload(
            @RequestPart("file")        MultipartFile file,
            @RequestParam("learnerId")  String learnerId,
            @RequestParam("subject")    String subject,
            @RequestParam("title")      String title,
            @RequestParam(value = "author", required = false) String author) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (learnerId == null || learnerId.isBlank()
         || subject == null   || subject.isBlank()
         || title == null     || title.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Book book;
        try {
            book = extractor.extract(file.getBytes(), learnerId, subject, title, author);
        } catch (IllegalArgumentException badInput) {
            log.info("book upload rejected: {}", badInput.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException ioe) {
            log.warn("book extraction io error", ioe);
            return ResponseEntity.status(502).build();
        }

        try {
            repo.save(book);
        } catch (RuntimeException persistErr) {
            log.warn("book persist failed", persistErr);
            return ResponseEntity.status(502).build();
        }

        // Semantic indexing — embed each chapter's chunks into the M1
        // memory store so /ask retrieval matches concepts by meaning,
        // not just substring. Best-effort: indexer is a no-op when
        // M1 is disabled or the embedder fails; lexical retrieval is
        // the fallback either way. Synchronous on purpose — Murphy-
        // sized books finish in ~10 s and we want the first /ask to
        // see indexed chunks; async would race the controller.
        try {
            indexer.indexBook(book);
            if (indexer.isEnabled()) {
                log.info("indexed book id={} for semantic retrieval " +
                    "({} chapters)", book.id, book.chapters().size());
            }
        } catch (RuntimeException indexErr) {
            log.warn("book index failed (continuing with lexical fallback)", indexErr);
        }

        log.info("ingested book id={} learner={} subject={} title=\"{}\" chapters={}",
            book.id, book.learnerId, book.subject, book.title, book.chapters().size());
        return ResponseEntity.ok(BookSummary.from(book));
    }

    // ── /{learnerId} ─────────────────────────────────────────────────

    /** GET /api/books/{learnerId} — all books for the learner, newest first. */
    @GetMapping("/{learnerId}")
    public ResponseEntity<List<BookSummary>> list(@PathVariable String learnerId) {
        List<BookSummary> out = repo.listForLearner(learnerId).stream()
            .map(BookSummary::from).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /** GET /api/books/{learnerId}/by-subject/{subject} — PR-A scoped list. */
    @GetMapping("/{learnerId}/by-subject/{subject}")
    public ResponseEntity<List<BookSummary>> listBySubject(
            @PathVariable String learnerId,
            @PathVariable String subject) {
        List<BookSummary> out = repo.listForSubject(learnerId, subject).stream()
            .map(BookSummary::from).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    // ── /{learnerId}/{bookId} ────────────────────────────────────────

    /** GET /api/books/{learnerId}/{bookId} — book metadata + chapter index (no bodies). */
    @GetMapping("/{learnerId}/{bookId}")
    public ResponseEntity<BookSummary> get(
            @PathVariable String learnerId,
            @PathVariable String bookId) {
        return repo.findById(bookId)
            .filter(b -> learnerId.equals(b.learnerId))
            .map(b -> ResponseEntity.ok(BookSummary.from(b)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/books/{learnerId}/{bookId}/chapter/{n}
     * Full chapter — body included so the learner / BookCoach has the
     * ground truth to work from. 404 when the book or chapter is unknown.
     */
    @GetMapping("/{learnerId}/{bookId}/chapter/{n}")
    public ResponseEntity<Chapter> chapter(
            @PathVariable String learnerId,
            @PathVariable String bookId,
            @PathVariable int n) {
        Book b = repo.findById(bookId)
            .filter(book -> learnerId.equals(book.learnerId))
            .orElse(null);
        if (b == null) return ResponseEntity.notFound().build();
        Chapter c = b.chapter(n);
        return c != null ? ResponseEntity.ok(c) : ResponseEntity.notFound().build();
    }

    // ── /ask — BookCoach learning loop (PR-2) ────────────────────────

    /**
     * POST /api/books/{learnerId}/{bookId}/chapter/{n}/ask?mode=basic
     *
     * Materialises a {@link LearningInsight} for the given chapter
     * through one of {@link BookCoachAgent}'s six lenses
     * (basic / intermediate / advanced / usage / history / future).
     *
     * <h2>Agent dispatch</h2>
     * BookCoachAgent shares the WILDCARD role with SyllabusSuggesterAgent;
     * the SquadOS registry's byRole map is last-writer-wins, so the
     * suggester owns {@code submitTo(WILDCARD)}. We reach BookCoach
     * by name via {@code getRegistry().getByName(...)} so the @StructuredOutput
     * + @Retry + @Traced wrappers still kick in — only the dispatch
     * lookup differs from the standard {@code ctx.submitTo} path.
     *
     * <h2>Response codes</h2>
     * <ul>
     *   <li>200 — LearningInsight body</li>
     *   <li>400 — empty/unknown mode or missing chapter</li>
     *   <li>404 — book/chapter not found, or ownership mismatch</li>
     *   <li>502 — agent call failed (LLM provider down)</li>
     * </ul>
     */
    @PostMapping("/{learnerId}/{bookId}/chapter/{n}/ask")
    public ResponseEntity<LearningInsight> ask(
            @PathVariable String learnerId,
            @PathVariable String bookId,
            @PathVariable int    n,
            @RequestParam(value = "mode",    defaultValue = "basic")            String mode,
            @RequestParam(value = "concept", required    = false)               String concept,
            @RequestParam(value = "level",   defaultValue = "SENIOR_SCHOOL")    String level) {

        Book b = repo.findById(bookId)
            .filter(book -> learnerId.equals(book.learnerId))
            .orElse(null);
        if (b == null) return ResponseEntity.notFound().build();
        Chapter ch = b.chapter(n);
        if (ch == null) return ResponseEntity.notFound().build();

        // Resolve the target concept: prefer the explicit one when
        // it appears in the chapter's extracted list (so M3 mastery
        // writes hit a real row), else fall back to the first concept
        // the extractor found.
        String targetConcept = resolveConcept(ch, concept);
        if (targetConcept == null) targetConcept = ch.title;

        String normalisedMode = BookCoachAgent.normalizeMode(mode);
        // Excerpt selection — semantic first (embedding-based RAG via the
        // M1 memory subsystem), falling back to lexical substring match.
        // Semantic catches the synonym cases lexical misses ("univariate
        // gaussian" ↔ "normal distribution", etc.); lexical handles the
        // path where M1 is disabled or no chunks were indexed yet
        // (e.g. books uploaded before this commit shipped).
        String excerpt = indexer.findRelevantExcerptSemantic(
            bookId, n, targetConcept, BookChunkIndexer.EXCERPT_MAX_CHARS);
        String excerptSource = excerpt.isBlank() ? "lexical" : "semantic";
        if (excerpt.isBlank()) {
            excerpt = findRelevantExcerpt(ch.body, targetConcept, 2000);
        }
        log.debug("book /ask excerpt selection: book={} chapter={} mode={} " +
            "concept={} source={} excerptChars={}",
            bookId, n, normalisedMode, targetConcept, excerptSource, excerpt.length());

        String prompt = coach.buildPrompt(
            normalisedMode,
            b.title, ch.title, excerpt,
            targetConcept, level,
            ch.pageStart, ch.pageEnd);

        // Dispatch by name — see class-level comment in BookCoachAgent
        // for why we bypass ctx.submitTo here.
        AgentWrapper wrapper = ctx.getRegistry().getByName("BookCoachAgent");
        if (wrapper == null) {
            // Hard config bug, not a per-request issue — surface as 503
            // so monitoring can distinguish "service degraded" from
            // "this request failed."
            log.error("BookCoachAgent not registered with SquadOS — bean wiring missing");
            return ResponseEntity.status(503).build();
        }

        LearningInsight insight = null;
        AgentResponse   rawResp = null;
        Throwable lastError = null;
        // Tri-state failure mode so the user-visible body can carry an
        // actionable diagnosis. UPSTREAM = empty response from the LLM
        // (auth, quota, transport); PARSE = LLM returned text but it
        // didn't match the schema; THROWN = an exception bubbled out.
        Failure failure = Failure.NONE;
        long t0 = System.nanoTime();
        try {
            TaskContext taskCtx = new TaskContext(
                prompt, UUID.randomUUID().toString(), ctx.getConfig().getProfile());
            rawResp = wrapper.execute(taskCtx);
            // structuredOutput can throw on parse failure — keep it
            // inside the try so the catch's stub fallback covers it.
            if (rawResp != null) insight = rawResp.structuredOutput(LearningInsight.class);
            if (insight == null && rawResp != null) {
                String raw = rawResp.content();
                int rawLen = raw != null ? raw.length() : 0;
                // EMPTY response → almost always an upstream problem
                // (Spring AI's retry mechanism eats the exception and
                // returns blank content when OpenAI rejects with 401 /
                // 429 / network error). Different cause + different
                // fix vs a non-empty-but-unparseable response.
                failure = (rawLen == 0) ? Failure.UPSTREAM : Failure.PARSE;
                log.warn(
                    "BookCoach response not usable: book={} chapter={} mode={} " +
                    "concept={} promptChars={} elapsedMs={} rawLen={} " +
                    "failure={} rawHead={}",
                    bookId, n, normalisedMode, targetConcept,
                    prompt.length(),
                    (System.nanoTime() - t0) / 1_000_000L,
                    rawLen, failure,
                    raw != null && rawLen > 0
                        ? raw.substring(0, Math.min(500, rawLen)).replace('\n', ' ')
                        : "(empty)");
            }
        } catch (Throwable e) {
            failure = Failure.THROWN;
            // Catch Throwable (not just RuntimeException) so OOM,
            // schema-parse Errors, or any wrapper rethrow can't leak
            // past us as a raw 500/502 with no body.
            lastError = e;
            // Structured log so the next failure leaves a useful breadcrumb.
            // The full prompt is intentionally NOT logged — it can carry
            // chapter content (potentially a copyrighted textbook) and
            // would balloon log volume. Length is enough to diagnose
            // size-related issues without leaking text.
            log.warn(
                "BookCoach ask failed: book={} chapter={} mode={} concept={} " +
                "promptChars={} bodyChars={} elapsedMs={} cause={}",
                bookId, n, normalisedMode, targetConcept,
                prompt.length(),
                ch.body != null ? ch.body.length() : 0,
                (System.nanoTime() - t0) / 1_000_000L,
                rootCauseSummary(e),
                e);
        }

        if (insight == null) {
            // Graceful UX: rather than a 502 with empty body (opaque to
            // the learner, no actionable info), return 200 with a stub
            // insight whose body explains what failed. The UI renders
            // it like any other lens — markdown + retry button — so the
            // learner can pick a different lens or retry. Each failure
            // mode names the right knob to turn.
            insight = new LearningInsight();
            insight.mode    = normalisedMode;
            insight.concept = targetConcept;
            insight.body    = switch (failure) {
                case THROWN -> "_The tutor couldn't draft this insight right now._\n\n" +
                    "**Error:** " + rootCauseSummary(lastError) + "\n\n" +
                    "Try again, or check that the LLM provider " +
                    "(Ollama / OpenAI) is reachable.";
                case UPSTREAM ->
                    "_The LLM returned an empty response._\n\n" +
                    "This almost always means the upstream provider rejected " +
                    "the call but didn't surface the reason. Common causes:\n\n" +
                    "- **Invalid or expired `OPENAI_API_KEY`** — verify with " +
                    "`curl https://api.openai.com/v1/models -H \"Authorization: Bearer $OPENAI_API_KEY\"`\n" +
                    "- **Rate limit / quota exceeded** — check your OpenAI usage page\n" +
                    "- **Auth challenge during a streaming upload** — Spring AI's " +
                    "transport sometimes swallows 401/429 as `HttpRetryException: " +
                    "cannot retry due to server authentication, in streaming mode`\n\n" +
                    "Server log carries the full stack — look for `Retry error` " +
                    "or `cannot retry due to server authentication`.";
                case PARSE ->
                    "_The tutor's response didn't match the expected shape._\n\n" +
                    "The LLM returned content but it wasn't valid JSON. This " +
                    "happens with weaker models on complex chapters. Try a " +
                    "different lens (**History** and **Future** are the most " +
                    "forgiving schemas), or retry. The server log shows the " +
                    "first 500 chars the model emitted — that's the fastest " +
                    "way to diagnose what's drifting.";
                case NONE -> "_Empty insight (this shouldn't happen — please file a bug)._";
            };
        } else {
            // Defensive normalisation — LLM occasionally drops fields.
            if (insight.mode    == null || insight.mode.isBlank())    insight.mode    = normalisedMode;
            if (insight.concept == null || insight.concept.isBlank()) insight.concept = targetConcept;
            // Defensive markdown normalisation. Two passes in order:
            //   1. unescapeLiteralEscapes — when the LLM types "\n"
            //      literally inside the JSON string instead of using
            //      a real newline (a recurring failure mode of older
            //      / smaller models), the JSON decoder produces the
            //      4-char sequence backslash-n in the body. The UI
            //      renders that as visible text. Replace any surviving
            //      literal "\n" / "\t" with real chars.
            //   2. ensureHeadingBreaks — guarantees "## " / "### "
            //      headings start on their own line even if the LLM
            //      ran them inline with the prior paragraph.
            // Both are idempotent and safe on already-correct markdown.
            insight.body        = ensureHeadingBreaks(unescapeLiteralEscapes(insight.body));
            insight.modelAnswer = ensureHeadingBreaks(unescapeLiteralEscapes(insight.modelAnswer));
            // followUps is a newline-separated list of prompts. Same
            // failure mode as body — some models emit literal "\n"
            // between items, which then renders as one run-on line in
            // the "Go deeper" panel. Unescape so the UI splits cleanly.
            insight.followUps   = unescapeLiteralEscapes(insight.followUps);
            // Source excerpt — NOT touched by the agent, populated
            // here so the UI's "From the book" panel always has
            // ground truth to render even when the LLM body is generic.
            insight.sourceExcerpt = excerpt;
        }
        return ResponseEntity.ok(insight);
    }

    /**
     * Lexical RAG — find a focused excerpt of the chapter body around
     * the target concept. Used by the {@code /ask} flow so the LLM
     * grounds on the part of the chapter actually relevant to the
     * learner's question, AND so the UI can show the raw source
     * excerpt before the generated take.
     *
     * <p>Strategy: case-insensitive substring search for the concept
     * tag. If found, return ~{@code maxChars} of body centred on the
     * first match (cut at sentence boundaries when possible). If not
     * found, return the first {@code maxChars} of the chapter — better
     * than nothing, and the chapter intro usually mentions the main
     * concepts anyway.
     *
     * <p>Embedding-based RAG is the future (we already have the
     * memory subsystem from M1) — lexical does the job for v1.
     */
    static String findRelevantExcerpt(String body, String concept, int maxChars) {
        if (body == null || body.isBlank()) return "";
        if (concept == null || concept.isBlank() || maxChars <= 0) {
            return safeSlice(body, 0, Math.min(maxChars, body.length()));
        }
        // Hit on the concept tag — try a few normalisation forms so
        // multi-word concepts find their first occurrence even if the
        // chapter uses slightly different spacing or capitalisation.
        int hit = indexOfCi(body, concept);
        if (hit < 0) hit = indexOfCi(body, concept.replace('-', ' '));
        if (hit < 0) {
            // No match — return the chapter intro as a fallback. The
            // first few hundred chars usually frame what's coming.
            return trimToSentenceBoundary(safeSlice(body, 0, Math.min(maxChars, body.length())));
        }
        // Centre the window on the hit, biased so a third of the window
        // is BEFORE the hit (context) and two thirds AFTER (the actual
        // discussion that follows the concept naming).
        int before = maxChars / 3;
        int after  = maxChars - before;
        int from = Math.max(0, hit - before);
        int to   = Math.min(body.length(), hit + after);
        return trimToSentenceBoundary(safeSlice(body, from, to));
    }

    private static int indexOfCi(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) return -1;
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    /** Defensive substring — never throws on out-of-range indices. */
    private static String safeSlice(String s, int from, int to) {
        if (s == null) return "";
        int a = Math.max(0, Math.min(from, s.length()));
        int b = Math.max(a, Math.min(to,   s.length()));
        return s.substring(a, b);
    }

    /**
     * Trim a substring to nearest sentence boundary at both ends so
     * the excerpt doesn't start mid-word or end mid-sentence. Falls
     * back to the original if no good boundary is found in a sensible
     * window.
     */
    private static String trimToSentenceBoundary(String s) {
        if (s == null || s.length() < 50) return s;
        // Trim leading partial sentence — find first ". " in the
        // first ~200 chars and start AFTER it.
        int lead = -1;
        for (int i = 0; i < Math.min(200, s.length() - 1); i++) {
            if (s.charAt(i) == '.' && Character.isWhitespace(s.charAt(i + 1))) {
                lead = i + 1;
                break;
            }
        }
        // Trim trailing partial — find last ". " in the last ~200 chars.
        int tail = s.length();
        for (int i = s.length() - 1; i > Math.max(0, s.length() - 200); i--) {
            if (i + 1 < s.length() && s.charAt(i) == '.'
                && Character.isWhitespace(s.charAt(i + 1))) {
                tail = i + 1;
                break;
            }
        }
        // Only apply if both trims are sane.
        int from = (lead > 0 && lead < s.length() / 4) ? lead + 1 : 0;
        int to   = (tail < s.length() && tail > 3 * s.length() / 4) ? tail : s.length();
        if (to <= from) return s.trim();
        return s.substring(from, to).trim();
    }

    /**
     * Replace any literal backslash-n / backslash-t that the LLM typed
     * inside its JSON string with the real characters. This is the
     * mirror of what {@code JSON.parse} would have done if the LLM had
     * emitted the JSON-escape sequence properly — but some models type
     * the 4 ASCII characters \n verbatim ("Today\\n\\n…") and the
     * decoder leaves them alone, so the UI ends up rendering visible
     * text "\n\n" instead of an actual paragraph break.
     *
     * <p>Idempotent: an already-decoded string contains zero literal
     * backslash-n sequences, so a second pass is a no-op.
     */
    static String unescapeLiteralEscapes(String md) {
        if (md == null || md.isEmpty()) return md;
        // Check before allocating — most well-behaved responses are
        // already correct and we don't want to thrash the heap on the
        // happy path.
        if (md.indexOf('\\') < 0) return md;
        return md
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r");
    }

    /**
     * Insert paragraph breaks before any "## " or "### " that the LLM
     * emitted inline with the previous paragraph. Runs the simplest
     * transformation that works:
     *
     *   - if a "###" / "##" token is at the start of the string → leave it
     *   - if it's preceded by whitespace including a newline → leave it
     *   - else → insert "\n\n" right before it
     *
     * Idempotent (running twice yields the same result) and safe on
     * already-correct markdown.
     */
    static String ensureHeadingBreaks(String md) {
        if (md == null || md.isBlank()) return md;
        // Two passes — one for ### then one for ## — order matters so
        // we don't double-insert when "##" is a prefix of "###".
        String out = breakBefore(md,  "### ");
        out        = breakBefore(out, "## ");
        return out;
    }

    private static String breakBefore(String md, String marker) {
        StringBuilder sb = new StringBuilder(md.length() + 16);
        int i = 0;
        while (i < md.length()) {
            int hit = md.indexOf(marker, i);
            if (hit < 0) { sb.append(md, i, md.length()); break; }
            sb.append(md, i, hit);
            // Already at line start (top of body or preceded by \n)?
            boolean atLineStart = hit == 0 || md.charAt(hit - 1) == '\n';
            if (!atLineStart) sb.append("\n\n");
            sb.append(marker);
            i = hit + marker.length();
        }
        return sb.toString();
    }

    /**
     * Failure classes used by {@link #ask} to pick the right user-visible
     * diagnosis. Kept private — the controller is the only place that
     * cares about the distinction.
     */
    private enum Failure {
        NONE,
        /** An exception bubbled out of the agent call (timeout, transport, etc). */
        THROWN,
        /**
         * The LLM call "succeeded" but returned an empty body. Almost
         * always means Spring AI's retry mechanism swallowed an
         * upstream 401/429/transport error and recovered with blanks.
         * Different fix vs PARSE — the operator should check provider
         * auth / quota, not the prompt.
         */
        UPSTREAM,
        /**
         * The LLM returned content but it didn't match the
         * {@code LearningInsight} schema. Suggests a prompt issue;
         * the server log carries the first 500 chars of the emission.
         */
        PARSE
    }

    /**
     * Walk an exception chain and produce a single short summary string
     * for both logs and the user-visible error body. Stops at the first
     * non-wrapper cause so the message is the ACTUAL failure (e.g.
     * "Read timed out") rather than the outermost rethrow (e.g.
     * "RuntimeException: Read timed out").
     */
    private static String rootCauseSummary(Throwable t) {
        if (t == null) return "(no detail)";
        Throwable cur = t;
        for (int i = 0; i < 8 && cur.getCause() != null && cur.getCause() != cur; i++) {
            cur = cur.getCause();
        }
        String name = cur.getClass().getSimpleName();
        String msg  = cur.getMessage();
        return msg != null && !msg.isBlank() ? name + ": " + msg : name;
    }

    // ── /answer — record mastery on a quiz-style lens (PR-2) ─────────

    /**
     * POST /api/books/{learnerId}/{bookId}/chapter/{n}/answer
     *
     * The learner self-grades after answering one of the quiz-style
     * lenses (basic / intermediate / advanced). We don't run the
     * AssessmentAgent here — the LLM-graded path lives on
     * {@code /session/answer} and pulls in a heavier prompt. For a
     * fast self-grade loop, the learner picks an SM-2 grade
     * (AGAIN / HARD / GOOD / EASY) and we hand it straight to
     * {@link MasteryService#recordOutcome} with source="book". Same
     * shape as {@code ReviewController.answer} so the M3-B "+12%"
     * toast fires identically.
     */
    @PostMapping("/{learnerId}/{bookId}/chapter/{n}/answer")
    public ResponseEntity<Void> recordAnswer(
            @PathVariable String learnerId,
            @PathVariable String bookId,
            @PathVariable int    n,
            @RequestBody  AnswerRequest body) {

        if (body == null || body.concept == null || body.concept.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Book b = repo.findById(bookId)
            .filter(book -> learnerId.equals(book.learnerId))
            .orElse(null);
        if (b == null) return ResponseEntity.notFound().build();
        Chapter ch = b.chapter(n);
        if (ch == null) return ResponseEntity.notFound().build();

        MasteryGrade grade;
        try {
            grade = MasteryGrade.valueOf(
                (body.grade == null ? "GOOD" : body.grade).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            grade = MasteryGrade.GOOD;
        }

        mastery.recordOutcome(learnerId, b.subject, body.concept, grade, "book");
        log.debug("book answer learner={} subject={} concept={} grade={}",
            learnerId, b.subject, body.concept, grade);
        return ResponseEntity.noContent().build();
    }

    public record AnswerRequest(String concept, String grade) {}

    /**
     * Pick the concept to anchor a learning request to.
     * <ol>
     *   <li>If the caller passed an explicit concept that matches one
     *       in the chapter's extracted list, use it (so mastery writes
     *       land on a real concept tag).</li>
     *   <li>Else if the chapter has any concepts, use the first.</li>
     *   <li>Else null — the controller falls back to the chapter title.</li>
     * </ol>
     */
    private static String resolveConcept(Chapter ch, String requested) {
        if (requested != null && !requested.isBlank()) {
            String key = requested.trim().toLowerCase();
            for (String c : ch.concepts()) {
                if (c.equalsIgnoreCase(key)) return c;
            }
            // Explicit but unknown — still use it so PR-3 UI doesn't
            // get stuck when the learner clicks a concept tag we
            // haven't extracted yet.
            return requested.trim();
        }
        return ch.concepts().isEmpty() ? null : ch.concepts().get(0);
    }

    // ── /pdf — serve original bytes ─────────────────────────────────

    /**
     * GET /api/books/{learnerId}/{bookId}/pdf
     *
     * Streams the original uploaded PDF back to the browser with
     * {@code Content-Type: application/pdf} + inline disposition so
     * the browser's built-in PDF viewer renders it in-place. Combined
     * with a {@code #page=N} URL fragment from the UI ("Open at p. 80"),
     * the viewer scrolls straight to the right page.
     *
     * <p>Heavy payloads (Murphy's PML is ~98 MB) are sent as a single
     * {@code byte[]} for simplicity — Spring buffers and Tomcat
     * chunked-transfer handle the streaming. For multi-GB textbooks
     * we'd want a {@code StreamingResponseBody} version; out of scope
     * for v1.
     *
     * <p>Status codes
     * <ul>
     *   <li>200 — PDF bytes</li>
     *   <li>404 — unknown book/learner, OR a book uploaded before this
     *       feature shipped (no bytes persisted)</li>
     * </ul>
     */
    @GetMapping("/{learnerId}/{bookId}/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable String learnerId,
            @PathVariable String bookId) {
        Book b = repo.findById(bookId).orElse(null);
        if (b == null || !learnerId.equals(b.learnerId)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = repo.findPdfBytes(bookId);
        if (bytes == null || bytes.length == 0) return ResponseEntity.notFound().build();

        String safeName = (b.title != null && !b.title.isBlank() ? b.title : "book")
            .replaceAll("[^a-zA-Z0-9 .-]", "_") + ".pdf";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentLength(bytes.length);
        // inline → browser viewer renders the PDF; attachment would
        // force-download. We want viewer behaviour for the "Open PDF
        // at page N" affordance.
        headers.setContentDisposition(org.springframework.http.ContentDisposition
            .inline().filename(safeName).build());
        // Cache the PDF aggressively in the browser — bytes never
        // change after upload, so subsequent jumps to different pages
        // hit the local cache instead of streaming from the server.
        headers.setCacheControl("private, max-age=86400, immutable");
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

    // ── DELETE ───────────────────────────────────────────────────────

    /**
     * DELETE /api/books/{learnerId}/{bookId} — hard delete. Used by an
     * admin tool / cleanup path; learners can't trigger this from the UI
     * in PR-1 (no delete button). Ownership-checked so a learner can't
     * delete another learner's book if the id leaks.
     */
    @DeleteMapping("/{learnerId}/{bookId}")
    public ResponseEntity<Void> delete(
            @PathVariable String learnerId,
            @PathVariable String bookId) {
        Book b = repo.findById(bookId).orElse(null);
        if (b == null) return ResponseEntity.notFound().build();
        if (!learnerId.equals(b.learnerId)) return ResponseEntity.status(403).build();
        repo.deleteById(bookId);
        // Drop the embedded chunks too — stale vectors in the memory
        // store would leak into another book's retrievals if a future
        // learner reuses the same bookId by accident.
        try { indexer.deindexBook(bookId); }
        catch (RuntimeException ignored) { /* best-effort cleanup */ }
        return ResponseEntity.noContent().build();
    }
}
