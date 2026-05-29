package io.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * A set of actionable learning todos produced by TodoAgent after each session.
 *
 * SquadOS: @StructuredOutput(schema = TodoList.class) + @AutoPlan
 *
 * TodoAgent receives the SessionSummary and generates a ranked, actionable
 * list calibrated to the learner's goal and available time before next session.
 *
 * Todos are tagged by:
 *   - effort: how long each should take (SHORT / MEDIUM / LONG)
 *   - type:   REVISE | PRACTICE | WATCH | CREATE | TEACH | READ
 *   - concept: which gap concept it targets
 */
public class TodoList {

    @OutputField(description = "Session this todo list was generated from", example = "SESSION-2024-Priya-014")
    public String sessionId;

    @OutputField(description = "Number of todos generated", example = "4")
    public int count;

    @OutputField(description = "JSON array: [{text, type, effort, concept, doneBy}] serialised as string")
    public String todosJson;

    @OutputField(description = "Highest-priority single action the learner should do first",
                 example     = "Revise the Z-scheme diagram — this concept appeared in 3 wrong answers today")
    public String topPriority;

    @OutputField(description = "Estimated total minutes to complete all todos", example = "45")
    public int totalEstimatedMinutes;

    @OutputField(description = "ISO-8601 recommended completion date (before next session)", example = "2024-09-04")
    public String completeBefore;
}
