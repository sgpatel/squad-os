package io.tutoros.book;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BookExtractionService — converts an uploaded textbook PDF into a
 * structured {@link Book} with chapter-level segmentation and lightweight
 * concept extraction.
 *
 * <p>Two stages, separated so each is testable in isolation:
 * <ol>
 *   <li><b>PDF → per-page plain text</b> via Apache PDFBox. Pure Java,
 *       no native deps, ~6 MiB books extract in &lt;1s. The byte-level
 *       cap of {@link #MAX_BYTES} is enforced here so a corrupted or
 *       hostile upload can't blow the heap.</li>
 *   <li><b>Plain text → Book</b> via {@link #buildBookFromPages}. This
 *       half doesn't touch PDFBox, so chapter-detection regression
 *       tests run on synthetic strings without ever creating a PDF.</li>
 * </ol>
 *
 * <h2>Chapter detection</h2>
 *
 * Layered heading matching. The pattern that fires first wins, but
 * the layers are checked top-down (more confident first):
 *
 * <ol>
 *   <li>{@code Chapter 3} or {@code CHAPTER III: …}</li>
 *   <li>{@code Unit 7} / {@code Module 7} / {@code Lesson 7}</li>
 *   <li>Standalone numbered headings ({@code 3. Photosynthesis} on its
 *       own line, ALL-CAPS or Title Case)</li>
 * </ol>
 *
 * If a book has fewer than {@link #MIN_DETECTED_CHAPTERS} headings
 * after all three layers run, the service falls back to a
 * <i>page-bucket split</i>: divide the total page range into
 * {@link #FALLBACK_BUCKETS} equal segments and call each one
 * {@code "Pages X–Y"}. This keeps the loop closed for poorly-formatted
 * scans where the OCR or PDF layout didn't preserve heading style.
 *
 * <h2>Concept extraction</h2>
 *
 * Heuristic, not LLM-driven (PR-1 is the foundation; PR-2 adds the
 * LLM-aware learning loop on top). We pick up {@link #MAX_CONCEPTS_PER_CHAPTER}
 * candidate concepts per chapter using a noun-phrase regex tuned for
 * textbook prose ({@code Capitalised Words (in sequences of 2–4)} and
 * {@code italicised-style terms} on their own line). De-duplicated
 * lower-cased so they're a clean key for the M3 mastery graph.
 */
public class BookExtractionService {

    /** 32 MiB — books are bigger than syllabi (PR-D was 8 MiB). */
    public static final long MAX_BYTES = 32L * 1024 * 1024;

    /**
     * If the layered detector produces fewer chapters than this, we
     * give up on text-based heading detection and fall back to the
     * page-bucket split. Three is the threshold because a one- or
     * two-chapter "book" is almost always a parse failure rather than
     * a real short book.
     */
    static final int MIN_DETECTED_CHAPTERS = 3;

    /** Number of buckets the fallback page split produces. */
    static final int FALLBACK_BUCKETS = 8;

    /** Cap on per-chapter concept extraction. */
    static final int MAX_CONCEPTS_PER_CHAPTER = 8;

    // ── Pre-compiled patterns ────────────────────────────────────────

    /** Chapter heading — most confident layer. */
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "^\\s*(?:CHAPTER|Chapter)\\s+([0-9IVXLCDM]+)\\s*[:.\\-—]?\\s*(.*)$",
        Pattern.MULTILINE);

    /** Unit / Module / Lesson — second layer. */
    private static final Pattern UNIT_PATTERN = Pattern.compile(
        "^\\s*(?:UNIT|Unit|MODULE|Module|LESSON|Lesson|PART|Part)\\s+([0-9IVXLCDM]+)\\s*[:.\\-—]?\\s*(.*)$",
        Pattern.MULTILINE);

    /**
     * Standalone numbered heading: "3. Title", "3.0 Title", "3) Title".
     * Anchored to start of line + must be followed by a capitalised
     * word to avoid matching prose like "see 3. above".
     */
    private static final Pattern NUMBERED_PATTERN = Pattern.compile(
        "^\\s*(\\d{1,2})(?:\\.\\d+)?[.)]\\s+([A-Z][\\w\\- ,]{2,80})\\s*$",
        Pattern.MULTILINE);

    /** Capitalised noun-phrase (2–4 words) used for concept extraction. */
    private static final Pattern CONCEPT_PATTERN = Pattern.compile(
        "\\b([A-Z][a-z]{3,}(?:\\s+[A-Z][a-z]{3,}){1,3})\\b");

    /** PDFBox sticks a form-feed between pages — used to compute page ranges. */
    private static final String PAGE_SEP = "\f";

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Full ingest path: PDF bytes → segmented {@link Book}. Throws on
     * empty / oversized uploads. The result has {@code id} set to a
     * fresh slug; callers persist via {@code BookRepository.save}.
     */
    public Book extract(byte[] bytes,
                        String learnerId,
                        String subject,
                        String title,
                        String author) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty upload");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                "File too large (" + bytes.length + " bytes, max " + MAX_BYTES + ")");
        }
        List<String> pages = pdfPages(bytes);
        Book book = buildBookFromPages(pages, learnerId, subject, title, author);
        book.id = freshBookId();
        book.uploadedAt = Instant.now();
        return book;
    }

    // ── Stage 1: PDF → pages ─────────────────────────────────────────

    /**
     * Per-page text via PDFBox. Sort-by-position keeps multi-column
     * scans readable; the form-feed page separator is preserved here
     * (stripped later by {@code normalise}).
     */
    static List<String> pdfPages(byte[] bytes) throws IOException {
        List<String> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int total = doc.getNumberOfPages();
            for (int i = 1; i <= total; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(normalise(stripper.getText(doc)));
            }
        }
        return pages;
    }

    // ── Stage 2: pages → Book ────────────────────────────────────────

    /**
     * Pure function — no I/O, no PDFBox. Takes the per-page text and
     * produces a Book with chapter segmentation + concept extraction.
     * Package-private so {@code BookExtractionServiceTest} can exercise
     * every chapter-detection branch without spinning up a real PDF.
     */
    Book buildBookFromPages(List<String> pages,
                            String learnerId,
                            String subject,
                            String title,
                            String author) {
        Book book = new Book();
        book.learnerId  = learnerId;
        book.subject    = subject != null ? subject.trim().toLowerCase() : null;
        book.title      = title != null ? title.trim() : "Untitled";
        book.author     = author != null ? author.trim() : "";
        book.totalPages = pages.size();

        // Build cumulative offsets so heading positions map back to pages.
        StringBuilder full = new StringBuilder();
        int[] pageEnds = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            full.append(pages.get(i));
            // Trailing newline guarantees ^ anchors land at page boundaries.
            if (!pages.get(i).endsWith("\n")) full.append('\n');
            pageEnds[i] = full.length();
        }
        String text = full.toString();

        // Run the three detection layers until one yields enough headings.
        List<Heading> headings = detectHeadings(text);
        if (headings.size() < MIN_DETECTED_CHAPTERS) {
            book.chapters = fallbackBucketChapters(pages);
        } else {
            book.chapters = chaptersFromHeadings(headings, text, pageEnds);
        }
        return book;
    }

    /**
     * Three-layer detection. Each layer returns its full hit set; the
     * first layer that meets {@link #MIN_DETECTED_CHAPTERS} wins so a
     * book using "Chapter N" doesn't get its headings polluted by
     * trailing "3. See also" matches from the lower layers.
     */
    static List<Heading> detectHeadings(String text) {
        List<Heading> h = matchAll(text, CHAPTER_PATTERN);
        if (h.size() >= MIN_DETECTED_CHAPTERS) return h;

        h = matchAll(text, UNIT_PATTERN);
        if (h.size() >= MIN_DETECTED_CHAPTERS) return h;

        return matchAll(text, NUMBERED_PATTERN);
    }

    private static List<Heading> matchAll(String text, Pattern p) {
        List<Heading> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            String num   = m.group(1) != null ? m.group(1).trim() : String.valueOf(out.size() + 1);
            String label = m.group(2) != null ? m.group(2).trim() : "";
            String full  = (label.isBlank()
                ? "Chapter " + num
                : "Chapter " + num + ": " + label).trim();
            out.add(new Heading(m.start(), full));
        }
        return out;
    }

    /**
     * Convert headings into Chapter records. Each chapter's body runs
     * from one heading to the next (or end of book). Page ranges come
     * from binary searching the cumulative {@code pageEnds} offsets.
     */
    private static List<Chapter> chaptersFromHeadings(
            List<Heading> headings, String text, int[] pageEnds) {
        List<Chapter> out = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            Heading h = headings.get(i);
            int from  = h.offset;
            int to    = (i + 1 < headings.size()) ? headings.get(i + 1).offset : text.length();
            // Skip the heading line itself when carving the body.
            int bodyStart = text.indexOf('\n', from);
            if (bodyStart < 0 || bodyStart > to) bodyStart = from;
            String body = text.substring(bodyStart, to).trim();

            Chapter c = new Chapter();
            c.number    = i + 1;
            c.title     = h.title;
            c.body      = trimBody(body);
            c.summary   = Chapter.defaultSummary(c.body);
            c.pageStart = pageOf(from, pageEnds);
            c.pageEnd   = pageOf(Math.max(from, to - 1), pageEnds);
            c.setConcepts(extractConcepts(body));
            out.add(c);
        }
        return out;
    }

    /**
     * Fallback when heading detection fails — split the page list into
     * equal buckets. Titles are page-range labels so the learner can
     * still pick a sensible-feeling chunk to study.
     */
    static List<Chapter> fallbackBucketChapters(List<String> pages) {
        List<Chapter> out = new ArrayList<>();
        if (pages.isEmpty()) return out;
        int buckets = Math.min(FALLBACK_BUCKETS, Math.max(1, pages.size()));
        int per = (int) Math.ceil(pages.size() / (double) buckets);
        for (int b = 0; b < buckets; b++) {
            int p0 = b * per;
            int p1 = Math.min(pages.size(), p0 + per);
            if (p0 >= p1) break;
            StringBuilder body = new StringBuilder();
            for (int p = p0; p < p1; p++) {
                body.append(pages.get(p));
                if (!pages.get(p).endsWith("\n")) body.append('\n');
            }
            Chapter c = new Chapter();
            c.number    = b + 1;
            c.title     = "Pages " + (p0 + 1) + "–" + p1;
            c.body      = trimBody(body.toString().trim());
            c.summary   = Chapter.defaultSummary(c.body);
            c.pageStart = p0 + 1;
            c.pageEnd   = p1;
            c.setConcepts(extractConcepts(c.body));
            out.add(c);
        }
        return out;
    }

    /**
     * 1-based page lookup via {@code pageEnds} (cumulative end offsets).
     * Linear scan is fine — typical books are &lt;500 pages.
     */
    private static int pageOf(int charOffset, int[] pageEnds) {
        for (int i = 0; i < pageEnds.length; i++) {
            if (charOffset < pageEnds[i]) return i + 1;
        }
        return Math.max(1, pageEnds.length);
    }

    /** Truncate body at {@link Chapter#MAX_BODY_CHARS}, preferring a sentence break. */
    private static String trimBody(String body) {
        if (body == null) return "";
        if (body.length() <= Chapter.MAX_BODY_CHARS) return body;
        int cut = Chapter.MAX_BODY_CHARS;
        for (int i = cut; i > Chapter.MAX_BODY_CHARS - 400 && i > 0; i--) {
            char c = body.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') { cut = i + 1; break; }
        }
        return body.substring(0, Math.min(body.length(), cut)).trim() + "\n…";
    }

    /**
     * Pick up to {@link #MAX_CONCEPTS_PER_CHAPTER} concept tags. Lower-
     * cased + de-duplicated. Ordered by first occurrence so the most
     * narratively prominent terms surface first.
     */
    static List<String> extractConcepts(String body) {
        if (body == null || body.isBlank()) return Collections.emptyList();
        LinkedHashSet<String> hits = new LinkedHashSet<>();
        Matcher m = CONCEPT_PATTERN.matcher(body);
        while (m.find() && hits.size() < MAX_CONCEPTS_PER_CHAPTER * 3) {
            String phrase = m.group(1).trim().toLowerCase();
            // Filter out very common stop-phrases that the regex
            // grabs because they're title-cased at line starts.
            if (phrase.startsWith("the ") || phrase.startsWith("this ")
             || phrase.startsWith("that ") || phrase.startsWith("these ")
             || phrase.startsWith("those ") || phrase.startsWith("there ")) {
                continue;
            }
            hits.add(phrase);
        }
        List<String> out = new ArrayList<>(hits);
        if (out.size() > MAX_CONCEPTS_PER_CHAPTER) {
            return new ArrayList<>(out.subList(0, MAX_CONCEPTS_PER_CHAPTER));
        }
        return out;
    }

    /** Strip form-feed page separators that inflate token count later. */
    static String normalise(String raw) {
        if (raw == null) return "";
        return raw.replace(PAGE_SEP, "\n").replace("\r", "").trim();
    }

    /** Short URL-friendly book id. */
    private static String freshBookId() {
        return "book_" + Long.toString(System.currentTimeMillis(), 36)
            + "_" + Long.toString((long) (Math.random() * 0xffff), 36);
    }

    /** Internal heading record. */
    private record Heading(int offset, String title) {}
}
