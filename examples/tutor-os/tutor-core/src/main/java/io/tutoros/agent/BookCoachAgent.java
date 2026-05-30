package io.tutoros.agent;

import io.squados.annotation.*;
import io.tutoros.model.LearningInsight;

/**
 * Book Coach Agent — the learning loop on top of an uploaded textbook.
 *
 * <p>Given a {@link io.tutoros.book.Chapter} and a "lens" (one of six
 * learning modes), produces a {@link LearningInsight} grounded in the
 * chapter body. The chapter body is included verbatim in every prompt
 * so the LLM has the SAME ground truth on every call — no hallucinated
 * facts about content the learner didn't actually upload.
 *
 * <h2>The six lenses</h2>
 *
 * <ol>
 *   <li><b>basic</b> — definition + recall question. Tests "can the
 *       learner state what the concept is?"</li>
 *   <li><b>intermediate</b> — application question with a worked
 *       example. Tests "can the learner use the concept on a fresh
 *       problem?"</li>
 *   <li><b>advanced</b> — synthesis, evaluation, multi-step. Tests
 *       "can the learner connect this concept to others?"</li>
 *   <li><b>usage</b> — concrete real-life examples. Where this is
 *       used today, by whom, and why it matters.</li>
 *   <li><b>history</b> — who discovered it, how the idea evolved,
 *       which problem it solved. Outside-the-chapter context is
 *       allowed here because textbooks rarely cover the full backstory.</li>
 *   <li><b>future</b> — open problems, current research, careers
 *       that build on the concept. Also outside-the-chapter — the
 *       prompt explicitly tells the LLM to look forward.</li>
 * </ol>
 *
 * <h2>Role choice — WILDCARD</h2>
 *
 * <p>The 13 specialised AgentRole values are all claimed by other
 * tutor-os agents (DiagnosticAgent=ANALYST, PracticeAgent=EXECUTOR,
 * etc.). WILDCARD is the explicit "no opinionated defaults" bucket
 * already shared by {@code TodoAgent} and {@code SyllabusSuggesterAgent}.
 *
 * <p>The SquadOS registry's {@code byRole} map is last-writer-wins on
 * collision, so {@code BookCoachAgent} is registered EARLIER than
 * {@code SyllabusSuggesterAgent} in {@code TutorBeansConfig} — the
 * suggester wins the WILDCARD slot for {@code ctx.submitTo(WILDCARD)},
 * and BookCoach is reached by name via
 * {@code ctx.getRegistry().getByName("BookCoachAgent").execute(...)}.
 * Both paths go through the full {@code @StructuredOutput} +
 * {@code @Retry} + {@code @Traced} wrapper machinery — only the
 * dispatch lookup differs.
 *
 * <h2>Features</h2>
 *
 * <ul>
 *   <li>{@link StructuredOutput} — LearningInsight JSON with malformed-retry</li>
 *   <li>{@link Retry} — three attempts; cheaper than re-asking the learner</li>
 *   <li>{@link Traced} — every call is a child span under the chapter ask</li>
 * </ul>
 */
@Agent(
    role        = AgentRole.WILDCARD,
    name        = "BookCoachAgent",
    description = "Reads an uploaded textbook chapter and produces a " +
                  "LearningInsight under one of six lenses (basic / " +
                  "intermediate / advanced / usage / history / future). " +
                  "Grounded in the chapter body — facts about textbook " +
                  "content stay inside the chapter, while history and " +
                  "future-scope lenses are allowed to draw on wider context."
)
@StructuredOutput(schema = LearningInsight.class, retryOnMalformed = true, maxRetries = 3)
@Retry(maxAttempts = 3, backoffMs = 500, multiplier = 2.0f)
@Traced(spanName = "book-coach")
public class BookCoachAgent {

    /** Six lens modes the controller accepts. Public so the controller's
        validation can reference this single source of truth. */
    public static final String MODE_BASIC        = "basic";
    public static final String MODE_INTERMEDIATE = "intermediate";
    public static final String MODE_ADVANCED     = "advanced";
    public static final String MODE_USAGE        = "usage";
    public static final String MODE_HISTORY      = "history";
    public static final String MODE_FUTURE       = "future";

