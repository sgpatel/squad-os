package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Dynamically routes a task to a specialist agent at runtime.
 *
 * Delegation strategies:
 *   LLM_CHOICE  - LLM reads task and picks the best role
 *   ROUND_ROBIN - cycle through candidates in order
 *   LOAD_BALANCE - route to candidate with fewest active tasks
 *   FIRST_MATCH - route to first candidate whose condition matches
 *
 * Usage:
 * <pre>
 * {@literal @}Delegate(
 *     candidates = {AgentRole.ANALYST, AgentRole.RESEARCHER},
 *     strategy   = DelegateStrategy.LLM_CHOICE,
 *     fallback   = AgentRole.STRATEGIST
 * )
 * public String routeTask(String input) { return input; }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Delegate {
    AgentRole[]      candidates();
    DelegateStrategy strategy()   default DelegateStrategy.LLM_CHOICE;
    AgentRole        fallback()   default AgentRole.STRATEGIST;
    String[]         conditions() default {};
    boolean          logDecision()default true;
    long             timeoutMs()  default 30_000L;
}