package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Enables few-shot learning from human feedback.
 *
 * When a human marks an agent output as GOOD or BAD via FeedbackStore,
 * the framework:
 *   1. Stores the example (input + output + label) in the feedback store
 *   2. On the next call, retrieves the top-K most similar past examples
 *   3. Injects them as few-shot examples into the agent system prompt
 *   4. Agent learns from real feedback without retraining the base model
 *
 * Over time: agents that receive feedback produce measurably better output.
 *
 * Usage:
 * <pre>
 * {@literal @}Improve(
 *     store  = FeedbackStore.IN_PROCESS,
 *     topK   = 3,
 *     label  = "loan-underwriting"
 * )
 * public LoanDecision underwriteLoan(LoanApplication app) { ... }
 *
 * // After the agent runs, mark the output:
 * improveStore.markGood(requestId, "Excellent — caught the fraud signal");
 * improveStore.markBad(requestId,  "Missed the income verification step");
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Improve {
    /** Which feedback store to use. */
    FeedbackStoreType store() default FeedbackStoreType.IN_PROCESS;

    /** How many similar past examples to inject into the prompt. */
    int topK() default 3;

    /**
     * Label for this method's feedback examples.
     * Examples from other methods are not mixed in.
     * Defaults to ClassName.methodName.
     */
    String label() default "";

    /**
     * Minimum number of feedback examples needed before injection starts.
     * Prevents cold-start noise.
     */
    int minExamples() default 1;

    /**
     * Whether to inject GOOD examples only, or also BAD examples
     * (as negative examples — "avoid outputs like this").
     */
    boolean includeNegativeExamples() default false;
}