    /**
     * Lenient mode parser used by both the controller and the prompt
     * dispatcher. Unrecognised modes default to "basic" so a typo
     * doesn't blow the learner's session.
     */
    public static String normalizeMode(String raw) {
        if (raw == null) return MODE_BASIC;
        String m = raw.trim().toLowerCase();
        return switch (m) {
            case MODE_BASIC, MODE_INTERMEDIATE, MODE_ADVANCED,
                 MODE_USAGE, MODE_HISTORY, MODE_FUTURE -> m;
            default -> MODE_BASIC;
        };
    }

    // ── Selection-context AI (advanced reader) ──────────────────────

    /** Modes the reader can request when the learner highlights text. */
    public static final String EXPLAIN_MODE_EXPLAIN  = "explain";
    public static final String EXPLAIN_MODE_SIMPLIFY = "simplify";
    public static final String EXPLAIN_MODE_DEFINE   = "define";

    /** Lenient normaliser for the explain mode param. */
    public static String normalizeExplainMode(String raw) {
        if (raw == null) return EXPLAIN_MODE_EXPLAIN;
        String m = raw.trim().toLowerCase();
        return switch (m) {
            case EXPLAIN_MODE_EXPLAIN, EXPLAIN_MODE_SIMPLIFY, EXPLAIN_MODE_DEFINE -> m;
            default -> EXPLAIN_MODE_EXPLAIN;
        };
    }

    /**
     * Tight, grounded response to "the learner highlighted this text
     * inside the chapter — explain/simplify/define it." Returns
     * PURE markdown (no JSON) so the reader can pipe it through
     * &lt;Markdown&gt; without a parsing step.
     *
     * <p>Three modes target distinct reading-failure patterns:
     * <ul>
     *   <li><b>explain</b> — selection is a sentence the learner
     *       doesn't follow. 2–3 paragraph unpacking grounded in
     *       chapter context.</li>
     *   <li><b>simplify</b> — selection is dense. Rewrite one Bloom
     *       level lower with concrete examples and an analogy.</li>
     *   <li><b>define</b> — selection is a single term. 2–3 sentence
     *       definition + one concrete example.</li>
     * </ul>
     */
    public String selectionExplainPrompt(String chapterTitle, String chapterBody,
                                         String selection, String mode,
                                         String learnerLevel) {
        String m = normalizeExplainMode(mode);
        String body = chapterBody == null ? "" : chapterBody;
        if (body.length() > 4000) body = body.substring(0, 4000) + "\n…";

        String instructions = switch (m) {
            case EXPLAIN_MODE_DEFINE -> """
                The selection is a TERM the learner wants defined.
                Output:
                  - First line: bold the term, then a 1-sentence definition.
                  - Second paragraph: ONE concrete example showing the term in use.
                  - 30–80 words total. No filler.
                """;
            case EXPLAIN_MODE_SIMPLIFY -> """
                The selection is DENSE prose the learner couldn't parse.
                Rewrite it at one Bloom level below the chapter's complexity:
                  - Use concrete nouns and shorter sentences.
                  - Replace jargon with the plainest equivalent that's still correct.
                  - Add ONE everyday-life analogy (a 1-sentence comparison).
                Keep total length to 60–120 words. Markdown OK.
                """;
            default -> """
                The selection is a sentence/passage the learner doesn't follow.
                Unpack it in 2–3 short paragraphs:
                  - Paragraph 1: what the selection is SAYING (paraphrase).
                  - Paragraph 2: why it MATTERS in the chapter's context.
                  - Paragraph 3 (optional): a follow-up the learner can think about.
                Stay anchored to the chapter — no outside facts.
                Total length 80–180 words. Markdown OK.
                """;
        };

        return """
            You are a textbook tutor helping a learner read this chapter.
            They highlighted a passage and asked you to %s it.

            Chapter title: %s
            Selected text:
            ----
            %s
            ----

            Chapter context (for grounding — DO NOT quote it back):
            ----
            %s
            ----

            Learner level: %s

            %s

            CRITICAL OUTPUT FORMAT:
              - Respond with PURE MARKDOWN only — no JSON, no code fences
                around the whole reply, no preamble like "Here is…".
              - Start with the answer directly.
              - If you quote the selection, use blockquote (> ...) sparingly.
              - For MATH, use KaTeX dollar delimiters:
                  • inline math:  $E[X]$
                  • block math:   $$E[X] = \\int_{-\\infty}^{\\infty} x\\,p(x)\\,dx$$
                Do NOT use \\( \\) or \\[ \\] — only $ … $ and $$ … $$.
                Put block math on its own line with blank lines above and below.
                Keep each expression whole inside ONE matched pair of $…$ —
                the function name and its parentheses go INSIDE the dollars
                ($P(x_i \\mid \\theta)$, never P$(x_i \\mid \\theta)$ nor a stray
                unmatched $). Use \\mid for a conditional bar, not a bare "|",
                and never mix unicode math glyphs (θ, ∑, ∣) with LaTeX macros.
            """.formatted(
                m, chapterTitle, selection, body, learnerLevel, instructions);
    }

