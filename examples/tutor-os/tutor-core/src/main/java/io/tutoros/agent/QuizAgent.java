package io.tutoros.agent;

import io.squados.annotation.*;
import io.tutoros.model.LearnerProfile;
import io.tutoros.model.Quiz;

/**
 * Quiz Agent — generates a complete, calibrated quiz on demand.
 *
 * Called when:
 *   - Student clicks "Take Quiz" in the UI
 *   - Student types "quiz me on [topic]"
 *   - After 3 consecutive sessions on the same chapter (automatic)
 *   - Teacher triggers a quiz from the portal
 *
 * Quiz composition strategy:
 *   - 60% questions on gap concepts (heaviest weight)
 *   - 30% questions on recently taught concepts (consolidation)
 *   - 10% questions on mastered concepts (confidence + retention check)
 *
 * Difficulty distribution by learner goal:
 *   PASS_EXAMS        → 20% EASY, 50% MEDIUM, 30% HARD (exam simulation)
 *   DEEP_UNDERSTANDING→ 10% EASY, 40% MEDIUM, 50% HARD (push thinking)
 *   QUICK_REVISION    → 50% EASY, 40% MEDIUM, 10% HARD (confidence rebuild)
 *   CAREER_UPSKILLING → 0% EASY, 40% MEDIUM, 60% HARD (practical challenge)
 *
 * Features:
 *   @StructuredOutput — typed Quiz with embedded PracticeQuestion JSON array
 *   @Benchmark        — quiz quality vs teacher-designed golden quiz set
 *   @AgentMemory      — recalls which questions the learner got wrong before
 *                        (avoids repeating easy ones, re-tests failed ones)
 *   @Traced           — quiz generation is a named span
 */
@Agent(
    role        = AgentRole.EXECUTOR,
    name        = "QuizAgent",
    description = "Generates calibrated on-demand quizzes targeting gap concepts. " +
                  "Adapts question count, difficulty distribution, and type " +
                  "to the learner's goal and current mastery profile."
)
@StructuredOutput(schema = Quiz.class, retryOnMalformed = true, maxRetries = 3)
@Benchmark(
    dataset  = "classpath:benchmarks/quiz-golden.json",
    minScore = 0.80f
)
@AgentMemory(topK = 5, minScore = 0.65f, scope = "squad")
@Traced(spanName = "quiz-generation")
public class QuizAgent {

    /**
     * Generates a full quiz for the learner.
     *
     * @param profile        current learner profile (gaps, mastery, style)
     * @param topic          the topic to quiz on
     * @param questionCount  requested number of questions (default 5)
     * @param overrideDiff   null = auto-calibrate, else "EASY"|"MEDIUM"|"HARD"|"MIXED"
     * @param memoryContext  previously wrong questions from @AgentMemory
     */
    public String quizPrompt(LearnerProfile profile, String topic,
                              int questionCount, String overrideDiff,
                              String memoryContext) {

        String diffProfile  = overrideDiff != null ? overrideDiff
                                                    : difficultyProfile(profile.goal);
        String bloomTarget  = profile.bloomsLevel;
        int    timeLimitMin = questionCount * 2; // ~2 min per question

        return """
            Generate a complete JSON Quiz for the following learner.

            Learner: %s | Level: %s | Goal: %s
            Topic: %s
            Learning style: %s | Bloom's level: %s

            Questions requested: %d | Difficulty profile: %s | Time limit: %d min

            Gap concepts (60%% of questions):  %s
            Recent concepts (30%% questions):  %s
            Mastered concepts (10%% questions): %s

            Previously wrong questions to re-test (from memory):
            %s

            Requirements for each question:
            1. Include a complete worked solution
            2. Include 3 progressive hints
            3. For MULTIPLE_CHOICE: 4 options, all plausible (no obviously wrong distractors)
            4. For SHORT_ANSWER: include a model answer with mark-scheme bullet points
            5. Tag each question with its Bloom's level and concept

            Quiz-level requirements:
            - questionsJson: JSON array of PracticeQuestion objects
            - bloomsLevelsCovered: list the Bloom's levels present
            - targetGaps: which gap concepts appear in this quiz
            - totalMarks: sum of all question marks
            - timeLimitMinutes: %d

            Calibrate question language to %s level — not simpler, not harder.
            """.formatted(
                profile.name, profile.level, profile.goal,
                topic, profile.learningStyle, bloomTarget,
                questionCount, diffProfile, timeLimitMin,
                profile.gapConcepts,
                profile.masteredConcepts, // recently taught ≈ mastered but not long-term
                profile.masteredConcepts,
                memoryContext,
                timeLimitMin, profile.level
            );
    }

    // ── Difficulty profiles per goal ─────────────────────────────────
    private String difficultyProfile(String goal) {
        return switch (goal) {
            case "PASS_EXAMS"         -> "20% EASY, 50% MEDIUM, 30% HARD";
            case "DEEP_UNDERSTANDING" -> "10% EASY, 40% MEDIUM, 50% HARD";
            case "QUICK_REVISION"     -> "50% EASY, 40% MEDIUM, 10% HARD";
            case "CAREER_UPSKILLING"  -> "0% EASY, 40% MEDIUM, 60% HARD";
            case "TEACH_OTHERS"       -> "30% EASY, 50% MEDIUM, 20% HARD";
            default                   -> "30% EASY, 50% MEDIUM, 20% HARD";
        };
    }
}
