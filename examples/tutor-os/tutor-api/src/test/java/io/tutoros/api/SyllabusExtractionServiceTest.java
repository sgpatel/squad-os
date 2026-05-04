package io.tutoros.api;

import io.tutoros.agent.SyllabusSuggesterAgent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SyllabusExtractionServiceTest — exercises the bits that don't require
 * a live LLM / network:
 *
 *   1. PDFBox extraction round-trips a generated PDF (real bytes →
 *      real text via the same code path the controller calls).
 *   2. The extract prompt embeds the document text and the subject
 *      hint, and pins source=CUSTOM in the output contract.
 *   3. Image extraction without a ChatModel throws a clear error so
 *      the controller can map to 415 with a useful message.
 */
class SyllabusExtractionServiceTest {

    @Test
    void pdfBoxPathRoundTripsKnownText() throws IOException {
        byte[] pdf = makeSingleLinePdf(
            "Chapter 1: Introduction to limits",
            "- Intuition: what does \"approaches\" mean",
            "- One-sided vs two-sided limits"
        );
        String extracted = SyllabusExtractionService.extractPdf(pdf);
        // Don't pin exact whitespace — just check the words survive.
        assertTrue(extracted.contains("Chapter 1"),
            "PDFBox extraction must preserve chapter heading text");
        assertTrue(extracted.contains("Intuition"),
            "PDFBox extraction must preserve bullet text");
        assertTrue(extracted.contains("two-sided limits"),
            "PDFBox extraction must preserve full bullet line");
    }

    @Test
    void imageExtractionWithoutChatModelThrowsUnsupported() {
        // No ChatModel bean → the service must refuse images cleanly so
        // the controller can return 415 instead of a 500.
        SyllabusExtractionService svc = new SyllabusExtractionService(
            new EmptyObjectProvider<>());
        UnsupportedOperationException ex = assertThrows(
            UnsupportedOperationException.class,
            () -> svc.extract(
                new byte[]{(byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47}, // PNG magic prefix
                "image/png", "syllabus.png")
        );
        assertTrue(ex.getMessage().toLowerCase().contains("vision"),
            "error message must mention vision so the UI surface knows what to ask for");
    }

    @Test
    void extractPromptEmbedsDocumentTextAndHint() {
        SyllabusSuggesterAgent agent = new SyllabusSuggesterAgent();
        String prompt = agent.extractFromTextPrompt(
            "Chapter 1: Photosynthesis\nChapter 2: Respiration",
            "Biology");

        assertTrue(prompt.contains("Photosynthesis"),
            "document text must appear in the LLM prompt");
        assertTrue(prompt.contains("Biology"),
            "subject hint must appear so the LLM can verify");
        assertTrue(prompt.contains("\"CUSTOM\""),
            "extract prompt MUST pin source=CUSTOM (uploads are learner-owned)");
        assertTrue(prompt.contains("not a syllabus"),
            "extract prompt must instruct the LLM to handle non-syllabus uploads");
    }

    @Test
    void extractPromptHandlesMissingHintGracefully() {
        SyllabusSuggesterAgent agent = new SyllabusSuggesterAgent();
        String prompt = agent.extractFromTextPrompt("Some doc text", null);
        assertNotNull(prompt);
        // Hint line collapses out when blank.
        assertFalse(prompt.contains("Caller's best guess at subject"));
    }

    @Test
    void extractPromptTruncatesHugeUploads() {
        SyllabusSuggesterAgent agent = new SyllabusSuggesterAgent();
        String huge = "x".repeat(20_000);
        String prompt = agent.extractFromTextPrompt(huge, "Maths");
        assertTrue(prompt.contains("(truncated)"),
            "prompt must mark truncation so the LLM knows the doc was clipped");
        assertFalse(prompt.length() > huge.length(),
            "prompt must be smaller than the raw input after truncation");
    }

    // ── helpers ───────────────────────────────────────────────────────

    /** Build a minimal, valid PDF containing the given lines on one page. */
    private static byte[] makeSingleLinePdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 720);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** ObjectProvider stub that always says "no bean available". */
    private static class EmptyObjectProvider<T>
            implements org.springframework.beans.factory.ObjectProvider<T> {
        @Override public T getObject() { throw new IllegalStateException(); }
        @Override public T getObject(Object... args) { throw new IllegalStateException(); }
        @Override public T getIfAvailable() { return null; }
        @Override public T getIfUnique() { return null; }
        @Override public void ifAvailable(java.util.function.Consumer<T> c) { /* no-op */ }
        @Override public void ifUnique(java.util.function.Consumer<T> c) { /* no-op */ }
    }
}
