package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Declares a sequential multi-agent pipeline.
 *
 * Mark the orchestrating @Agent with @Pipeline and define steps via @Step.
 * Executed via ctx.submitPipeline(AgentRole, String input).
 *
 * name     — Human-readable pipeline name
 * steps    — Ordered array of @Step definitions
 * failFast — Stop immediately on first failure (default: true)
 *
 * <pre>
 *   @Agent(role = AgentRole.STRATEGIST, name = "MentionPipeline")
 *   @Pipeline(name = "mention-analysis", failFast = true, steps = {
 *       @Step(role = AgentRole.ANALYST,  name = "sentiment"),
 *       @Step(role = AgentRole.CRITIC,   name = "escalation"),
 *       @Step(role = AgentRole.SUPPORT,  name = "ticket",
 *             inputFrom = "escalation",
 *             condition = "contains(\"P1\") || contains(\"P2\")")
 *   })
 *   public class MentionPipelineAgent {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Pipeline {
    String name()     default "";
    Step[] steps()    default {};
    boolean failFast() default true;
}