    /**
     * Single entry point — builds the prompt for the supplied mode.
     * Splits internally by mode rather than exposing six public
     * methods so the controller's wiring stays a single LLM call.
     *
     * @param mode         one of the six constants above
     * @param bookTitle    learner-facing book title (for context framing)
     * @param chapterTitle chapter title verbatim
     * @param chapterBody  chapter body, capped by {@link io.tutoros.book.Chapter#MAX_BODY_CHARS}
     * @param concept      target concept tag (one of the chapter's extracted concepts)
     * @param learnerLevel PRIMARY / MIDDLE_SCHOOL / SENIOR_SCHOOL / etc.
     * @param pageStart    first chapter page (for the {@code sourcePages} field)
     * @param pageEnd      last chapter page
     */
    public String buildPrompt(String mode,
                              String bookTitle, String chapterTitle, String chapterBody,
                              String concept, String learnerLevel,
                              int pageStart, int pageEnd) {
        String m = normalizeMode(mode);
        String pages = (pageStart > 0 && pageEnd > 0)
            ? "pp. " + pageStart + "–" + pageEnd
            : "";
        return switch (m) {
            case MODE_BASIC        -> basicPrompt(bookTitle, chapterTitle, chapterBody,
                                                  concept, learnerLevel, pages);
            case MODE_INTERMEDIATE -> intermediatePrompt(bookTitle, chapterTitle, chapterBody,
                                                         concept, learnerLevel, pages);
            case MODE_ADVANCED     -> advancedPrompt(bookTitle, chapterTitle, chapterBody,
                                                     concept, learnerLevel, pages);
            case MODE_USAGE        -> usagePrompt(bookTitle, chapterTitle, chapterBody,
                                                  concept, learnerLevel, pages);
            case MODE_HISTORY      -> historyPrompt(bookTitle, chapterTitle, chapterBody,
                                                    concept, learnerLevel);
            case MODE_FUTURE       -> futurePrompt(bookTitle, chapterTitle, chapterBody,
                                                   concept, learnerLevel);
            default                -> basicPrompt(bookTitle, chapterTitle, chapterBody,
                                                  concept, learnerLevel, pages);
        };
    }

    // ── Quiz-style lenses ───────────────────────────────────────────

