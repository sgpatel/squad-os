package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Creates multiple instances of the same agent role for high-throughput load balancing.
 *
 * size         — Number of agent instances in the pool (default: 3)
 * strategy     — "ROUND_ROBIN" | "LEAST_BUSY" | "RANDOM"
 * maxQueueSize — Max pending tasks per instance before rejecting (default: 10)
 *
 * All instances share the same @Agent metadata and LlmOptions.
 * Load is distributed across instances transparently via the AgentPool scheduler.
 *
 * Usage:
 * <pre>
 *   @Agent(role = AgentRole.ANALYST, name = "AnalystPool")
 *   @AgentPool(size = 5, strategy = "LEAST_BUSY")
 *   public class HighThroughputAnalyst {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentPool {
    int    size()         default 3;
    String strategy()     default "ROUND_ROBIN";
    int    maxQueueSize() default 10;
}
