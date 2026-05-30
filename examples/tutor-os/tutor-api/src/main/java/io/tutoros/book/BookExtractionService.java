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

    /**
     * 128 MiB — sized for college-level textbooks (Kevin P. Murphy's
     * "Probabilistic Machine Learning" is ~98 MB; physics/chemistry
     * reference works land in the same range). The compose nginx
     * (client_max_body_size) and Spring multipart caps in
     * application.properties match this number — they all have to
     * line up or the upload fails at the smallest cap.
     *
     * Books larger than this should be chunked or compressed first;
     * extraction time + PDFBox heap usage grow roughly linearly with
     * file size, so an unbounded cap would be a DoS surface.
     */
    public static final long MAX_BYTES = 128L * 1024 * 1024;

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

    /**
     * Max number of consecutive non-entry lines tolerated inside a Table
     * of Contents block before we decide the TOC has ended. Running
     * headers / footers ("viii BRIEF CONTENTS", a copyright line) show up
     * as one or two stray lines between real entries, so a small window
     * keeps the block contiguous without bleeding into body text.
     */
    private static final int TOC_GAP_LIMIT = 8;

    /** Hard cap on lines scanned while parsing a TOC block. */
    private static final int TOC_MAX_LINES = 800;

    /**
     * Safety net for textbooks where heading detection succeeds at the
     * top level but misses sub-sections — e.g. Murphy's "Probabilistic
     * Machine Learning" where each chapter spans 50+ pages but the
     * sub-section style ("2.1 Random variables") didn't initially
     * match the standalone-numbered-heading regex.
     *
     * <p>If any detected chapter spans more pages than this, we replace
     * it with N page-bucket sub-segments so a learner doesn't end up
     * with one 290-page "chapter" that drowns out any specific concept
     * in the LLM context window.
     */
    static final int CHAPTER_PAGE_SAFETY = 40;

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
     * Standalone numbered heading. Matches all of these on their own line:
     *
     *   3. Title              (legacy single-level, trailing period)
     *   3.1 Title             (dotted-decimal, NO trailing period — Murphy's
     *                          PML book uses this)
     *   3.1.4 Title           (three-level dotted decimal)
     *   3) Title              (alternate single-level)
     *
     * The capture group keeps the FULL numeric prefix (e.g. "3.1.4") so
     * downstream code can detect hierarchy if it ever wants to. The
     * trailing-period requirement of the previous regex was the reason
     * Murphy's "2.1 Random variables" never matched and chapter 2
     * ballooned to 290 pages.
     */
    private static final Pattern NUMBERED_PATTERN = Pattern.compile(
        "^\\s*(\\d{1,2}(?:\\.\\d{1,2}){0,3})[.)]?\\s+([A-Z][\\w\\- ,'’]{2,80})\\s*$",
        Pattern.MULTILINE);

    /** Capitalised noun-phrase (2–4 words) used for concept extraction. */
    private static final Pattern CONCEPT_PATTERN = Pattern.compile(
        "\\b([A-Z][a-z]{3,}(?:\\s+[A-Z][a-z]{3,}){1,3})\\b");

    // ── Table-of-Contents patterns (layer 0, most authoritative) ─────

    /** A "Contents" / "Brief Contents" / "Table of Contents" heading line. */
    private static final Pattern TOC_HEADING = Pattern.compile(
        "(?i)^(brief contents|table of contents|contents)$");

    /**
     * A chapter line in a TOC: a 1–2 digit chapter number, a title, then
     * a trailing printed page number. The title is non-greedy so the LAST
     * run of digits is taken as the page. Dotted sub-section numbers
     * ("2.1 …") never match because "2" must be followed by whitespace.
     */
    private static final Pattern TOC_CHAPTER = Pattern.compile(
        "^(\\d{1,2})\\s+(\\p{L}.*?)\\s+(\\d{1,4})$");

    /** A Part divider in a TOC: a Roman numeral, a title, a page. Skipped. */
    private static final Pattern TOC_PART = Pattern.compile(
        "^([IVXLCDM]{1,5})\\s+(\\p{L}.*?)\\s+(\\d{1,4})$");

    /** An appendix line: a single (non-Roman) capital letter, title, page. */
    private static final Pattern TOC_APPENDIX = Pattern.compile(
        "^([A-Z])\\s+(\\p{L}.*?)\\s+(\\d{1,4})$");

    /**
     * Words that disqualify a candidate concept phrase if they appear
     * anywhere in it. The capitalised-noun-phrase regex is structurally
     * blind, so it happily grabs figure/table references ("From Figure",
     * "Virginica Table"), structural headings ("Introduction Estimate …"),
     * and figure-credit boilerplate that surrounds author names ("Used
     * with kind permission of …"). Rejecting any phrase containing one of
     * these keeps the "Anchored on" tags — and the mastery-graph keys they
     * become — to actual subject-matter terms.
     */
    private static final Set<String> CONCEPT_STOPWORDS = Set.of(
        // discourse glue that gets Title-cased at line/sentence starts
        "the", "this", "that", "these", "those", "there",
        "from", "using", "used", "suppose", "consider", "recall", "let",
        "thus", "hence", "where", "when", "given", "above", "below",
        "following", "here", "then", "with", "into", "over", "your",
        // structural / reference nouns. NB: words that often form real
        // concept names (theorem, lemma, algorithm, definition, …) are
        // deliberately NOT here — "Central Limit Theorem" / "EM Algorithm"
        // are concepts, whereas "Figure 2.3" / "Table 4" are pure refs.
        "figure", "figures", "table", "tables", "section", "sections",
        "chapter", "chapters", "equation", "equations", "example", "examples",
        "appendix", "exercise", "exercises", "page", "pages",
        "part", "volume", "edition", "introduction", "conclusion", "summary",
        "left", "right", "top", "bottom", "middle", "row", "column",
        // figure-credit boilerplate that brackets author names
        "permission", "courtesy", "kind", "adapted", "reproduced",
        "source", "credit", "author", "copyright", "press", "license");

    /** A sub-section line ("2.1 …", "2.6.4 …") — present only in the detailed TOC. Skipped. */
    private static final Pattern TOC_DOTTED = Pattern.compile(
        "^\\d{1,2}\\.\\d.*");

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
        // Retain a copy of the original PDF so the UI can stream it
        // back to the learner. Defensive clone — callers might reuse
        // the byte[] for other purposes after extract() returns.
        book.pdfBytes = bytes.clone();
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

        // ── Layer 0: Table of Contents (most authoritative) ──────────
        // A well-formed textbook lists its real chapters in a "Contents"
        // / "Brief Contents" block. Parsing that gives the true chapter
        // titles and order — and crucially avoids the failure mode where
        // the regex layers latch onto coarse "Part I … V" dividers (or
        // prose that mentions "Part II") and shred a 300-page Part into
        // arbitrary page windows. We keep these chapters whole (no
        // oversize split) because their boundaries are real, not guessed.
        List<Chapter> toc = chaptersFromToc(text, pages, pageEnds);
        if (toc != null && toc.size() >= MIN_DETECTED_CHAPTERS) {
            book.chapters = toc;
        } else {
            // Run the three detection layers until one yields enough headings.
            List<Heading> headings = detectHeadings(text);
            if (headings.size() < MIN_DETECTED_CHAPTERS) {
                book.chapters = fallbackBucketChapters(pages);
            } else {
                book.chapters = chaptersFromHeadings(headings, text, pageEnds);
                // Safety net: replace any detected chapter that spans more
                // pages than CHAPTER_PAGE_SAFETY with page-bucket sub-segments.
                // Catches the failure mode where top-level chapter detection
                // works but sub-section detection misses, leaving the learner
                // with one 290-page mega-chapter whose body excerpt is too
                // generic for the LLM to ground on.
                book.chapters = splitOversizedChapters(book.chapters, pages);
            }
        }

        // Re-derive concept tags now that the book title/subject are known,
        // so the running footer (the book's own title) and structural words
        // ("From Figure", "Virginica Table") don't pollute the "Anchored on"
        // tags or the mastery-graph keys they become.
        for (Chapter c : book.chapters) {
            c.setConcepts(extractConcepts(c.body, book.title, book.subject));
        }
        return book;
    }

    /**
     * Detect any chapter that's wider than {@link #CHAPTER_PAGE_SAFETY}
     * and replace it with a sequence of page-bucket sub-chapters. The
     * label format makes the split visible to the learner so they
     * understand why a "chapter 2" became "Chapter 2 — Section 1",
     * "Section 2", etc.
     *
     * <p>Re-numbers chapters across the whole result so the UI gets
     * a contiguous 1..N sequence after expansion.
     */
    static List<Chapter> splitOversizedChapters(List<Chapter> in, List<String> pages) {
        List<Chapter> out = new ArrayList<>();
        for (Chapter ch : in) {
            int span = ch.pageEnd - ch.pageStart + 1;
            if (span <= CHAPTER_PAGE_SAFETY) {
                out.add(ch);
                continue;
            }
            // How many sub-segments? Choose so each is ~CHAPTER_PAGE_SAFETY/2
            // wide — keeps focus tight without producing 50 sub-chapters
            // for a 300-page mega-chapter.
            int buckets = Math.max(2, (int) Math.ceil(span / (double) (CHAPTER_PAGE_SAFETY / 2)));
            int perBucket = (int) Math.ceil(span / (double) buckets);
            for (int b = 0; b < buckets; b++) {
                int p0 = ch.pageStart + b * perBucket;
                int p1 = Math.min(ch.pageEnd, p0 + perBucket - 1);
                if (p0 > ch.pageEnd) break;
                // Slice the page-text we already have rather than the
                // (already-truncated) chapter body — the body cap may
                // have eaten content we need here.
                int pIdx0 = Math.max(0, p0 - 1);
                int pIdx1 = Math.min(pages.size(), p1);
                StringBuilder body = new StringBuilder();
                for (int p = pIdx0; p < pIdx1; p++) {
                    body.append(pages.get(p));
                    if (!pages.get(p).endsWith("\n")) body.append('\n');
                }
                Chapter sub = new Chapter();
                sub.number    = 0;                 // re-numbered below
                sub.title     = ch.title + " — Section " + (b + 1) +
                                " (pp. " + p0 + "–" + p1 + ")";
                sub.body      = trimBody(body.toString().trim());
                sub.summary   = Chapter.defaultSummary(sub.body);
                sub.pageStart = p0;
                sub.pageEnd   = p1;
                sub.setConcepts(extractConcepts(sub.body));
                out.add(sub);
            }
        }
        // Re-number all chapters contiguously so the UI shows a clean
        // 1..N sequence after the safety split fires.
        for (int i = 0; i < out.size(); i++) out.get(i).number = i + 1;
        return out;
    }

    // ── Layer 0: Table-of-Contents detection ─────────────────────────

    /**
     * Parse the book's Table of Contents into real chapters, then locate
     * each chapter's start in the body so we get accurate page ranges.
     *
     * <p>Returns {@code null} (so the caller falls back to the regex
     * layers) when there's no usable "Contents" block or fewer than
     * {@link #MIN_DETECTED_CHAPTERS} entries are found.
     *
     * <p>Why this is the top layer: a TOC lists the <i>actual</i> chapters
     * an author intended, in order, with titles. The lower regex layers
     * can be fooled by Part dividers ("Part I Foundations") or running
     * headers; the TOC cannot.
     */
    static List<Chapter> chaptersFromToc(String text, List<String> pages, int[] pageEnds) {
        TocResult toc = parseToc(text);
        if (toc == null || toc.entries.size() < MIN_DETECTED_CHAPTERS) return null;

        List<TocEntry> entries = toc.entries;
        // Locate each chapter's start offset in the body with a forward-only
        // cursor so the matches stay in reading order and a title that also
        // appears in earlier prose can't drag a chapter backwards.
        String lower = text.toLowerCase();
        int[] starts = new int[entries.size()];
        int cursor = toc.endOffset;
        for (int i = 0; i < entries.size(); i++) {
            int off = locateChapterStart(lower, entries.get(i), cursor);
            starts[i] = off;
            cursor = off + 1;
        }

        List<Chapter> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int from = starts[i];
            int to   = (i + 1 < entries.size()) ? starts[i + 1] : text.length();
            if (to < from) to = from;
            // Skip the heading/running-header line when carving the body.
            int bodyStart = text.indexOf('\n', from);
            if (bodyStart < 0 || bodyStart > to) bodyStart = from;
            String body = text.substring(bodyStart, to).trim();

            Chapter c = new Chapter();
            c.number    = i + 1;
            c.title     = tocTitleLabel(entries.get(i).num, entries.get(i).title);
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
     * Scan from the first "Contents" heading and collect chapter entries.
     * Part dividers (Roman numerals) and dotted sub-sections are skipped;
     * appendix letters are kept. Stops at the next "Contents" heading, a
     * chapter-number reset (the detailed TOC repeating the brief one), or
     * a run of {@link #TOC_GAP_LIMIT} stray lines.
     */
    static TocResult parseToc(String text) {
        // TOC_HEADING is anchored to a full line, so walk line by line to
        // find the first "Contents" heading.
        int regionStart = -1;
        for (int scanPos = 0; scanPos <= text.length(); ) {
            int nl = text.indexOf('\n', scanPos);
            int end = nl < 0 ? text.length() : nl;
            if (TOC_HEADING.matcher(text.substring(scanPos, end).trim()).matches()) {
                regionStart = Math.min(end + 1, text.length());
                break;
            }
            if (nl < 0) break;
            scanPos = nl + 1;
        }
        if (regionStart < 0) return null;

        List<TocEntry> entries = new ArrayList<>();
        int lastNum = 0;
        int gap = 0;
        int lines = 0;
        int endOffset = regionStart;
        for (int pos = regionStart; pos <= text.length() && lines < TOC_MAX_LINES; ) {
            int nl = text.indexOf('\n', pos);
            int end = nl < 0 ? text.length() : nl;
            String line = text.substring(pos, end).trim();
            lines++;
            boolean atEnd = nl < 0;
            // Cursor for the NEXT iteration; we may `break` before using it.
            int next = atEnd ? text.length() + 1 : nl + 1;

            if (line.isEmpty()) { pos = next; continue; }

            // A second "Contents" heading marks the detailed TOC — stop
            // once we already have a brief block in hand.
            if (!entries.isEmpty() && TOC_HEADING.matcher(line).matches()) break;

            // Sub-section ("2.1 …") or Part divider ("I Foundations 31") —
            // skip without counting as a gap so long runs of them inside a
            // detailed TOC don't prematurely end the scan.
            if (TOC_DOTTED.matcher(line).matches() || TOC_PART.matcher(line).matches()) {
                gap = 0; endOffset = end; pos = next; continue;
            }

            Matcher ch = TOC_CHAPTER.matcher(line);
            if (ch.matches()) {
                int num = Integer.parseInt(ch.group(1));
                // A non-increase means the detailed TOC has restarted at 1 —
                // we've captured the first complete run, so stop.
                if (!entries.isEmpty() && num <= lastNum) break;
                entries.add(new TocEntry(ch.group(1), ch.group(2).trim(), parseIntSafe(ch.group(3))));
                lastNum = num; gap = 0; endOffset = end; pos = next; continue;
            }

            Matcher app = TOC_APPENDIX.matcher(line);
            if (app.matches()) {
                entries.add(new TocEntry(app.group(1), app.group(2).trim(), parseIntSafe(app.group(3))));
                gap = 0; endOffset = end; pos = next; continue;
            }

            // Unrecognised line — tolerate a few (running headers/footers).
            gap++;
            if (entries.size() >= MIN_DETECTED_CHAPTERS && gap >= TOC_GAP_LIMIT) break;
            if (atEnd) break;
            pos = next;
        }
        return new TocResult(entries, Math.min(endOffset, text.length()));
    }

    /**
     * Find where a TOC chapter actually starts in the (lower-cased) body.
     * Prefers the running-header form "chapter N. Title" / "appendix L.
     * Title" — these repeat on every page of the chapter and never appear
     * in the TOC itself — then falls back to the bare title. Returns
     * {@code cursor} (contiguous fallback) when nothing matches, so
     * chapters never overlap or run backwards.
     */
    private static int locateChapterStart(String lowerText, TocEntry e, int cursor) {
        if (cursor < 0) cursor = 0;
        boolean numeric = e.num.chars().allMatch(Character::isDigit);
        String kind  = numeric ? "chapter " : "appendix ";
        String numLc = e.num.toLowerCase();
        String title = e.title.toLowerCase().replaceAll("\\s*\\*\\s*$", "").trim();

        String[] candidates = {
            kind + numLc + ". " + title,   // "chapter 2. probability: univariate models"
            kind + numLc + ".",            // "chapter 2."  (header, title may differ slightly)
            title                          // bare title (last resort)
        };
        for (String cand : candidates) {
            if (cand.isBlank()) continue;
            int idx = lowerText.indexOf(cand, cursor);
            if (idx >= 0) return idx;
        }
        return cursor;
    }

    /** Render a learner-facing chapter title from a TOC number + raw title. */
    private static String tocTitleLabel(String num, String rawTitle) {
        String t = rawTitle.replaceAll("\\s*\\*\\s*$", "").replaceAll("\\s{2,}", " ").trim();
        boolean numeric = num.chars().allMatch(Character::isDigit);
        return (numeric ? "Chapter " : "Appendix ") + num + ": " + t;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return 0; }
    }

    /** A parsed TOC chapter entry: number/letter, title, printed page. */
    private record TocEntry(String num, String title, int page) {}

    /** Parsed TOC block: ordered entries plus the offset where it ended. */
    record TocResult(List<TocEntry> entries, int endOffset) {}

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
        return extractConcepts(body, null, null);
    }

    /**
     * Concept extraction with the book's own title/subject excluded.
     *
     * <p>Selection criteria, in order:
     * <ol>
     *   <li>match the capitalised noun-phrase regex (2–4 Title-Case words);</li>
     *   <li>reject if any word is a {@link #CONCEPT_STOPWORDS} (figure/table
     *       references, structural headings, figure-credit boilerplate);</li>
     *   <li>reject if the phrase is contained in the book title or subject
     *       (kills the running footer, e.g. "probabilistic machine learning");</li>
     *   <li>rank surviving phrases by frequency (recurring subject terms beat
     *       one-off author names from figure credits), keeping first-occurrence
     *       order for ties; take the top {@link #MAX_CONCEPTS_PER_CHAPTER}.</li>
     * </ol>
     */
    static List<String> extractConcepts(String body, String title, String subject) {
        if (body == null || body.isBlank()) return Collections.emptyList();

        List<String> blockers = new ArrayList<>();
        if (title != null && !title.isBlank())   blockers.add(title.toLowerCase());
        if (subject != null && !subject.isBlank()) blockers.add(subject.toLowerCase());

        // Count occurrences so recurring concepts outrank incidental ones.
        // LinkedHashMap preserves first-occurrence order for the stable tie-break.
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        Matcher m = CONCEPT_PATTERN.matcher(body);
        int scanned = 0;
        while (m.find() && scanned < 8000) {
            scanned++;
            String phrase = m.group(1).trim().toLowerCase();
            if (!isConceptCandidate(phrase, blockers)) continue;
            counts.merge(phrase, 1, Integer::sum);
        }

        List<String> ordered = new ArrayList<>(counts.keySet());
        // Stable sort by descending frequency; ties keep insertion (first-occurrence) order.
        ordered.sort((a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        if (ordered.size() > MAX_CONCEPTS_PER_CHAPTER) {
            return new ArrayList<>(ordered.subList(0, MAX_CONCEPTS_PER_CHAPTER));
        }
        return ordered;
    }

    /** True if {@code phrase} is a plausible subject concept (not glue/reference/title). */
    private static boolean isConceptCandidate(String phrase, List<String> blockers) {
        for (String w : phrase.split("\\s+")) {
            if (CONCEPT_STOPWORDS.contains(w)) return false;
        }
        for (String b : blockers) {
            if (b.contains(phrase)) return false;
        }
        return true;
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
