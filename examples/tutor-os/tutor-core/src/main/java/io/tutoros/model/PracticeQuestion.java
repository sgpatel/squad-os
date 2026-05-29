package io.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * A single practice question produced by PracticeAgent.
 *
 * SquadOS: @StructuredOutput(schema = PracticeQuestion.class)
 *
 * The question is pitched exactly one Bloom's level above the learner's
 * current mastery to create a productive learning zone (Vygotsky's ZPD).
 *
 * For VISUAL learners the question includes a diagram description.
 * For ANALYTICAL learners it includes a data table or calculation.
 */
public class PracticeQuestion {

    @OutputField(description = "The full question text", example = "What two molecules are produced in the light reactions?")
    public String question;

    @OutputField(description = "Question type", example = "MULTIPLE_CHOICE")
    public String type; // MULTIPLE_CHOICE | SHORT_ANSWER | CALCULATION | DIAGRAM_LABEL | ESSAY

    @OutputField(description = "For MULTIPLE_CHOICE: pipe-separated options e.g. ATP|ADP|NADPH|FADH2")
    public String options;

    @OutputField(description = "The correct answer or model answer")
    public String answer;

    @OutputField(description = "Bloom's Taxonomy level this question targets", example = "APPLY")
    public String bloomsLevel;

    @OutputField(description = "Difficulty level relative to learner's current mastery", example = "MEDIUM")
    public String difficulty; // EASY | MEDIUM | HARD

    @OutputField(description = "The concept this question tests", example = "ATP synthesis via chemiosmosis")
    public String conceptTag;

    @OutputField(description = "Hints as a newline-separated list (revealed progressively)", example = "Think about the electron transport chain\nConsider what drives ATP synthase")
    public String hints;

    @OutputField(description = "Full worked solution with step-by-step explanation")
    public String workedSolution;

    @OutputField(description = "For visual learners: ASCII or description of a diagram to accompany the question")
    public String diagramDescription;

    @OutputField(description = "Marks available if this is exam-style", example = "3")
    public int marks;
}
