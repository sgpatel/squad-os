package io.squados.examples.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * Post-session summary produced by SummaryAgent at session end.
 *
 * SquadOS: @StructuredOutput(schema = SessionSummary.class) + @Streaming
 *
 * The summary is streamed to the learner as it is generated, then
 * persisted by @DurableAgent to the session store for later review.
 *
 * It feeds forward into the next session:
 *   - revisitConcepts → CurriculumPlannerAgent prioritises these
 *   - generatedTodos  → TodoAgent stores into the learner's todo list
 *   - parentNote      → optional note for parent/teacher portal
 */
public class SessionSummary {

    @OutputField(description = "Session ID, e.g. SESSION-2024-Priya-014")
    public String sessionId;

    @OutputField(description = "Chapter and topic covered in this session", example = "Chapter 3: Light-Dependent Reactions")
    public String chapterCovered;

    @OutputField(description = "Duration of the session in minutes", example = "42")
    public int durationMinutes;

    @OutputField(description = "Number of practice questions attempted", example = "4")
    public int questionsAttempted;

    @OutputField(description = "Score across all questions in this session 0–100", example = "78")
    public int sessionScore;

    @OutputField(description = "Concepts the learner demonstrated understanding of, comma-separated",
                 example     = "photolysis, ATP production, NADPH")
    public String conceptsMastered;

    @OutputField(description = "Concepts that need follow-up in the next session, comma-separated",
                 example     = "proton gradient, cyclic photophosphorylation")
    public String revisitConcepts;

    @OutputField(description = "Key insight or breakthrough moment from this session",
                 example     = "The dam analogy unlocked the proton gradient concept")
    public String keyInsight;

    @OutputField(description = "Whether the teaching style debate selected Socratic or Direct this session",
                 example     = "DIRECT")
    public String teachingStyleUsed;

    @OutputField(description = "Overall mastery change from this session in percent", example = "+8")
    public int masteryGained;

    @OutputField(description = "Up to 5 actionable todos for the learner before next session, newline-separated",
                 example     = "Revise the Z-scheme diagram\nPractice the proton gradient explanation")
    public String generatedTodos;

    @OutputField(description = "Short note suitable for a parent or teacher portal (1–2 sentences)")
    public String parentNote;

    @OutputField(description = "Whether the learner showed distress or frustration signals this session",
                 example     = "false")
    public boolean distressSignalDetected;

    @OutputField(description = "ISO-8601 timestamp of the session")
    public String sessionDate;
}
