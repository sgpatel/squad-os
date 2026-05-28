package io.tutoros.api;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.tutoros.agent.BookCoachAgent;
import io.tutoros.book.Book;
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

    public BookController(BookExtractionService extractor,
                          BookRepository repo,
                          SquadContext ctx,
                          BookCoachAgent coach,
                          MasteryService mastery) {
        this.extractor = extractor;
        this.repo      = repo;
        this.ctx       = ctx;
        this.coach     = coach;
        this.mastery   = mastery;
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
        String prompt = coach.buildPrompt(
            normalisedMode,
            b.title, ch.title, ch.body,
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
        }
        return ResponseEntity.ok(insight);
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
        return ResponseEntity.noContent().build();
    }
}
