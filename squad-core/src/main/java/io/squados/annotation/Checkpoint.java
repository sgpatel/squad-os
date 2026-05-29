package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Mid-method state saving within a single @DurableAgent step.
 *
 * When annotated on a method within a @DurableAgent class, the method's
 * return value is checkpointed to the DurableStore after each call.
 * If the JVM restarts, the method is skipped and the checkpointed value is returned.
 *
 * name — Checkpoint identifier (unique within the workflow)
 * ttlHours — How long to retain the checkpoint (default: inherits from @DurableAgent)
 *
 * Usage:
 * <pre>
 *   @Agent(role = AgentRole.EXECUTOR)
 *   @DurableAgent
 *   public class ClaimProcessorAgent {
 *
 *       @Checkpoint(name = "validation")
 *       public String validateClaim(String input) { ... }
 *
 *       @Checkpoint(name = "enrichment")
 *       public String enrichClaim(String validated) { ... }
 *   }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Checkpoint {
    String name();
    int    ttlHours() default -1;  // -1 = inherit from @DurableAgent
}
