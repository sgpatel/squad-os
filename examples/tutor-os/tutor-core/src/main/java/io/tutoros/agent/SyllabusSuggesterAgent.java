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
