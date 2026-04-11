package io.squados.annotation;

import java.lang.annotation.*;

/**
 * One step in a @Pipeline.
 *
 * role      — AgentRole to invoke for this step
 * name      — Unique step name (used for inputFrom references)
 * inputFrom — Name of the prior step whose output feeds this step.
 *             Empty = use output of the immediately preceding step.
 *             Step 0 always receives the original pipeline input.
 * condition — Boolean expression evaluated against the preceding step's output.
 *             Empty = always execute.
 *             False result → AgentResponse.skipped() recorded; pipeline continues.
 * failFast  — Override pipeline-level failFast for this step only (default: inherits)
 *
 * Condition predicates: contains("x")  notContains("x")  startsWith("x")  endsWith("x")
 *                       matches("regex")  isEmpty  isNotEmpty  success  failure
 * Compound: expr1 || expr2   expr1 && expr2
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Step {
    AgentRole role();
    String    name()      default "";
    String    inputFrom() default "";
    String    condition() default "";
    boolean   failFast()  default true;
}
