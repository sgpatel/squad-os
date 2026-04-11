package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Marks an agent as durable — workflow state is persisted after every step.
 *
 * store     — "memory" | "redis"
 * ttlHours  — How long to retain completed workflows (default: 24h)
 *
 * A durable agent resumes from the last completed step if the JVM restarts.
 * Completed workflows return cached results immediately (idempotent).
 *
 * Usage via SquadContext:
 * <pre>
 *   ctx.submitDurable(AgentRole.ANALYST, "claim-2024-9821", input)
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DurableAgent {
    String store()    default "memory";
    int    ttlHours() default 24;
}
