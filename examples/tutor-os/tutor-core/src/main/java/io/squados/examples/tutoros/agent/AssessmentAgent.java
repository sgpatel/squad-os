package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.AssessmentFeedback;
import io.squados.examples.tutoros.model.PracticeQuestion;

/**
 * Assessment Agent — grades the student's answer and produces a typed
 * AssessmentFeedback with per-part analysis, a hint, and encouragement.
 *
 * Grading philosophy:
 *   - Never a bare "wrong" — always explain what WAS correct first
 *   - Encouragement is mandatory regardless of score
 *   - Hints are progressive: broad → specific → near-answer
 *   - masteryDelta drives ProgressAgent to update the concept mastery
 *
 * Escalation trigger:
 *   If the learner answers the same question INCORRECTLY 3 times,
 *   AssessmentAgent sets suggestRetry=false and signals EscalationAgent.
 *
 * Features:
 *   @StructuredOutput — typed AssessmentFeedback; retry 3× on malformed JSON
 *   @Benchmark        — grading accuracy vs teacher-graded golden set
 *   @AgentMemory      — recalls how the learner answered similar questions before
 *   @Traced           — every grading call is a named span
 */
@Agent(
    role        = AgentRole.CRITIC,
    name        = "AssessmentAgent",
    description = "Grades student answers with line-level feedback: " +
                  "correct parts, incorrect parts, a targeted hint, " +
                  "encouragement, and mastery delta. Never 'just wrong'."
)
@StructuredOutput(schema = AssessmentFeedback.class, retryOnMalformed = true, maxRetries = 3)
@Benchmark(
    dataset  = "classpath:benchmarks/assessment-grading-golden.json",
    minScore = 0.85f
)
@AgentMemory(topK = 2, minScore = 0.80f, scope = "squad")
@Traced(spanName = "assessment-grading")
public class AssessmentAgent {

    /**
     * Main grading prompt.
     *
     * @param question       the PracticeQuestion that was posed
     * @param studentAnswer  the raw student answer text
     * @param attemptNumber  1, 2, or 3 (changes hint depth)
     * @param learnerLevel   e.g. MIDDLE_SCHOOL (affects language in feedback)
     * @param goal           PASS_EXAMS → mark-scheme language in model answer
     * @param memoryContext  past answers to this concept from @AgentMemory
     */
    public String gradingPrompt(PracticeQuestion question, String studentAnswer,
                                 int attemptNumber, String learnerLevel,
                                 String goal, String memoryContext) {

        String hintDepth = switch (attemptNumber) {
            case 1  -> "broad — point toward the general area";
            case 2  -> "specific — name the relevant mechanism or term";
            default -> "near-answer — almost give it to them but make them say it";
        };

        return """
            You are an expert, compassionate examiner for %s-level learners.

            Question: %s
            Model answer: %s
            Marks available: %d
            Bloom's level targeted: %s

            Student answered (attempt %d of 3):
            "%s"

            Past memory for this learner on this concept:
            %s

            Grade the answer and produce a JSON AssessmentFeedback with:
            - score: 0–100 (not marks — percentage of marks earned)
            - correct: true only if full marks earned
            - correctParts: quote what was correct (be specific, not vague)
            - incorrectParts: what was wrong or missing (factual, not unkind)
            - hint: %s
            - encouragement: warm, personal, genuine — always positive
            - modelAnswer: %s
            - bloomsDemonstrated: what level did their answer actually show?
            - masteryDelta: +0.10 if correct first attempt, +0.05 if correct with hints,
                            -0.05 if wrong (min 0)
            - suggestRetry: true if score < 80 AND attemptNumber < 3
            - nextConcept: the logical next concept to study

            Never say "incorrect" or "wrong" alone — always explain WHY and WHAT to do next.
            """.formatted(
                learnerLevel,
                question.question, question.answer, question.marks,
                question.bloomsLevel,
                attemptNumber, studentAnswer,
                memoryContext,
                hintDepth,
                goal.equals("PASS_EXAMS")
                    ? "write in mark-scheme language (e.g. '1 mark for: ...')"
                    : "write in plain friendly language"
            );
    }

    /**
     * Escalation-check after 3 failed attempts on the same concept.
     * Returns true if EscalationAgent should be invoked.
     */
    public boolean shouldEscalate(int consecutiveFailures, boolean distressDetected) {
        return consecutiveFailures >= 3 || distressDetected;
    }
}
