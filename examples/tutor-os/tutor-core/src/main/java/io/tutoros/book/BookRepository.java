package io.tutoros.book;

import java.util.List;
import java.util.Optional;

/**
 * BookRepository — persistence SPI for uploaded textbooks.
 *
 * <p>Same shape as the M3 {@code MasteryGraphStore} and M1
 * {@code MemoryStore} contracts so callers don't have to learn a new
 * shape per pillar. PR-1 ships an in-process default + a JDBC variant
 * driven by {@code tutor.book.store=jdbc}.
 *
 * <p>The store is the dumb persistence layer — it doesn't parse PDFs,
 * compute concept scores, or pick chapters. Those live in
 * {@code BookExtractionService} (ingest) and the upcoming
 * {@code BookCoachAgent} (learning, PR-2). The store just round-trips
 * {@link Book} rows.
 *
 * <p>Threading: implementations MUST be safe for concurrent reads. In
 * PR-1 the only writer is the upload endpoint (one row per upload),
 * so write contention isn't a v1 concern.
 */
public interface BookRepository {

    /** Persist or replace. Identity is {@link Book#id}. */
    void save(Book book);

    /** Lookup by book id. Empty when unknown. */
    Optional<Book> findById(String bookId);

    /**
     * All books owned by a learner, newest first. Used by the library
     * page so learners can pick which book to study.
     */
    List<Book> listForLearner(String learnerId);

    /**
     * Learner + subject filter — feeds the per-subject library and
     * keeps PR-A's subject isolation honest (a Mathematics book never
     * surfaces in a Biology session).
     */
    List<Book> listForSubject(String learnerId, String subject);

    /** Hard delete. Used by an admin path; not exposed to learners yet. */
    void deleteById(String bookId);

    /** Total books for a learner — cheaper than {@code listForLearner().size()} on JDBC. */
    int countForLearner(String learnerId);

    /**
     * Read just the original PDF bytes for one book. Separated from
     * {@link #findById} so the heavy payload isn't loaded when we
     * only need metadata + chapters. Returns null when the book is
     * unknown or was uploaded before the PDF-bytes feature shipped
     * (in-process: byte[] never persisted across restarts;
     * JDBC: pre-migration rows have pdf_bytes IS NULL).
     */
    default byte[] findPdfBytes(String bookId) {
        return findById(bookId).map(b -> b.pdfBytes).orElse(null);
    }
}
