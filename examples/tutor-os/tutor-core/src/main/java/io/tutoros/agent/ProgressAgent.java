package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.AssessmentFeedback;
import io.squados.examples.tutoros.model.LearnerProfile;
import io.squados.examples.tutoros.model.SessionSummary;

/**
 * Progress Agent — the long-term memory keeper of TutorOS.
 *
 * After every AssessmentFeedback, ProgressAgent:
 *   1. Updates the concept-level mastery score in @AgentMemory
 *   2. Detects plateaus: 3+ sessions with no mastery gain on a concept
 *   3. Detects acceleration: mastery jumped >20% in one session (learner clicked)
 *   4. Recalculates overall topic mastery from concept-level scores
 *   5. Determines if CurriculumPlannerAgent should advance to the next chapter
 *   6. Flags at-risk learners (plateau + frustration signals) for EscalationAgent
 *
 * After every SessionSummary, ProgressAgent:
 *   7. Persists the session record to @DurableAgent store
 *   8. Updates the learner's streak counter
 *   9. Generates the "parent note" for the teacher/parent portal
 *
 * Features:
 *   @AgentMemory  — concept mastery graph is stored in and retrieved from memory
 *   @DurableAgent — session history survives restarts (checkpointed to store)
 *   @Traced       — every progress update is a named span
 */
@Agent(
    role        = AgentRole.SUPPORT,
    name        = "ProgressAgent",
    description = "Maintains concept-level mastery scores, session history, " +
                  "and learning streaks. Detects plateaus and accelerations. " +
                  "Determines when to advance chapters or escalate to a teacher."
)
@AgentMemory(topK = 10, minScore = 0.60f, scope = "squad")
@DurableAgent(store = "memory", ttlHours = 720) // 30 days session history
@Traced(spanName = "progress-update")
public class ProgressAgent {

    // ── Mastery thresholds ───────────────────────────────────────────
    /** Mastery above this → concept considered "done", advance chapter. */
    private static final double MASTERY_DONE_THRESHOLD     = 0.85;

    /** Drop below this after being above DONE → schedule targeted review. */
    private static final double MASTERY_REVIEW_THRESHOLD   = 0.65;

    /** Plateau = mastery change < this over 3 consecutive sessions. */
    private static final double PLATEAU_CHANGE_THRESHOLD   = 0.03;

    /** Acceleration = mastery gain > this in one session. */
    private static final double ACCELERATION_THRESHOLD     = 0.20;

    /**
     * Prompt for updating concept mastery after an assessment.
     *
     * The LLM receives the full context and produces a structured update
     * record that ProgressAgent persists via @AgentMemory.
     */
    public String masteryUpdatePrompt(String concept, double currentMastery,
                                       AssessmentFeedback feedback,
                                       int consecutiveFailures, int sessionCount) {
        double newMastery = Math.min(1.0, Math.max(0.0, currentMastery + feedback.masteryDelta));

        return """
            Update the mastery record for concept: %s

            Current mastery: %.0f%%
            Feedback delta: %+.0f%%
            New mastery: %.0f%%
            Consecutive failures: %d
            Sessions on this concept: %d

            Determine:
            1. STATUS: MASTERED (≥85%%) | PROGRESSING | PLATEAU (<3%% change over 3 sessions) | STRUGGLING (≤20%%)
            2. ACTION: ADVANCE | CONTINUE | REVIEW | ESCALATE
            3. Write a 1-sentence memory record summarising the learner's state on this concept.
               This will be retrieved in future sessions via semantic search.

            Output format (JSON):
            {
              "concept": "...",
              "mastery": 0.00,
              "status": "...",
              "action": "...",
              "memoryRecord": "..."
            }
            """.formatted(
                concept, currentMastery * 100, feedback.masteryDelta * 100,
                newMastery * 100, consecutiveFailures, sessionCount
            );
    }

    /**
     * Prompt for generating a session summary after all questions are answered.
     * Called at the end of every tutoring session.
     */
    public String sessionSummaryPrompt(String learnerId, String chapter,
                                        int durationMinutes, int questionsAttempted,
                                        String masteryChanges, String conceptsCovered,
                                        boolean distressDetected, String teachingStyle) {
        return """
            Generate a complete JSON SessionSummary for this tutoring session.

            Learner: %s
            Chapter: %s
            Duration: %d minutes | Questions attempted: %d
            Teaching style used: %s
            Distress detected: %b

            Mastery changes this session:
            %s

            Concepts covered:
            %s

            Requirements:
            - sessionScore: weighted average of all question scores
            - conceptsMastered: concepts that reached ≥70%% mastery this session
            - revisitConcepts: concepts still below 70%% — these drive next session priority
            - keyInsight: the most important learning moment from this session
            - generatedTodos: 3–5 actionable items, specific to the gaps, ranked by priority
            - parentNote: 1–2 sentences suitable for a parent or teacher; factual and positive
            - masteryGained: integer percentage improvement in overall topic mastery
            """.formatted(
                learnerId, chapter, durationMinutes, questionsAttempted,
                teachingStyle, distressDetected, masteryChanges, conceptsCovered
            );
    }

    /**
     * Detects whether a learner has reached a plateau on a concept.
     * Called after each session update.
     *
     * @param masteryHistory  recent mastery values, newest last
     * @return true if mastery has not improved significantly in 3 sessions
     */
    public boolean isPlateaued(double[] masteryHistory) {
        if (masteryHistory.length < 3) return false;
        int n = masteryHistory.length;
        double change = Math.abs(masteryHistory[n - 1] - masteryHistory[n - 3]);
        return change < PLATEAU_CHANGE_THRESHOLD;
    }

    /**
     * Determines if the learner is ready to advance to the next chapter.
     */
    public boolean readyToAdvance(LearnerProfile profile, String concept, double mastery) {
        return mastery >= MASTERY_DONE_THRESHOLD;
    }
}
