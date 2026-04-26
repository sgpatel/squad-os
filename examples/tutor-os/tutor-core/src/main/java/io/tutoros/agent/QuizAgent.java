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
    role        = AgentRole.DPS,
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

            Each question MUST be a JSON object with EXACTLY these field
            names (no synonyms — do not rename, do not add fields):
              "question"       : string — the question text
              "type"           : "MULTIPLE_CHOICE" | "SHORT_ANSWER"
              "options"        : string — for MULTIPLE_CHOICE only, exactly 4
                                 options joined by " | " (pipe), e.g.
                                 "Option A | Option B | Option C | Option D".
                                 OMIT for SHORT_ANSWER.
              "answer"         : string — for MULTIPLE_CHOICE this is the
                                 correct option text (must match one of the
                                 options exactly); for SHORT_ANSWER this is
                                 a one-line model answer.
              "bloomsLevel"    : "REMEMBER"|"UNDERSTAND"|"APPLY"|"ANALYZE"|"EVALUATE"|"CREATE"
              "difficulty"     : "EASY" | "MEDIUM" | "HARD"
              "conceptTag"     : string — the concept this question tests
              "hints"          : string — up to 2 short hints separated by " | "
              "workedSolution" : string — 1–2 short sentences (NOT an essay)
              "marks"          : integer

            Be concise — keep total output under ~3000 tokens so the JSON
            is never truncated. Do not invent extra fields.

            Quiz-level requirements:
            - questionsJson: JSON array of PracticeQuestion objects, serialised
              as a STRING (escape inner quotes). This field is REQUIRED — never
              omit it, never end the response before closing it.
            - bloomsLevelsCovered: comma-separated Bloom levels present.
            - targetGaps: comma-separated gap concepts covered.
            - totalMarks: sum of all question marks.
            - timeLimitMinutes: %d

            Calibrate question language to %s level — not simpler, not harder.
            Keep prose tight. Prioritise completing every required field over
            verbosity in any single field.
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
