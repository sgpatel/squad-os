package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Activates DSPy-style automated prompt optimization for an agent.
 *
 * The optimizer:
 *   1. Runs the agent on a set of training examples
 *   2. Identifies failures (score below {@code scoreThreshold})
 *   3. Asks an LLM to analyse the failure patterns and suggest prompt improvements
 *   4. Rewrites the agent's system prompt incorporating the suggestions
 *   5. Re-evaluates on the same examples
 *   6. Accepts the new prompt only if it improves the score
 *   7. Repeats up to {@code maxIterations} times
 *
 * The optimized prompt is stored in the {@link io.squados.optimize.PromptVersionStore}
 * and can be loaded into the agent on the next boot.
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.ANALYST, name = "SentimentAnalyser")
 * {@literal @}OptimizePrompt(
 *     scoreThreshold = 0.85f,
 *     maxIterations  = 5,
 *     criteria       = {EvalCriteria.CORRECTNESS, EvalCriteria.FAITHFULNESS}
 * )
 * public class SentimentAnalyserAgent {}
 *
 * // Run optimization:
 * PromptOptimizerEngine optimizer = new PromptOptimizerEngine(ctx, judgePort);
 * PromptOptimizationResult result = optimizer.optimize(
 *     SentimentAnalyserAgent.class, trainingExamples);
 * System.out.println("Best prompt: " + result.bestPrompt());
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface OptimizePrompt {

    /** Target score — optimization stops early when this is exceeded. */
    float scoreThreshold() default 0.85f;

    /** Maximum optimization iterations. */
    int maxIterations() default 5;

    /** Criteria to optimize against. */
    EvalCriteria[] criteria() default {
        EvalCriteria.FAITHFULNESS,
        EvalCriteria.CORRECTNESS,
        EvalCriteria.RELEVANCE
    };

    /**
     * Accept a new prompt only if it improves the score by at least this delta.
     * Prevents accepting marginal or noise-driven improvements.
     */
    float minImprovement() default 0.02f;
}
