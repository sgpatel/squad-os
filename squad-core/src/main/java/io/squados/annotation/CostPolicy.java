package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Declares a per-agent cost budget with automatic model degradation.
 *
 * {@link io.squados.cost.CostAwareLlmPort} wraps the agent's {@code LlmPort}
 * and transparently switches from {@code primaryModel} to {@code fallbackModel}
 * once {@code degradeAt} fraction of {@code budgetCentsPerHour} has been spent
 * within the current sliding window.
 *
 * <pre>
 * {@literal @}Agent(role = AgentRole.ANALYST, name = "DataAgent")
 * {@literal @}CostPolicy(
 *     primaryModel      = "gpt-4o",
 *     fallbackModel     = "gpt-4o-mini",
 *     budgetCentsPerHour = 5.0,
 *     degradeAt          = 0.80
 * )
 * public class DataAgent { ... }
 * </pre>
 *
 * A {@code budgetCentsPerHour} of 0.0 means unlimited — the agent always uses
 * {@code primaryModel} and degradation never triggers.
 *
 * Model pricing is sourced from {@code model-pricing.properties} on the
 * classpath, with hard-coded defaults as a fallback.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface CostPolicy {

    /**
     * Primary (preferred, higher-quality) model identifier.
     * Empty string means inherit from {@code squad.yml}.
     */
    String primaryModel() default "";

    /**
     * Fallback (cheaper) model identifier used when budget threshold is hit.
     * Empty string means stay on the primary model (degradation disabled).
     */
    String fallbackModel() default "";

    /**
     * Total token-cost budget in USD cents per sliding hour window.
     * 0.0 = unlimited.
     */
    double budgetCentsPerHour() default 10.0;

    /**
     * Fraction of {@code budgetCentsPerHour} consumed before switching to
     * {@code fallbackModel}.  Must be in (0.0, 1.0].  Default 0.80 = 80 %.
     */
    double degradeAt() default 0.80;
}
