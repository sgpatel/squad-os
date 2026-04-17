package io.squados.examples.tutoros.agent;

import io.squados.annotation.*;
import io.squados.examples.tutoros.model.SessionSummary;
import io.squados.examples.tutoros.model.TodoList;

/**
 * Todo Agent — converts session outcomes into actionable learning todos.
 *
 * Triggered after every session summary. Uses @AutoPlan to iterate
 * until the todo list covers every revisit concept from the summary.
 *
 * Todo calibration by goal:
 *   PASS_EXAMS        → todos are exam-prep tasks (past papers, mark schemes)
 *   DEEP_UNDERSTANDING→ todos include extension reading and "teach it back" tasks
 *   QUICK_REVISION    → max 3 todos, all SHORT effort (< 10 min each)
 *   CAREER_UPSKILLING → todos include practical exercises (build, apply, implement)
 *   TEACH_OTHERS      → todos include "explain to a colleague" tasks
 *   CURIOSITY         → todos include rabbit-hole exploration links
 *
 * Each todo includes:
 *   - text:    specific, actionable instruction (not vague like "study more")
 *   - type:    REVISE | PRACTICE | WATCH | CREATE | TEACH | READ
 *   - effort:  SHORT (<10 min) | MEDIUM (10–30 min) | LONG (30+ min)
 *   - concept: which gap concept this targets
 *   - doneBy:  recommended completion date (before next session)
 *
 * Features:
 *   @StructuredOutput — typed TodoList with JSON array of todo items
 *   @AutoPlan         — iterates until all revisit concepts have ≥1 todo
 *   @AgentMemory      — recalls which todo types the learner actually completes
 */
@Agent(
    role        = AgentRole.EXECUTOR,
    name        = "TodoAgent",
    description = "Generates specific, actionable learning todos after every session. " +
                  "Calibrated to the learner's goal, available time, and gap concepts. " +
                  "Tracks which todo types the learner actually completes."
)
@StructuredOutput(schema = TodoList.class, retryOnMalformed = true, maxRetries = 2)
@AutoPlan(
    goal            = "Every revisit concept from the session summary has at least one specific, " +
                      "actionable todo that the learner can complete before the next session",
    maxIterations   = 3,
    reflectOn       = "Does every revisit concept have a todo? Are todos specific and actionable? " +
                      "Do they fit the learner's available time and goal?",
    stopCondition   = "ALL_GAPS_COVERED",
    onMaxIterations = IterationPolicy.RETURN_BEST
)
@AgentMemory(topK = 3, minScore = 0.70f, scope = "agent")
@Traced(spanName = "todo-generation")
public class TodoAgent {

    /**
     * Main todo generation prompt.
     *
     * @param summary         the session summary from ProgressAgent
     * @param goal            learner's learning goal
     * @param sessionsPerWeek how often they study (affects doneBy date)
     * @param completionRate  0.0–1.0 historical rate of completing todos (from memory)
     */
    public String generatePrompt(SessionSummary summary, String goal,
                                  int sessionsPerWeek, double completionRate) {

        int maxTodos = goal.equals("QUICK_REVISION") ? 3 : 5;
        String effort = completionRate < 0.5 ? "SHORT or MEDIUM only — this learner completes more when todos are quick"
                                             : "mix of SHORT, MEDIUM, and LONG";

        return """
            Generate a JSON TodoList for a learner after this tutoring session.

            Session summary:
            - Chapter: %s
            - Score: %d%%
            - Concepts mastered: %s
            - Concepts to revisit: %s
            - Key insight: %s

            Learner goal: %s
            Study frequency: %d sessions/week
            Todo effort calibration: %s
            Maximum todos: %d

            Generate specific, actionable todos. Each must:
            1. Name the EXACT concept, not just "study biology"
            2. Specify HOW to study it (draw, write, answer questions, watch, explain)
            3. Be completable in the time specified by effort level
            4. Be framed positively ("Draw the Z-scheme from memory" not "Learn Z-scheme")

            Todo types to use based on goal:
            %s

            Also set:
            - topPriority: the single most important todo (the biggest gap from today)
            - totalEstimatedMinutes: sum of all todo effort estimates
            - completeBefore: date of next session (today + %d days approximately)
            """.formatted(
                summary.chapterCovered,
                summary.sessionScore,
                summary.conceptsMastered,
                summary.revisitConcepts,
                summary.keyInsight,
                goal,
                sessionsPerWeek,
                effort,
                maxTodos,
                todoTypesForGoal(goal),
                7 / Math.max(1, sessionsPerWeek)
            );
    }

    private String todoTypesForGoal(String goal) {
        return switch (goal) {
            case "PASS_EXAMS"         -> "PRACTICE (past paper Qs), REVISE (mark scheme review), READ (examiners' report)";
            case "DEEP_UNDERSTANDING" -> "CREATE (mind map, diagram), TEACH (explain to someone), READ (extension article)";
            case "QUICK_REVISION"     -> "REVISE (flashcards), PRACTICE (3 quick questions max)";
            case "CAREER_UPSKILLING"  -> "CREATE (implement/apply), PRACTICE (real problem), READ (industry case study)";
            case "TEACH_OTHERS"       -> "TEACH (explain to a colleague), CREATE (lesson plan), REVISE (common misconceptions)";
            default                   -> "REVISE, PRACTICE, READ";
        };
    }
}
