package io.squados.examples.tutoros.model;

import io.squados.annotation.OutputField;

/**
 * Per-answer feedback produced by AssessmentAgent.
 *
 * SquadOS: @StructuredOutput(schema = AssessmentFeedback.class)
 *
 * Feedback is never "wrong" without explanation — every incorrect answer
 * receives: what was right, what was wrong, a targeted hint, and
 * encouragement. The agent never just marks and moves on.
 *
 * The masteryDelta drives ProgressAgent's memory update:
 *   - Correct first attempt: +0.10 mastery for the concept
 *   - Correct with hints:    +0.05
 *   - Incorrect:             -0.05 (floors at 0)
 */
public class AssessmentFeedback {

    @OutputField(description = "Score for this answer 0–100", example = "75")
    public int score;

    @OutputField(description = "Whether the answer is fully correct", example = "false")
    public boolean correct;

    @OutputField(description = "What part of the answer was correct", example = "ATP correctly identified as an energy carrier")
    public String correctParts;

    @OutputField(description = "What part of the answer was incorrect or missing", example = "NADPH was not mentioned")
    public String incorrectParts;

    @OutputField(description = "A targeted hint to guide the learner to the missing concept", example = "Think about what carries electrons to the Calvin Cycle")
    public String hint;

    @OutputField(description = "Encouraging message — positive regardless of score", example = "Great start! You have the energy part nailed.")
    public String encouragement;

    @OutputField(description = "The model answer the learner can compare against")
    public String modelAnswer;

    @OutputField(description = "Bloom's Taxonomy level demonstrated in this answer", example = "UNDERSTAND")
    public String bloomsDemonstrated;

    @OutputField(description = "Change in concept mastery from this answer (-0.10 to +0.10)", example = "0.10")
    public double masteryDelta;

    @OutputField(description = "Whether the learner should attempt this question again", example = "true")
    public boolean suggestRetry;

    @OutputField(description = "Next recommended concept based on this answer", example = "proton gradient")
    public String nextConcept;
}
