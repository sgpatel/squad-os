package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Enables agentic plan-execute-reflect-replan loops.
 *
 * The agent iterates autonomously until a goal condition is satisfied
 * or maxIterations is reached. Each iteration:
 *   1. PLAN    — agent produces a plan for the current state
 *   2. EXECUTE — agent executes one step of the plan
 *   3. REFLECT — agent evaluates progress toward the goal
 *   4. REPLAN  — if goal not met, agent updates the plan and repeats
 *
 * Usage:
 * <pre>
 * {@literal @}AutoPlan(
 *     goal          = "A comprehensive risk report with all sections complete",
 *     maxIterations = 5,
 *     reflectOn     = "What is missing from the report so far?",
 *     stopCondition = "COMPLETE",
 *     onMaxIterations = IterationPolicy.RETURN_BEST
 * )
 * public String generateRiskReport(LoanApplication app) {
 *     return "initial analysis"; // starting point
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface AutoPlan {
    /**
     * Goal description — tells the agent what "done" looks like.
     * The reflection step checks progress against this goal.
     */
    String goal();

    /**
     * Maximum number of plan-execute-reflect cycles.
     * Prevents infinite loops.
     */
    int maxIterations() default 5;

    /**
     * Reflection prompt appended each iteration.
     * Example: "What gaps remain? What should be added next?"
     */
    String reflectOn() default "What is missing or incomplete in the current output?";

    /**
     * If the agent output contains this string, the loop stops immediately.
     * Example: "COMPLETE", "DONE", "GOAL_MET"
     */
    String stopCondition() default "COMPLETE";

    /**
     * What to do when maxIterations is reached without meeting the goal.
     */
    IterationPolicy onMaxIterations() default IterationPolicy.RETURN_BEST;

    /**
     * Whether to accumulate output across iterations (default: true).
     * true  — each iteration APPENDS to the previous output
     * false — each iteration REPLACES the previous output
     */
    boolean accumulate() default true;
}