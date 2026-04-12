package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Enables the Reflexion loop on an agent.
 *
 * After each LLM call the output is scored by an EvalJudge.
 * If the overall score is below {@code scoreThreshold} and iterations remain,
 * a textual critique is generated and the agent is called again with the
 * critique prepended — without going through the full 16-step pipeline
 * (rate-limit, circuit-breaker, guardrails, etc.) a second time.
 *
 * <pre>
 * {@literal @}Agent(role = AgentRole.ANALYST, name = "ResearchAgent",
 *          description = "Research and summarise complex topics.")
 * {@literal @}Reflexion(maxIterations = 3, scoreThreshold = 0.80f)
 * public class ResearchAgent { ... }
 * </pre>
 *
 * Token cost: up to {@code maxIterations + 1} LLM calls per request (initial + iterations).
 * For production, keep {@code maxIterations ≤ 3}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Reflexion {

    /**
     * Maximum number of critique-and-retry iterations after the initial call.
     * Total LLM calls ≤ {@code maxIterations + 1}.
     */
    int maxIterations() default 3;

    /**
     * Name of a registered agent to act as judge.
     * Empty string (default) uses EvalJudge with the shared LlmPort.
     */
    String judgeAgent() default "";

    /**
     * Minimum overall score (0.0–1.0) to accept a response without further iteration.
     */
    float scoreThreshold() default 0.80f;

    /**
     * Evaluation criteria used by the EvalJudge.
     */
    EvalCriteria[] criteria() default {
        EvalCriteria.FAITHFULNESS,
        EvalCriteria.COMPLETENESS,
        EvalCriteria.RELEVANCE
    };
}
