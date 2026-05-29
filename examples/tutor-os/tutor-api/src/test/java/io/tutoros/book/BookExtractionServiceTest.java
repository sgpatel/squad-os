package io.tutoros.book;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BookExtractionServiceTest — exercises chapter detection + concept
 * extraction WITHOUT touching PDFBox.
 *
 * <p>The service splits ingest into two halves:
 * <ol>
 *   <li>PDF bytes → per-page plain text (PDFBox; covered indirectly)</li>
 *   <li>per-page text → segmented {@link Book} (pure regex; covered here)</li>
 * </ol>
 *
 * Routing all unit tests through the second half lets us assert
 * chapter-detection behaviour across many synthetic books in
 * milliseconds, with no PDF fixtures to maintain.
 */
class BookExtractionServiceTest {

    private static final String L = "learner-1";
    private static final String S = "Biology";

    private final BookExtractionService svc = new BookExtractionService();

    // ── Layer 1 — "Chapter N" headings ───────────────────────────────

    @Test
    void detectsChapterHeadingsAcrossMultiplePages() {
        List<String> pages = List.of(
            "Chapter 1: The Cell\nCells are the smallest unit of life.\n",
            "More about cells. Mitochondria are organelles that produce energy.\n",
            "Chapter 2: Photosynthesis\nPlants convert sunlight into glucose.\n",
            "Photosynthesis happens in chloroplasts.\n",
            "Chapter 3: Cellular Respiration\nGlucose is broken down for ATP.\n"
        );
        Book book = svc.buildBookFromPages(pages, L, S, "Bio 101", "Smith");

        assertEquals(3, book.chapters().size());
        assertEquals(1, book.chapters().get(0).number);
        assertEquals("Chapter 1: The Cell",            book.chapters().get(0).title);
        assertEquals("Chapter 2: Photosynthesis",      book.chapters().get(1).title);
        assertEquals("Chapter 3: Cellular Respiration",book.chapters().get(2).title);

        // Page ranges should align with where each chapter heading lives.
        assertEquals(1, book.chapters().get(0).pageStart);
        assertEquals(3, book.chapters().get(1).pageStart);
        assertEquals(5, book.chapters().get(2).pageStart);

        // Body shouldn't include the heading itself (cut at the newline).
        assertFalse(book.chapters().get(0).body.startsWith("Chapter 1"));
        assertTrue(book.chapters().get(0).body.contains("smallest unit of life"));
    }

    // ── Layer 2 — Unit / Module / Lesson ─────────────────────────────

    @Test
    void detectsUnitHeadingsWhenChapterAbsent() {
        List<String> pages = List.of(
            "Unit 1: Algebra\nVariables represent unknown values.\n",
            "Unit 2: Geometry\nA triangle has three sides.\n",
            "Unit 3: Statistics\nMean is the average of a dataset.\n"
        );
        Book book = svc.buildBookFromPages(pages, L, "Maths", "Math 101", null);
        assertEquals(3, book.chapters().size());
        assertTrue(book.chapters().get(0).title.contains("Algebra"));
        assertTrue(book.chapters().get(1).title.contains("Geometry"));
    }

    // ── Layer 3 — numbered standalone headings ───────────────────────

    @Test
    void detectsNumberedStandaloneHeadings() {
        // No "Chapter"/"Unit" keywords — third layer must fire.
        List<String> pages = List.of(
            "1. Introduction\nWhat is this book about?\n",
            "2. Fundamentals\nThe basics of the field.\n",
            "3. Advanced Topics\nHarder material for later study.\n",
            "4. Conclusion\nWhat we covered.\n"
        );
        Book book = svc.buildBookFromPages(pages, L, S, "Sample", null);
        assertEquals(4, book.chapters().size());
        assertTrue(book.chapters().get(0).title.endsWith("Introduction"));
        assertTrue(book.chapters().get(2).title.endsWith("Advanced Topics"));
    }

    @Test
    void detectsDottedDecimalSubsectionsWithoutTrailingPeriod() {
        // Murphy's PML and many textbooks use "2.1 Title" (no trailing
        // period) for sub-sections. Previous regex required `[.)]`
        // after the number and would miss this entirely — chapters
        // ended up dozens of pages long because sub-headings never
        // matched. This test locks the new regex in.
        List<String> pages = List.of(
            "2.1 Random Variables\nA random variable is a function...\n",
            "2.2 Bayes Rule\nBayes' rule relates conditional probabilities...\n",
            "2.3 Gaussian Distribution\nThe Gaussian (or normal) distribution is...\n",
            "2.4 Bernoulli Distribution\nFor a binary outcome...\n"
        );
        Book book = svc.buildBookFromPages(pages, L, S, "PML", null);
        assertEquals(4, book.chapters().size(),
            "dotted-decimal headings without trailing period must be detected");
        assertTrue(book.chapters().get(0).title.endsWith("Random Variables"));
        assertTrue(book.chapters().get(1).title.endsWith("Bayes Rule"));
        assertTrue(book.chapters().get(2).title.endsWith("Gaussian Distribution"));
    }

