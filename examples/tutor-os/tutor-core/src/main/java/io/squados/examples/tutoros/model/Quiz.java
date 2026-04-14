package io.squados.examples.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * A complete quiz produced on-demand by QuizAgent.
 *
 * SquadOS: @StructuredOutput(schema = Quiz.class)
 *
 * QuizAgent calibrates difficulty to the learner's current Bloom's level
 * and distributes questions across gap concepts proportionally.
 *
 * Supports 4 question formats:
 *   MULTIPLE_CHOICE — 4 options, one correct
 *   SHORT_ANSWER    — 1–3 sentence expected response
 *   CALCULATION     — numeric or algebraic answer with working
 *   DIAGRAM_LABEL   — textual description of a diagram labelling task
 */
public class Quiz {

    @OutputField(description = "Unique quiz ID, e.g. QUIZ-2024-Biology-Priya-007")
    public String quizId;

    @OutputField(description = "Subject the quiz covers", example = "Biology")
    public String subject;

    @OutputField(description = "Chapter or topic the quiz covers", example = "Light-Dependent Reactions")
    public String topic;

    @OutputField(description = "Overall difficulty calibrated to learner level", example = "MEDIUM")
    public String difficulty; // EASY | MEDIUM | HARD | MIXED

    @OutputField(description = "Number of questions in this quiz", example = "5")
    public int questionCount;

    @OutputField(description = "Time allowed in minutes", example = "10")
    public int timeLimitMinutes;

    @OutputField(description = "JSON array of PracticeQuestion objects serialised as a string")
    public String questionsJson;

    @OutputField(description = "Total marks available across all questions", example = "20")
    public int totalMarks;

    @OutputField(description = "Bloom's levels covered, comma-separated", example = "REMEMBER, UNDERSTAND, APPLY")
    public String bloomsLevelsCovered;

    @OutputField(description = "Gap concepts this quiz specifically targets", example = "proton gradient, cyclic photophosphorylation")
    public String targetGaps;
}
