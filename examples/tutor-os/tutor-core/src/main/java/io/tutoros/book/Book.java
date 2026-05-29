package io.tutoros.book;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A learner-uploaded textbook, segmented into {@link Chapter}s at
 * ingest time by {@code BookExtractionService}.
 *
 * <p>One Book represents the whole PDF; chapters are the unit the UI
 * + BookCoach agent (PR-2) operate on. Identity is
 * {@code (learnerId, bookId)} so multiple learners can each upload
 * their own copy of the same textbook without collision.
 *
 * <p>Persisted in selfhost mode by {@code JdbcBookStore} (recipe from
 * M3-JDBC). In-process otherwise.
 */
public class Book {

    /**
     * Server-assigned identifier — the BookExtractionService generates
     * a short slug ({@code book_lka9zb}) so URLs stay friendly without
     * exposing raw UUIDs in the address bar.
     */
    public String id;

    public String learnerId;

    /**
     * Scoping subject ({@code Mathematics}, {@code Biology}, …) so a
     * book uploaded under Biology doesn't surface in a Mathematics
     * review pull. Lower-cased at ingest to match the PR-A subject
     * isolation key.
     */
    public String subject;

    /** Learner-facing book title — extracted from the PDF or the upload form. */
    public String title;

    /** Optional author. Empty/null when not detected. */
    public String author;

    /** Total page count as reported by PDFBox. */
    public int totalPages;

    public Instant uploadedAt;

    /** Chapters in document order. */
    public List<Chapter> chapters = new ArrayList<>();

    /**
     * Original PDF bytes — stored so the UI can offer a "View original"
     * affordance and the learner can read the textbook directly.
     *
     * <p>Lazy-loaded: list and detail endpoints intentionally do NOT
     * include this in their response payloads (it can be hundreds of
     * MB). Only the dedicated {@code GET /api/books/.../pdf} endpoint
     * streams the bytes.
     *
     * <p>Null for books uploaded before the PDF-bytes feature shipped
     * — the UI surfaces that as "re-upload to enable PDF view"
     * rather than a broken-looking button.
     */
    public byte[] pdfBytes;

    /** Read-only view of chapters. */
    public List<Chapter> chapters() {
        return chapters == null ? Collections.emptyList()
                                : Collections.unmodifiableList(chapters);
    }

    /**
     * 1-based chapter lookup. Returns null when {@code n} is out of
     * range so the controller can map to 404 without throwing.
     */
    public Chapter chapter(int number) {
        if (chapters == null) return null;
        for (Chapter c : chapters) if (c.number == number) return c;
        return null;
    }
}
