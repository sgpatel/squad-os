package io.tutoros.agent;

import io.squados.annotation.*;
import io.tutoros.model.Syllabus;

/**
 * Syllabus Suggester Agent — produces a {@link Syllabus} for a given
 * (subject, topic, level) triple when the learner asks for one.
 *
 * Why a dedicated agent (not a method on CurriculumPlannerAgent):
 *   CurriculumPlannerAgent carries {@code @StructuredOutput(StudyPlan.class)}
 *   class-wide. Every call routed to its role gets parsed as a StudyPlan,
 *   not a Syllabus. The framework dispatches by role, so the only way to
 *   get a different schema applied is a different agent class on a
 *   different role. Same pattern we use for {@link SyllabusSuggesterAgent}
 *   vs the planner — separate agent, separate schema, no parser collision.
 *
 * Role: WILDCARD. Every other tutor role is taken; WILDCARD has no
 * opinionated default temperature/max-tokens, which is fine because we
 * don't need a heavy LlmOptions profile for what is essentially a single
 * structured emission.
 *
 * Lifecycle:
 *   /api/syllabus/suggest → SyllabusController submits to WILDCARD role
 *                          → AgentWrapper applies @StructuredOutput
 *                          → returns typed Syllabus to the controller
 *
 * The output is NOT persisted by this agent — that's the controller's
 * job (writes to LearnerProfile.customSyllabus on /save).
 */
@Agent(
    role        = AgentRole.WILDCARD,
    name        = "SyllabusSuggesterAgent",
    description = "Produces a strict, scoped syllabus (chapter list with " +
                  "bulleted sub-points) for a given subject/topic/level. " +
                  "Used when the learner clicks 'Suggest a syllabus' or " +
                  "the pipeline needs a default syllabus before planning."
)
@StructuredOutput(schema = Syllabus.class, retryOnMalformed = true, maxRetries = 2)
@Retry(maxAttempts = 2, backoffMs = 1000)
@Traced(spanName = "syllabus-suggest")
public class SyllabusSuggesterAgent {

    /**
     * Build the prompt for a syllabus suggestion.
     *
     * Hard rules embedded in the prompt:
     *   - Echo the subject + topic exactly (so the controller can verify
     *     the LLM didn't drift into a different subject).
     *   - Stay strictly inside the stated subject/topic — no adjacent
     *     subjects, no general "study skills" filler.
     *   - 4–8 chapters; each chapter has 2–5 bulleted sub-points.
     *   - Mark source = "SUGGESTED" so /save can distinguish it from a
     *     learner-pasted CUSTOM syllabus later.
     *
     * @param subject mandatory — must be a real subject name (e.g. "Mathematics")
     * @param topic   mandatory — must be a topic inside the subject
     * @param level   one of PRIMARY / MIDDLE_SCHOOL / SENIOR_SCHOOL / UNIVERSITY / EDUCATOR / PROFESSIONAL
     */
    /**
     * Extract a Syllabus from raw text — typically the output of:
     *   - PDFBox text extraction on an uploaded syllabus PDF, or
     *   - a vision-LLM transcription of a syllabus image.
     *
     * The agent's job here is normalisation, not invention: pull the
     * subject, topic, level, and chapter list out of whatever messy
     * text the upload produced and emit them as a clean Syllabus.
     *
     * Hard rules embedded:
     *   - source MUST be "CUSTOM" (the learner uploaded their own
     *     material, even if the file came from an institution).
     *   - subject + topic MUST be inferred from the document content,
     *     not from training-data adjacent topics.
     *   - level guesses are explicit when uncertain (default
     *     SENIOR_SCHOOL) — don't pretend to know.
     *   - chapters preserve the document's own ordering and naming;
     *     don't re-paragraph or merge.
     *   - if the document is clearly NOT a syllabus, set chapters to
     *     an empty string and put the disqualifying reason in
     *     `rationale` so the controller can show a useful error.
     *
     * @param hint optional caller-supplied subject hint to disambiguate
     *             documents that span multiple subjects; pass empty
     *             string when no hint is available.
     */
    public String extractFromTextPrompt(String rawText, String hint) {
        String hintLine = (hint == null || hint.isBlank())
            ? ""
            : "Caller's best guess at subject: " + hint + " (verify against the document).\n";
        // Truncate very long uploads so we don't blow the LLM context.
        // ~12k chars ≈ ~3k tokens at OpenAI's tokenisation, plenty for a
        // typical 2–3 page syllabus document.
        String body = rawText == null ? "" : rawText.length() > 12_000
            ? rawText.substring(0, 12_000) + "\n…(truncated)"
            : rawText;
        return """
            You are normalising a learner-uploaded document into a clean
            Syllabus JSON object. The document text is below.

            Your job: identify the SINGLE subject and topic the document
            covers, and emit the chapter list in the document's own order.

            Hard rules:
              1. Read the document. Identify the subject and topic from
                 ITS content — do NOT default to a familiar example.
              2. If the document is clearly not a syllabus (e.g. a
                 random article, image of homework, blank page), set
                 `chapters` to an empty string and explain in `rationale`.
              3. Preserve the document's chapter ordering and chapter
                 names. Do NOT rephrase. Add bullet sub-points only when
                 they appear in the document.
              4. Format chapters as "Chapter N: <title>" with the doc's
                 numbering. If the doc uses "Unit"/"Module"/"Week", keep
                 that label.
              5. Level: pick from PRIMARY / MIDDLE_SCHOOL / SENIOR_SCHOOL
                 / UNIVERSITY / EDUCATOR / PROFESSIONAL based on the
                 document's complexity. Default SENIOR_SCHOOL when
                 unclear.
              6. `source` MUST be exactly "CUSTOM".
              7. `rationale` is one sentence (the inference for the
                 level, or the disqualifying reason).

            %s
            Document text:
            ----
            %s
            ----

            Output a complete JSON Syllabus object matching the schema.
            """.formatted(hintLine, body);
    }

    public String suggestPrompt(String subject, String topic, String level) {
        return """
            You are an experienced curriculum designer. Produce a tight,
            level-appropriate syllabus for the SINGLE (subject, topic) below.

            Subject: %s
            Topic:   %s
            Level:   %s

            Hard rules:
              1. Stay STRICTLY inside the stated subject and topic. Do NOT
                 drift into adjacent subjects. If the topic is ambiguous,
                 assume the most common interpretation INSIDE the subject.
              2. Echo `subject` and `topic` exactly as given.
              3. Produce 4–8 chapters total — no fewer, no more.
              4. Each chapter starts with "Chapter N: <title>" on its own
                 line, followed by 2–5 bulleted sub-points beginning with
                 "- ".
              5. Order chapters so each builds on the previous. Foundations
                 first, applications last.
              6. Calibrate depth to the level:
                   PRIMARY        → concrete, intuition-led, no formal proofs
                   MIDDLE_SCHOOL  → mix intuition + light formalism
                   SENIOR_SCHOOL  → exam-style depth, named theorems
                   UNIVERSITY     → rigour, derivations, edge cases
                   EDUCATOR       → pedagogy + common misconceptions
                   PROFESSIONAL   → applied, time-efficient
              7. `source` MUST be exactly "SUGGESTED".
              8. `rationale` is one sentence. No more.

            Output a complete JSON Syllabus object matching the schema.
            """.formatted(
                subject == null ? "(unknown)" : subject,
                topic   == null ? "(unknown)" : topic,
                level   == null ? "MIDDLE_SCHOOL" : level
            );
    }
}