    @Test
    void detectsThreeLevelDottedDecimalHeadings() {
        // Some textbooks go three deep: "2.1.3 Title". The regex
        // should still match — capture group keeps the full numeric
        // prefix.
        List<String> pages = List.of(
            "2.1.1 First Bit\nSub-sub content here.\n",
            "2.1.2 Second Bit\nMore sub-sub content.\n",
            "2.1.3 Third Bit\nFinal sub-sub.\n"
        );
        Book book = svc.buildBookFromPages(pages, L, S, "Deep", null);
        assertEquals(3, book.chapters().size());
        assertTrue(book.chapters().get(0).title.endsWith("First Bit"));
    }

    // ── Mega-chapter safety split ────────────────────────────────────

    @Test
    void splitsMegaChaptersIntoSafePageBuckets() {
        // Simulate Murphy's "chapter 2 spans pp. 60-350" failure: one
        // detected heading at the start of a very long span, no
        // further sub-section detection. Safety net should chop the
        // mega-chapter into bucket-sized sub-chapters so each LLM
        // call sees focused content.
        int total = CHAPTER_PAGE_SAFETY_FOR_TEST * 3 + 20;  // ~140 pages
        List<String> pages = new java.util.ArrayList<>();
        pages.add("Chapter 1: Intro\nIntro body\n");                 // page 1
        for (int i = 1; i < total - 1; i++) {
            pages.add("body of mega chapter, page " + (i + 1) +
                ". ".repeat(80) + "\n");
        }
        pages.add("body of mega chapter, last page.\n");

        // Add two more headings far enough apart to satisfy
        // MIN_DETECTED_CHAPTERS=3, so the heading-detection path runs
        // (not the page-bucket fallback) and the safety split has
        // something to react to.
        pages.set(0, "Chapter 1: Intro\nIntro body\n");
        pages.set(total - 2, "Chapter 3: Outro start\nOutro content.\n");
        pages.set(total - 1, "Chapter 4: After\nMore outro.\n");

        Book book = svc.buildBookFromPages(pages, L, S, "Mega", null);

        // After the safety split:
        //   chapter 1 (1 page, untouched)
        //   chapter 2 (~138 pages) → split into N safety sub-sections
        //   chapter 3, 4 (1 page each, untouched)
        // The mega chapter MUST be replaced — no surviving chapter
        // should span more than CHAPTER_PAGE_SAFETY pages.
        for (Chapter ch : book.chapters()) {
            int span = ch.pageEnd - ch.pageStart + 1;
            assertTrue(span <= CHAPTER_PAGE_SAFETY_FOR_TEST,
                "after safety split, no chapter should span >" +
                CHAPTER_PAGE_SAFETY_FOR_TEST + " pages; got '" +
                ch.title + "' spanning " + span);
        }
        // Re-numbering: chapters must be contiguous 1..N.
        for (int i = 0; i < book.chapters().size(); i++) {
            assertEquals(i + 1, book.chapters().get(i).number,
                "chapters must be re-numbered contiguously after safety split");
        }
        // The split sub-chapters should carry the "Section N" suffix
        // so the learner sees what happened.
        assertTrue(book.chapters().stream().anyMatch(c -> c.title.contains("Section ")),
            "safety-split sub-chapters should be labelled '... Section N (pp. X–Y)'");
    }

    // Mirror of BookExtractionService.CHAPTER_PAGE_SAFETY — kept here
    // as a constant so changing it in the service forces an explicit
    // test update rather than silently shifting the test's expectations.
    private static final int CHAPTER_PAGE_SAFETY_FOR_TEST =
        io.tutoros.book.BookExtractionService.CHAPTER_PAGE_SAFETY;

    // ── Fallback — page-bucket split ─────────────────────────────────

