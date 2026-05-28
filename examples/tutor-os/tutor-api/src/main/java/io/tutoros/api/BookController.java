package io.tutoros.api;

import io.tutoros.book.Book;
import io.tutoros.book.BookExtractionService;
import io.tutoros.book.BookRepository;
import io.tutoros.book.Chapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
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

    public BookController(BookExtractionService extractor, BookRepository repo) {
        this.extractor = extractor;
        this.repo      = repo;
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
