package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Auto-inject memories from MemoryManager into the agent's system prompt.
 *
 * topK      — How many memory records to retrieve per call (default: 3)
 * minScore  — Minimum cosine similarity threshold (default: 0.72)
 * scope     — "agent" (per-agent) | "squad" (shared across all agents)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentMemory {
    int    topK()     default 3;
    float  minScore() default 0.72f;
    String scope()    default "agent";
}
