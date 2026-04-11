package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Guard condition evaluated before executing this agent.
 * Used independently (not inside @Pipeline) to conditionally skip execution.
 *
 * expression — Condition expression using ConditionEvaluator predicates.
 *              Evaluated against the task input string.
 *              If false → AgentResponse.skipped() returned without LLM call.
 *
 * Predicates: contains("x")  notContains("x")  startsWith("x")  endsWith("x")
 *             matches("regex")  isEmpty  isNotEmpty
 * Compound:   expr1 || expr2   expr1 && expr2
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Condition {
    String expression();
}
