package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Hard deadline for a single agent LLM call.
 *
 * If the LLM call does not complete within timeoutMs, the future is cancelled
 * and AgentTimeoutException is thrown (which the circuit breaker counts as failure).
 *
 * action — "fail" (throw AgentTimeoutException) | "fallback" (return fallbackResponse)
 *
 * Usage:
 * <pre>
 *   @Agent(role = AgentRole.ANALYST, name = "FastAnalyst")
 *   @Timeout(timeoutMs = 5000, action = "fail")
 *   public class FastAnalystAgent {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Timeout {
    long   timeoutMs()       default 30000L;
    String action()          default "fail";   // "fail" | "fallback"
    String fallbackResponse() default "";
}
