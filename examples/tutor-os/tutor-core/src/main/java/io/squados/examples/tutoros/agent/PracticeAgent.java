package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.PracticeQuestion;

/**
 * Practice Agent — generates a calibrated PracticeQuestion after each
 * tutor explanation, and on-demand when the student asks for one.
 *
 * Calibration logic (Zone of Proximal Development):
 *   - Bloom's level = one level above the learner's demonstrated level
 *   - Difficulty    = medium if mastery 40–70%, easy below, hard above
 *   - Question type = adapted to learningStyle:
 *       VISUAL      → DIAGRAM_LABEL or description-based
 *       ANALYTICAL  → CALCULATION or data-interpretation
 *       NARRATIVE   → SHORT_ANSWER with context scenario
 *       HANDS_ON    → describe-an-experiment style
 *       default     → MULTIPLE_CHOICE
 *
 * @AgentMemory recalls which question types and concepts generated the
 * most mastery gain in previous sessions, and prioritises those.
 *
 * Features:
 *   @StructuredOutput — typed PracticeQuestion, retry on malformed JSON
 *   @AgentMemory      — recalls high-yield question patterns
 *   @Benchmark        — question quality vs golden exam question set
 */
@Agent(
    role        = AgentRole.EXECUTOR,
    name        = "PracticeAgent",
    description = "Generates a calibrated practice question at exactly the right " +
                  "Bloom's level and difficulty for the learner. " +
                  "Adapts question type to learning style."
)
@StructuredOutput(schema = PracticeQuestion.class, retryOnMalformed = true, maxRetries = 3)
@AgentMemory(topK = 3, minScore = 0.75f, scope = "squad")
@Benchmark(
    dataset  = "classpath:benchmarks/practice-question-golden.json",
    minScore = 0.78f
)
@Traced(spanName = "practice-generation")
public class PracticeAgent {

    /**
     * Generates a single practice question.
     *
     * @param concept      the concept just taught
     * @param bloomsLevel  learner's CURRENT Bloom's level (question targets one above)
     * @param mastery      0–100 current mastery
     * @param level        learner level e.g. MIDDLE_SCHOOL
     * @param learningStyle VISUAL | ANALYTICAL | NARRATIVE | HANDS_ON | SOCRATIC
     * @param goal         PASS_EXAMS | DEEP_UNDERSTANDING | QUICK_REVISION | etc.
     * @param memoryContext recalled high-yield question patterns from memory
     */
    public String generatePrompt(String concept, String bloomsLevel, int mastery,
                                  String level, String learningStyle, String goal,
                                  String memoryContext) {

        String targetBloom  = nextBloom(bloomsLevel);
        String difficulty   = mastery < 40 ? "EASY" : mastery > 70 ? "HARD" : "MEDIUM";
        String questionType = preferredType(learningStyle, goal);

        return """
            You are an expert question writer for %s-level learners.

            Concept to test: %s
            Target Bloom's level: %s (one above their current: %s)
            Difficulty: %s | Question type: %s
            Learner mastery of this concept: %d%%

            High-yield question patterns from past sessions:
            %s

            Generate ONE practice question as a JSON PracticeQuestion with all fields populated.

            Requirements:
            - The question MUST require the learner to operate at %s level — not lower
            - Provide 3 progressive hints (from broad to specific)
            - Write a complete worked solution with step-by-step reasoning
            - For MULTIPLE_CHOICE: exactly 4 options, one correct, all plausible distractors
            - For DIAGRAM_LABEL: describe the diagram in text, list the labels to fill in
            - marks field should reflect exam-style mark allocation (1 mark per fact/step)
            %s
            """.formatted(
                level, concept, targetBloom, bloomsLevel,
                difficulty, questionType, mastery,
                memoryContext, targetBloom,
                goal.equals("PASS_EXAMS") ? "- Write in the style of official exam questions (mark scheme language)" : ""
            );
    }

    /**
     * Generates a mini-quiz of N questions targeting the gap concepts.
     * Used by QuizAgent to compose full quiz sessions.
     */
    public String quizBatchPrompt(String[] gapConcepts, int questionCount,
                                   String difficulty, String level, String bloomsLevel) {
        return """
            Generate a quiz of %d questions targeting these gap concepts: %s

            Learner: %s level | Difficulty: %s | Bloom's target: %s

            Distribute questions across all gap concepts proportionally.
            Mix question types: at least 60%% MULTIPLE_CHOICE, rest SHORT_ANSWER or CALCULATION.
            Output a JSON array of PracticeQuestion objects.
            """.formatted(
                questionCount,
                String.join(", ", gapConcepts),
                level, difficulty, bloomsLevel
            );
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private String nextBloom(String current) {
        return switch (current.toUpperCase()) {
            case "REMEMBER"  -> "UNDERSTAND";
            case "UNDERSTAND"-> "APPLY";
            case "APPLY"     -> "ANALYSE";
            case "ANALYSE"   -> "EVALUATE";
            case "EVALUATE"  -> "CREATE";
            default          -> "UNDERSTAND";
        };
    }

    private String preferredType(String learningStyle, String goal) {
        if (goal.equals("PASS_EXAMS")) return "MULTIPLE_CHOICE";
        return switch (learningStyle.toUpperCase()) {
            case "VISUAL"    -> "DIAGRAM_LABEL";
            case "ANALYTICAL"-> "CALCULATION";
            case "NARRATIVE" -> "SHORT_ANSWER";
            case "HANDS_ON"  -> "SHORT_ANSWER"; // describe-an-experiment
            default          -> "MULTIPLE_CHOICE";
        };
    }
}