    @Test
    void fallsBackToPageBucketsWhenNoHeadingsDetected() {
        // Twelve pages of body without a single heading the regex
        // accepts (no "Chapter", no "Unit", no "N. Title" at the line
        // start). Detector must fall back to page buckets.
        List<String> pages = new java.util.ArrayList<>();
        for (int i = 0; i < 24; i++) {
            pages.add("Some narrative text on page " + (i + 1) +
                ". More words to keep the body non-trivial. ".repeat(4) + "\n");
        }
        Book book = svc.buildBookFromPages(pages, L, S, "No Headings", null);

        // 24 pages / 8 buckets = 3 pages each, exactly the
        // FALLBACK_BUCKETS default.
        assertEquals(BookExtractionService.FALLBACK_BUCKETS, book.chapters().size());
        // Titles should be page-range labels.
        for (Chapter c : book.chapters()) {
            assertTrue(c.title.startsWith("Pages "),
                "fallback titles must be page-range labels, got: " + c.title);
        }
        // Page ranges must be contiguous + cover the whole book.
        assertEquals(1, book.chapters().get(0).pageStart);
        assertEquals(24, book.chapters().get(book.chapters().size() - 1).pageEnd);
    }

    @Test
    void fallbackKeepsAtLeastOneChapterOnShortBook() {
        // Two pages of headingless text — too short for any heading
        // layer to detect 3+ chapters, so we still fall back to
        // buckets, which produces at most pages.size() chapters.
        Book book = svc.buildBookFromPages(
            List.of("page 1 text\n", "page 2 text\n"),
            L, S, "Tiny", null);
        assertFalse(book.chapters().isEmpty());
        assertTrue(book.chapters().size() <= 2);
    }

    // ── Concept extraction ───────────────────────────────────────────

    @Test
    void extractsConceptsFromCapitalisedNounPhrases() {
        // Three multi-word capitalised phrases should land as concepts.
        // The leading "The " phrases should be filtered as stop-prefixed.
        String body = """
            The Electron Transport Chain is a sequence of protein
            complexes. ATP Synthase produces ATP. The Krebs Cycle is
            also called the Citric Acid Cycle. Mitochondrial Membrane
            chemistry depends on Proton Gradient maintenance.
            """;
        List<String> concepts = BookExtractionService.extractConcepts(body);
        assertFalse(concepts.isEmpty());
        // De-duped + lowercased.
        for (String c : concepts) assertEquals(c.toLowerCase(), c);
        // At least one of the obvious ones should be in.
        assertTrue(
            concepts.contains("electron transport chain")
            || concepts.contains("atp synthase")
            || concepts.contains("krebs cycle"),
            "expected at least one capitalised noun phrase concept; got " + concepts);
        // No stop-prefixed entries.
        for (String c : concepts) {
            assertFalse(c.startsWith("the "),
                "stop-prefix 'the ' must be filtered: " + c);
        }
    }

    @Test
    void conceptListIsBounded() {
        // Synthesise more than the cap of capitalised noun phrases —
        // the extractor must clamp.
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < BookExtractionService.MAX_CONCEPTS_PER_CHAPTER * 4; i++) {
            body.append("Concept Number ").append(i).append(" is important. ");
        }
        List<String> concepts = BookExtractionService.extractConcepts(body.toString());
        assertTrue(concepts.size() <= BookExtractionService.MAX_CONCEPTS_PER_CHAPTER);
    }

    // ── Book metadata ────────────────────────────────────────────────

    @Test
    void carriesSubjectAndTitleVerbatim() {
        Book book = svc.buildBookFromPages(
            List.of("Chapter 1: A\n", "Chapter 2: B\n", "Chapter 3: C\n"),
            L, "Mathematics", "Algebra Made Simple", "Doe");
        assertEquals(L,                       book.learnerId);
        assertEquals("mathematics",           book.subject,
            "subject must be lower-cased for PR-A isolation key");
        assertEquals("Algebra Made Simple",   book.title);
        assertEquals("Doe",                   book.author);
        assertEquals(3,                       book.totalPages);
    }

    // ── Chapter body truncation ──────────────────────────────────────

    @Test
    void truncatesChapterBodyAtMaxBodyChars() {
        // One huge chapter — body must end up <= MAX_BODY_CHARS plus
        // a small suffix marker.
        StringBuilder huge = new StringBuilder("Chapter 1: Big\n");
        for (int i = 0; i < 2000; i++) huge.append("sentence ").append(i).append(". ");
        // Pad with two more headings so detector triggers (need >=3).
        Book book = svc.buildBookFromPages(
            List.of(huge.toString(), "Chapter 2: Two\nsmall.\n", "Chapter 3: Three\nsmall.\n"),
            L, S, "Big Book", null);
        assertTrue(book.chapters().get(0).body.length() <= Chapter.MAX_BODY_CHARS + 16,
            "body must be truncated near MAX_BODY_CHARS");
        assertTrue(book.chapters().get(0).body.endsWith("…"),
            "truncation marker must be present");
    }
}
