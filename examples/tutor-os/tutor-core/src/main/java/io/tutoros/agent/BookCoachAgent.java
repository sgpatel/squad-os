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

            Output a LearningInsight JSON with:
              mode         = "basic"
              concept      = "%s"
              body         = 2–3 short paragraphs (markdown). Define
                             the concept, name its parts, and pin down
                             ONE concrete example FROM THE CHAPTER.
              question     = a recall question that asks the learner
                             to RESTATE the core idea in their own
                             words. One sentence.
              modelAnswer  = the ideal short-answer response (2–3
                             sentences). Used by the grader.
              difficulty   = "EASY"
              followUps    = 1–3 follow-up questions (newline-separated)
                             that push toward intermediate / advanced
                             understanding.
              sourcePages  = "%s"
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

            Output a LearningInsight JSON with:
              mode         = "intermediate"
              concept      = "%s"
              body         = ONE worked example (markdown) that applies
                             %s to a problem. Show the steps. Use the
                             same notation / vocabulary as the chapter.
              question     = a fresh problem the learner solves with the
                             same method (NOT identical to the worked
                             example). Specific, not vague.
              modelAnswer  = the worked solution to the question (4–6
                             lines, step-by-step).
              difficulty   = "MEDIUM"
              followUps    = 1–2 harder variants of the same problem.
              sourcePages  = "%s"
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

            Output a LearningInsight JSON with:
              mode         = "advanced"
              concept      = "%s"
              body         = 2 paragraphs that surface a non-obvious
                             aspect of %s — a limitation, an edge case,
                             a connection to another concept the
                             learner has likely seen.
              question     = a question that asks the learner to either
                             (a) compare %s to a related concept and
                             explain when each applies, or (b) evaluate
                             a non-trivial claim about %s. Open-ended.
              modelAnswer  = a model answer (4–6 sentences) showing the
                             kind of reasoning a strong learner would
                             produce.
              difficulty   = "HARD"
              followUps    = 1–2 even-harder variants.
              sourcePages  = "%s"
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept, concept, concept, pages);
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

            Output a LearningInsight JSON with:
              mode         = "usage"
              concept      = "%s"
              body         = 3 short markdown sections (### headings):
                              1. "Today" — 2 concrete present-day uses,
                                 each one paragraph naming a specific
                                 industry / product / scenario.
                              2. "Adjacent fields" — 1 paragraph on
                                 disciplines that depend on %s.
                              3. "Why it matters" — 1 paragraph stating
                                 the stakes / what changes if no-one
                                 understands this.
              question     = ""        (no quiz on the usage lens)
              modelAnswer  = ""
              difficulty   = ""
              followUps    = 1–2 questions the learner could research
                             to dig deeper.
              sourcePages  = "%s"
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept, pages);
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

            Output a LearningInsight JSON with:
              mode         = "history"
              concept      = "%s"
              body         = 3 short markdown sections (### headings):
                              1. "Discovery" — who first proposed it,
                                 roughly when, and what problem they
                                 were trying to solve.
                              2. "Evolution" — 2–3 key milestones that
                                 refined / generalised the idea.
                              3. "Today's understanding" — one sentence
                                 connecting back to the chapter's
                                 framing.
              question     = ""
              modelAnswer  = ""
              difficulty   = ""
              followUps    = 1–2 historical figures the learner could
                             read more about.
              sourcePages  = ""   (history extends beyond the chapter)
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

            Output a LearningInsight JSON with:
              mode         = "future"
              concept      = "%s"
              body         = 3 short markdown sections (### headings):
                              1. "Open problems" — 2 specific questions
                                 researchers are still working on.
                              2. "Where it's heading" — 1 paragraph on
                                 trends shaping the next decade.
                              3. "Careers" — 1 paragraph naming 2–3
                                 specific jobs / fields that need
                                 strong understanding of %s.
              question     = ""
              modelAnswer  = ""
              difficulty   = ""
              followUps    = 1–2 areas the learner could dive into
                             to pursue this further.
              sourcePages  = ""
            """.formatted(
                learnerLevel, concept, bookTitle, chapterTitle,
                chapterBody, concept, concept);
    }
}