    /**
     * BASIC — definition + recall. EASY difficulty, mastery write
     * carries a small positive delta on correct.
     *
     * <p>The body explains the concept in 2–3 short paragraphs aimed
     * at the learner's level, then the question asks them to restate
     * the core idea in their own words. The model answer is what we
     * grade against in {@code AssessmentAgent}.
     */
    private String basicPrompt(String bookTitle, String chapterTitle, String chapterBody,
                               String concept, String learnerLevel, String pages) {
        return """
            You are a textbook tutor explaining the concept "%s" to a
            %s-level learner. Your ground truth is the chapter below —
            do NOT invent facts that aren't supported by it.

            Book:    %s
            Chapter: %s
            ----
            %s
            ----

            Respond with ONLY a JSON object in this exact shape — no
            prose before or after, no markdown code fences, no comments.
            Every field is required; use an empty string "" if a field
            genuinely doesn't apply, never omit it.

            CRITICAL FORMATTING for the body field — these rules are
            the difference between a polished response and an unreadable
            wall of text. Follow them exactly:

              1. Structure the body with these THREE markdown headings,
                 in order, each on its own line preceded by a blank line:
                   ### Core Explanation
                   ### Real-World Analogy
                   ### Concrete Example
                 Do NOT use inline labels like "Core Explanation:" — they
                 render as one long paragraph. Use the ### heading form.

              2. Put a BLANK LINE between every paragraph. Real newlines,
                 not the characters backslash-n.

              3. For math, use KaTeX dollar delimiters:
                   • inline math:  $\\theta$ , $E[X]$
                   • block  math:  $$\\arg\\max_\\theta\\,p(D \\mid \\theta)$$
                 Block math goes on its OWN line with blank lines above
                 and below. Never use \\( \\) or \\[ \\] — only $ and $$.
                 Keep EACH expression whole inside ONE matched pair of
                 $…$: every $ must be closed, and the function name AND
                 its parentheses live INSIDE the dollars — write
                 $P(x_i \\mid \\theta)$, never P$(x_i \\mid \\theta)$ nor a
                 stray unmatched $. Use \\mid for a conditional bar, not a
                 bare "|". NEVER mix unicode math glyphs (θ, ∑, ∣, ≤, ×)
                 with LaTeX — always the macro form (\\theta, \\sum, \\mid).

              4. Bold key terms with **double asterisks** the first time
                 they appear so the eye lands on them.

            {
              "mode":        "basic",
              "concept":     "%s",
              "body":        "<markdown body following the structure above>",
              "question":    "<one-sentence recall question asking the learner to restate the core idea in their own words>",
              "modelAnswer": "<the ideal short-answer response, 2–3 sentences. Used by the grader.>",
              "difficulty":  "EASY",
              "followUps":   "<1–3 follow-up questions, newline-separated, pushing toward intermediate / advanced>",
              "sourcePages": "%s"
            }
            """.formatted(
                concept, learnerLevel, bookTitle, chapterTitle,
                chapterBody, concept, pages);
    }

