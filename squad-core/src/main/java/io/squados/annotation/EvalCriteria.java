package io.squados.annotation;

/**
 * Scoring dimensions used by the @Eval judge agent.
 *
 * Each criterion is scored 0.0 to 1.0.
 * The final score is the average across all selected criteria.
 */
public enum EvalCriteria {
    /**
     * Does the output accurately reflect the input/context?
     * Catches hallucinations and made-up facts.
     */
    FAITHFULNESS,

    /**
     * Does the output cover all required aspects of the task?
     * Catches incomplete or partial responses.
     */
    COMPLETENESS,

    /**
     * Is the output relevant to the question asked?
     * Catches off-topic or tangential responses.
     */
    RELEVANCE,

    /**
     * Is the output clear and well-structured?
     * Evaluates readability and logical flow.
     */
    CLARITY,

    /**
     * Is the output factually correct?
     * For domains with verifiable ground truth.
     */
    CORRECTNESS,

    /**
     * Is the output free of harmful or toxic content?
     * Safety check for customer-facing responses.
     */
    SAFETY
}