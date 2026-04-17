package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.LearnerProfile;

/**
 * Diagnostic Agent — runs once at the start of each new subject and
 * every 5 sessions (re-diagnostic) to keep the profile fresh.
 *
 * Delivers a 5-question adaptive diagnostic:
 *   Q1 — REMEMBER level (calibration)
 *   Q2 — UNDERSTAND level (if Q1 correct, else stay at REMEMBER)
 *   Q3 — APPLY level
 *   Q4 — ANALYSE level
 *   Q5 — Open-ended (infers learning style from answer style)
 *
 * Produces a typed LearnerProfile (@StructuredOutput) that persists
 * in @AgentMemory and drives every subsequent agent decision.
 *
 * Features:
 *   @StructuredOutput — typed LearnerProfile output with retry on malformed JSON
 *   @AgentMemory      — recalls previous diagnostic results to track growth
 *   @Traced           — every diagnostic session is a named span in OTel
 */
@Agent(
    role        = AgentRole.ANALYST,
    name        = "DiagnosticAgent",
    description = "Runs an adaptive 5-question diagnostic to build a typed LearnerProfile. " +
                  "Identifies mastered concepts, knowledge gaps, learning style, " +
                  "Bloom's level, and preferred teaching approach."
)
@StructuredOutput(schema = LearnerProfile.class, retryOnMalformed = true, maxRetries = 3)
@AgentMemory(topK = 2, minScore = 0.80f, scope = "agent")
@Traced(spanName = "diagnostic-assessment")
public class DiagnosticAgent {

    /**
     * Builds the adaptive diagnostic prompt.
     *
     * @param name          learner's name
     * @param level         e.g. "MIDDLE_SCHOOL"
     * @param subject       e.g. "Biology"
     * @param topic         e.g. "Photosynthesis"
     * @param goal          e.g. "PASS_EXAMS"
     * @param profession    e.g. "Software Engineer" (empty for school students)
     */
    public String diagnosticPrompt(String name, String level, String subject,
                                   String topic, String goal, String profession) {
        String levelContext = buildLevelContext(level, profession);
        return """
            You are an expert educational diagnostician.

            You are assessing %s, a %s learner%s.
            Subject: %s | Topic: %s | Goal: %s

            Run an adaptive 5-question diagnostic. Ask each question, wait for the
            answer, then adapt the next question based on the response:
              Q1: REMEMBER level — basic recall
              Q2: UNDERSTAND level (if Q1 correct) or REMEMBER (if Q1 wrong)
              Q3: APPLY level
              Q4: ANALYSE level
              Q5: Open-ended — "Explain %s in your own words as if to a friend"

            After Q5, produce a complete JSON LearnerProfile with all fields populated.

            Important:
            - Set learningStyle by analysing HOW they answered, not just WHAT they answered
              (VISUAL = uses diagrams/images in words, ANALYTICAL = uses numbers/logic,
               NARRATIVE = uses stories/analogies, SOCRATIC = asks back-questions)
            - Set bloomsLevel to the highest level they demonstrated correctly
            - Set masteredConcepts to things they got right
            - Set gapConcepts to things they got wrong or skipped
            - Set preferredTeachingStyle to DIRECT if they want explanations,
              SOCRATIC if they engage better with guided questions
            """.formatted(name, levelContext, profession.isBlank() ? "" : " ("+profession+")",
                          subject, topic, goal, topic);
    }

    /**
     * Re-diagnostic prompt — run after every 5 sessions.
     * Shorter than the initial diagnostic; focuses on growth.
     */
    public String reDiagnosticPrompt(String name, String subject, String topic,
                                     String previousMastery, String previousGaps) {
        return """
            You are reassessing %s's progress in %s — %s.

            Previous state (from 5 sessions ago):
              Mastery: %s%%
              Gaps:    %s

            Ask 3 targeted questions on the previous gap concepts only.
            Then update and return a revised LearnerProfile JSON.

            Show progress explicitly in the masteryPercent and masteredConcepts fields.
            """.formatted(name, subject, topic, previousMastery, previousGaps);
    }

    private String buildLevelContext(String level, String profession) {
        return switch (level) {
            case "PRIMARY"       -> "primary school (age 5–11)";
            case "MIDDLE_SCHOOL" -> "middle school (age 11–16)";
            case "SENIOR_SCHOOL" -> "senior school (age 16–18)";
            case "UNIVERSITY"    -> "university";
            case "EDUCATOR"      -> "educator / teacher";
            case "PROFESSIONAL"  -> "working professional (" + profession + ")";
            default              -> level.toLowerCase();
        };
    }
}
