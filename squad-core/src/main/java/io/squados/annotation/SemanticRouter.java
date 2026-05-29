package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Enables embedding-based task routing on the annotated orchestrator agent.
 *
 * At boot time {@link io.squados.router.SemanticRouterEngine} embeds the
 * {@code description} field of every registered agent.  On each
 * {@code SquadContext.submitSemantic(task)} call the task itself is embedded
 * and cosine-similarity is computed against all agent embeddings.  The
 * highest-similarity agent above {@code minConfidence} handles the task.
 *
 * Requires an {@link io.squados.memory.retrieval.EmbeddingPort} to be wired
 * into {@code SquadContext} before boot.
 *
 * <pre>
 * {@literal @}Agent(role = AgentRole.STRATEGIST, name = "Router")
 * {@literal @}SemanticRouter(fallback = "GeneralistAgent", minConfidence = 0.65f)
 * public class RouterAgent { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface SemanticRouter {

    /**
     * Name of the fallback agent (as in {@code @Agent(name = ...)}) to use
     * when no registered agent exceeds {@code minConfidence}.
     * Empty string = route to the squad's lead agent.
     */
    String fallback() default "";

    /**
     * Minimum cosine similarity score (0.0–1.0) required to route to a specialist.
     * Tasks below this threshold fall through to the fallback agent.
     */
    float minConfidence() default 0.60f;

    /**
     * Log routing decisions (agent chosen, confidence score) to stdout.
     */
    boolean logRouting() default true;
}
