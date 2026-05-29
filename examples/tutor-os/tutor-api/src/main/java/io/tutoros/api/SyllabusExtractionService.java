package io.tutoros.api;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.util.List;

/**
 * SyllabusExtractionService — converts an uploaded PDF or image of a
 * syllabus into plain text suitable for {@link io.tutoros.agent.SyllabusSuggesterAgent}'s
 * extract path.
 *
 * Two extractors, one output (plain text):
 *
 *   1. PDF  — Apache PDFBox 3.x. Pure Java, no native deps. Returns the
 *             concatenated text of all pages in document order.
 *   2. IMG  — Spring AI {@code ChatModel} with multimodal {@link Media}.
 *             We send the image bytes alongside a transcription prompt
 *             and take the model's text response. Requires a ChatModel
 *             whose underlying provider supports vision (e.g. OpenAI
 *             gpt-4o / gpt-4o-mini). When no ChatModel is wired (no
 *             API key configured) we throw {@link UnsupportedOperationException}
 *             so the controller can return 415 with a clear error.
 *
 * The service deliberately doesn't talk to {@link io.squados.context.SquadContext}
 * — extraction is "raw bytes → text", a layer below the agent dispatch.
 * The controller then hands the text to the agent for structuring.
 */
@Service
public class SyllabusExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SyllabusExtractionService.class);

    /** Cap to keep PDFBox + LLM calls reasonable on huge uploads. */
    private static final long MAX_BYTES = 8L * 1024 * 1024; // 8 MiB

    private final ObjectProvider<ChatModel> chatModelProvider;

    public SyllabusExtractionService(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    // ── public API ───────────────────────────────────────────────────

    /** Extracted text plus metadata for tracing/logging. */
    public record Extracted(String text, String sourceKind, int charCount) {}

    /**
     * Dispatch on content type. {@code application/pdf} → PDF path;
     * {@code image/*} → vision path. Anything else throws
     * {@link IllegalArgumentException} so the controller can map to 415.
     */
    public Extracted extract(byte[] bytes, String contentType, String filename)
            throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty upload");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                "File too large (" + bytes.length + " bytes, max " + MAX_BYTES + ")");
        }

        String ct = contentType == null ? "" : contentType.toLowerCase();
        // Filename fallback when the browser didn't set a content type.
        if (ct.isBlank() && filename != null) {
            String low = filename.toLowerCase();
            if      (low.endsWith(".pdf"))  ct = "application/pdf";
            else if (low.endsWith(".png"))  ct = "image/png";
            else if (low.endsWith(".jpg") ||
                     low.endsWith(".jpeg")) ct = "image/jpeg";
            else if (low.endsWith(".webp")) ct = "image/webp";
        }

        if (ct.equals("application/pdf")) {
            String text = extractPdf(bytes);
            return new Extracted(text, "pdf", text.length());
        }
        if (ct.startsWith("image/")) {
            String text = extractImage(bytes, ct);
            return new Extracted(text, "image:" + ct, text.length());
        }
        throw new IllegalArgumentException(
            "Unsupported content type: " + (contentType == null ? "(none)" : contentType)
            + " — accepted: application/pdf, image/png, image/jpeg, image/webp");
    }

    // ── PDF (PDFBox) ────────────────────────────────────────────────

    /**
     * Pure-Java text extraction. Strips form-feed page separators that
     * PDFBox inserts between pages; the LLM doesn't need them and they
     * inflate token count.
     */
    static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // multi-column docs
            String raw = stripper.getText(doc);
            return normalise(raw);
        }
    }

    // ── Image (Spring AI vision) ────────────────────────────────────

    /**
     * Send the image to a vision-capable ChatModel and return whatever
     * text it produces. We deliberately ask for a literal transcription
     * (not a syllabus extraction) — the agent that follows handles
     * structuring; mixing both into one call has been less reliable in
     * practice than a clean two-step.
     */
    private String extractImage(byte[] bytes, String contentType) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new UnsupportedOperationException(
                "Image extraction requires a vision-capable ChatModel — set " +
                "OPENAI_API_KEY (or another Spring AI provider) and use a " +
                "vision model (e.g. gpt-4o-mini). PDF uploads still work " +
                "without a ChatModel.");
        }
        try {
            Media media = new Media(MimeType.valueOf(contentType),
                new ByteArrayResource(bytes));
            UserMessage userMsg = new UserMessage(
                "Transcribe ALL visible text from this syllabus image as plain text. " +
                "Preserve chapter / unit / module headings on their own lines. " +
                "Preserve bullet markers ('-' / '•') as '- '. " +
                "Do NOT summarise, do NOT translate, do NOT add any commentary. " +
                "If the image contains no readable syllabus text, output the " +
                "single word: NO_SYLLABUS_TEXT.",
                List.of(media));
            String out = model.call(new Prompt(List.of(userMsg))).getResult()
                .getOutput().getText();
            if (out == null || out.isBlank()) {
                throw new IllegalStateException(
                    "Vision model returned empty transcription");
            }
            String text = normalise(out);
            if (text.contains("NO_SYLLABUS_TEXT")) {
                throw new IllegalArgumentException(
                    "No readable syllabus text was detected in the image. " +
                    "Try a clearer photo or a PDF.");
            }
            return text;
        } catch (RuntimeException e) {
            log.warn("vision extraction failed: {}", e.toString());
            throw e;
        }
    }

    // ── helpers ─────────────────────────────────────────────────────

    /** Strip FF/CR, collapse runs of blank lines, trim. */
    private static String normalise(String raw) {
        if (raw == null) return "";
        return raw
            .replace("\f", "\n")
            .replace("\r", "")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }
}

