package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Evaluates an agent output for quality before returning it to the caller.
 *
 * The framework:
 *   1. Captures the agent output
 *   2. Passes it to a judge agent (the CriticAgent or a dedicated EvalAgent)
 *   3. Judge scores across configured criteria (faithfulness, completeness, etc.)
 *   4. If score >= minScore: return the output
 *   5. If score < minScore and retryOnFail=true: retry up to maxRetries times
 *   6. If still below threshold: throw EvalFailedException or return best attempt
 *
 * Usage:
 * <pre>
 * {@literal @}Eval(
 *     judge       = AgentRole.CRITIC,
 *     minScore    = 0.8f,
 *     criteria    = {EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS},
 *     retryOnFail = true,
 *     maxRetries  = 3
 * )
 * public String analyseLoan(LoanApplication app) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Eval {
    /** Role of the judge agent that evaluates the output. */
    AgentRole judge() default AgentRole.CRITIC;

    /** Minimum acceptable score (0.0 to 1.0). */
    float minScore() default 0.7f;

    /** Criteria to evaluate. Defaults to all criteria. */
    EvalCriteria[] criteria() default {
        EvalCriteria.FAITHFULNESS,
        EvalCriteria.COMPLETENESS,
        EvalCriteria.RELEVANCE
    };

    /** Retry the agent if score is below minScore. */
    boolean retryOnFail() default true;

    /** Maximum retry attempts before giving up. */
    int maxRetries() default 3;

    /**
     * What to do when all retries are exhausted and score still too low.
     * THROW: throw EvalFailedException.
     * RETURN_BEST: return the highest-scoring attempt.
     */
    EvalFailPolicy onFail() default EvalFailPolicy.RETURN_BEST;
}