    /**
     * INTERMEDIATE — application + worked example. MEDIUM difficulty.
     *
     * <p>The question asks the learner to USE the concept on a new
     * problem; the body provides one worked example as a template.
     */
    private String intermediatePrompt(String bookTitle, String chapterTitle, String chapterBody,
                                      String concept, String learnerLevel, String pages) {
        return """
            You are a textbook tutor coaching a %s-level learner who
            already knows what "%s" is — they need PRACTICE applying it.
            Ground every claim in the chapter below.

            Book:    %s
            Chapter: %s
            ----
            %s
            ----

            Respond with ONLY a JSON object in this exact shape — no
            prose before or after, no markdown code fences, no comments.
            Every field is required; use an empty string "" if a field
            genuinely doesn't apply, never omit it.

            CRITICAL FORMATTING for the body field:

              1. Structure with these markdown headings, in order, each
                 preceded by a blank line:
                   ### Setup
                   ### Worked Solution
                   ### Why this works
                 Use the ### heading form, not inline "Setup:" labels.

              2. Put a BLANK LINE between every paragraph and between
                 each numbered step. Real newlines, not backslash-n.

              3. For math, use KaTeX dollar delimiters:
                   • inline math:  $\\theta$ , $E[X]$
                   • block  math:  $$L(\\theta) = \\sum_i \\log p(x_i \\mid \\theta)$$
                 Block math goes on its OWN line with blank lines above
                 and below. Never use \\( \\) or \\[ \\] — only $ and $$.
                 Keep EACH expression whole inside ONE matched pair of
                 $…$: every $ must be closed, and the function name AND
                 its parentheses live INSIDE the dollars — write
                 $P(x_i \\mid \\theta)$, never P$(x_i \\mid \\theta)$ nor a
                 stray unmatched $. Use \\mid for a conditional bar, not a
                 bare "|". NEVER mix unicode math glyphs (θ, ∑, ∣, ≤, ×)
                 with LaTeX — always the macro form (\\theta, \\sum, \\mid).

              4. Number the steps with "1.", "2.", "3." on separate lines.

            {
              "mode":        "intermediate",
              "concept":     "%s",
              "body":        "<markdown body following the structure above, applying %s to a worked problem with the chapter's notation>",
              "question":    "<a fresh problem solved with the same method (NOT identical to the worked example). Specific, not vague.>",
              "modelAnswer": "<the worked solution to the question, 4–6 lines, step-by-step>",
              "difficulty":  "MEDIUM",
              "followUps":   "<1–2 harder variants of the same problem, newline-separated>",
              "sourcePages": "%s"
            }
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept, pages);
    }

    /**
     * ADVANCED — synthesis / evaluation. HARD difficulty.
     *
     * <p>The question asks the learner to CONNECT the concept to
     * something else (a prior chapter, a real-world constraint, an
     * edge case) or to JUDGE a claim about it.
     */
    private String advancedPrompt(String bookTitle, String chapterTitle, String chapterBody,
                                  String concept, String learnerLevel, String pages) {
        return """
            You are a textbook tutor pushing a %s-level learner past
            rote application of "%s" into synthesis and evaluation.
            Ground factual claims in the chapter below — but the
            question SHOULD draw connections beyond it.

            Book:    %s
            Chapter: %s
            ----
            %s
            ----

            Respond with ONLY a JSON object in this exact shape — no
            prose before or after, no markdown code fences, no comments.
            Every field is required; use an empty string "" if a field
            genuinely doesn't apply, never omit it.

            CRITICAL FORMATTING for the body field:

              1. Structure with these markdown headings, in order, each
                 preceded by a blank line:
                   ### The non-obvious bit
                   ### Connection to other concepts
                   ### When this breaks
                 Use the ### heading form, not inline labels.

              2. Put a BLANK LINE between every paragraph. Real newlines,
                 not backslash-n.

              3. For math, use KaTeX dollar delimiters:
                   • inline math:  $\\theta$
                   • block  math:  $$\\mathrm{KL}(p \\| q) = \\int p\\log\\frac{p}{q}\\,dx$$
                 Block math goes on its OWN line with blank lines above
                 and below. Never use \\( \\) or \\[ \\] — only $ and $$.
                 Keep EACH expression whole inside ONE matched pair of
                 $…$: every $ must be closed, and the function name AND
                 its parentheses live INSIDE the dollars — write
                 $P(x_i \\mid \\theta)$, never P$(x_i \\mid \\theta)$ nor a
                 stray unmatched $. Use \\mid for a conditional bar, not a
                 bare "|". NEVER mix unicode math glyphs (θ, ∑, ∣, ≤, ×)
                 with LaTeX — always the macro form (\\theta, \\sum, \\mid).

              4. Bold technical terms on first use.

            {
              "mode":        "advanced",
              "concept":     "%s",
              "body":        "<markdown body following the structure above, surfacing non-obvious aspects of %s>",
              "question":    "<an open-ended question asking the learner to either compare %s to a related concept (when does each apply?) or evaluate a non-trivial claim about it>",
              "modelAnswer": "<a 4–6 sentence model answer showing the reasoning a strong learner would produce>",
              "difficulty":  "HARD",
              "followUps":   "<1–2 even-harder variants, newline-separated>",
              "sourcePages": "%s"
            }
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept, concept, pages);
    }

    // ── Context lenses (no quiz attached) ───────────────────────────

    /**
     * USAGE — concrete real-life examples.
     *
     * <p>The chapter body is the anchor, but the LLM is allowed to
     * extend with examples that don't appear in the chapter as long
     * as they're real. No question — this is a "show me where this
     * matters" surface.
     */
    private String usagePrompt(String bookTitle, String chapterTitle, String chapterBody,
                               String concept, String learnerLevel, String pages) {
        return """
            You are a textbook tutor showing a %s-level learner where
            "%s" is actually used in the world. Anchor your answer to
            the chapter below, but you can name examples that aren't
            explicitly in it — as long as they are REAL and concrete.

            Book:    %s
            Chapter: %s
            ----
            %s
            ----

            Respond with ONLY a JSON object in this exact shape — no
            prose before or after, no markdown code fences, no comments.
            Every field is required; use an empty string "" if a field
            genuinely doesn't apply, never omit it.

            CRITICAL FORMATTING: the body is a markdown string with
            three "###" subheadings. Use REAL line breaks between
            sections (press Enter; let your JSON encoder handle the
            escaping). Do NOT type the characters backslash-n
            yourself — that ends up as visible text "\\n\\n" in the
            output instead of an actual newline.

            The body must follow this structure exactly:

              ### Today
              <2 paragraphs naming specific industries / products /
              scenarios that use %s today. Be concrete — name companies,
              tools, or use-cases.>

              ### Adjacent fields
              <1 paragraph on disciplines that depend on %s.>

              ### Why it matters
              <1 paragraph stating the stakes — what changes if no-one
              understands this concept.>

            {
              "mode":        "usage",
              "concept":     "%s",
              "body":        "<markdown body following the structure above, with real newlines between sections>",
              "question":    "",
              "modelAnswer": "",
              "difficulty":  "",
              "followUps":   "<1–2 research questions, one per line>",
              "sourcePages": "%s"
            }
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept, concept, pages);
    }

    /**
     * HISTORY — discovery, key figures, evolution.
     *
     * <p>Textbooks rarely include the full backstory of a concept, so
     * the prompt explicitly opens up beyond the chapter. The body must
     * be factual; the {@code sourcePages} field comes back empty.
     */
    private String historyPrompt(String bookTitle, String chapterTitle, String chapterBody,
                                 String concept, String learnerLevel) {
        return """
            You are a textbook tutor telling a %s-level learner the
            story of where "%s" came from. The chapter below is your
            point of reference for what the concept IS — but for the
            history you draw on wider knowledge. Stay factual; if you
            don't know a date or name, say so rather than inventing.

            Book:    %s
            Chapter: %s
            ----
            %s
            ----

            Respond with ONLY a JSON object in this exact shape — no
            prose before or after, no markdown code fences, no comments.
            Every field is required; use an empty string "" if a field
            genuinely doesn't apply, never omit it.

            CRITICAL FORMATTING: the body is a markdown string with
            three "###" subheadings. Use REAL line breaks between
            sections (press Enter; let your JSON encoder handle the
            escaping). Do NOT type the characters backslash-n
            yourself — that ends up as visible text "\\n\\n" in the
            output instead of an actual newline.

            The body must follow this structure exactly:

              ### Discovery
              <who first proposed it, roughly when, and what problem
              they were trying to solve.>

              ### Evolution
              <2–3 key milestones that refined or generalised the idea.>

              ### Today's understanding
              <one sentence connecting back to the chapter's framing.>

            {
              "mode":        "history",
              "concept":     "%s",
              "body":        "<markdown body following the structure above, with real newlines between sections>",
              "question":    "",
              "modelAnswer": "",
              "difficulty":  "",
              "followUps":   "<1–2 historical figures, one per line>",
              "sourcePages": ""
            }
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept);
    }

    /**
     * FUTURE — open problems, research, careers.
     *
     * <p>Same outside-the-chapter latitude as history. The point is
     * to give the learner a horizon line — what's next if they keep
     * going? Careers anchor it to "why should I bother."
     */
    private String futurePrompt(String bookTitle, String chapterTitle, String chapterBody,
                                String concept, String learnerLevel) {
        return """
            You are a textbook tutor showing a %s-level learner where
            "%s" is HEADED — open problems, current research, careers
            that depend on it. Use the chapter below as your starting
            point for what the concept currently is. For the forward-
            looking content, draw on wider knowledge and stay factual.

            Book:    %s
            Chapter: %s
            ----
            %s
            ----

            Respond with ONLY a JSON object in this exact shape — no
            prose before or after, no markdown code fences, no comments.
            Every field is required; use an empty string "" if a field
            genuinely doesn't apply, never omit it.

            CRITICAL FORMATTING: the body is a markdown string with
            three "###" subheadings. Use REAL line breaks between
            sections (press Enter; let your JSON encoder handle the
            escaping). Do NOT type the characters backslash-n
            yourself — that ends up as visible text "\\n\\n" in the
            output instead of an actual newline.

            The body must follow this structure exactly:

              ### Open problems
              <2 specific questions researchers are still working on.>

              ### Where it's heading
              <1 paragraph on trends shaping the next decade.>

              ### Careers
              <1 paragraph naming 2–3 specific jobs / fields that need
              strong understanding of %s.>

            {
              "mode":        "future",
              "concept":     "%s",
              "body":        "<markdown body following the structure above, with real newlines between sections>",
              "question":    "",
              "modelAnswer": "",
              "difficulty":  "",
              "followUps":   "<1–2 areas to dive into, one per line>",
              "sourcePages": ""
            }
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept);
    }
}
