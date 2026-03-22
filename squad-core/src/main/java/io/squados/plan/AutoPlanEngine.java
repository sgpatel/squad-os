package io.squados.plan;

import io.squados.annotation.AutoPlan;
import io.squados.annotation.IterationPolicy;
import io.squados.exception.AutoPlanMaxIterationsException;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the @AutoPlan plan-execute-reflect-replan loop.
 *
 * Each iteration:
 *   1. PLAN    — asks agent: "Given the goal and current output, what is the next step?"
 *   2. EXECUTE — asks agent: "Execute that step and produce output"
 *   3. REFLECT — asks agent: "What is still missing? Have we met the goal?"
 *   4. CHECK   — if stopCondition found in output: stop
 *   5. REPLAN  — otherwise loop with updated context
 */
public class AutoPlanEngine {

    private final LlmPort llm;

    public AutoPlanEngine(LlmPort llm) {
        this.llm = llm;
    }

    /**
     * Run the AutoPlan loop for a method annotated with @AutoPlan.
     *
     * @param method        Method with @AutoPlan annotation
     * @param initialOutput The initial agent output (from first execution)
     * @param systemPrompt  Agent system prompt
     * @param originalInput Original user task input
     * @return AutoPlanResult with final output and iteration history
     */
    public AutoPlanResult run(Method method,
                              String initialOutput,
                              String systemPrompt,
                              String originalInput) {
        AutoPlan ann = method.getAnnotation(AutoPlan.class);
        if (ann == null) {
            return new AutoPlanResult(initialOutput, List.of(), false);
        }

        String  goal          = ann.goal();
        int     maxIter       = ann.maxIterations();
        String  reflectPrompt = ann.reflectOn();
        String  stopCond      = ann.stopCondition();
        boolean accumulate    = ann.accumulate();

        List<PlanIteration> iterations  = new ArrayList<>();
        String              accumulated = initialOutput;
        String              bestOutput  = initialOutput;

        System.out.printf("[AutoPlan] Starting loop — goal: %s%n", goal);
        System.out.printf("[AutoPlan] maxIterations=%d stopCondition=%s%n",
            maxIter, stopCond);

        // Check if initial output already meets goal
        if (meetsGoal(initialOutput, stopCond)) {
            System.out.println("[AutoPlan] Goal met on initial output — skipping loop");
            return new AutoPlanResult(initialOutput, List.of(), true);
        }

        for (int i = 1; i <= maxIter; i++) {
            long start = System.currentTimeMillis();
            System.out.printf("[AutoPlan] Iteration %d/%d%n", i, maxIter);

            // ── PLAN ─────────────────────────────────────────────
            String planPrompt = buildPlanPrompt(
                originalInput, goal, accumulated, i);
            String plan = callLlm(systemPrompt, planPrompt);
            System.out.printf("[AutoPlan] Plan: %s%n",
                plan.length() > 100 ? plan.substring(0, 100) + "..." : plan);

            // ── EXECUTE ──────────────────────────────────────────
            String executePrompt = buildExecutePrompt(
                originalInput, goal, accumulated, plan, i);
            String stepOutput = callLlm(systemPrompt, executePrompt);

            // ── ACCUMULATE ───────────────────────────────────────
            if (accumulate) {
                accumulated = accumulated + "\n\n--- Iteration " + i + " ---\n" + stepOutput;
            } else {
                accumulated = stepOutput;
            }

            // Track best
            if (accumulated.length() > bestOutput.length()) {
                bestOutput = accumulated;
            }

            // ── REFLECT ──────────────────────────────────────────
            String reflection = "";
            boolean goalMet   = meetsGoal(accumulated, stopCond);

            if (!goalMet) {
                String reflectFull = buildReflectPrompt(
                    goal, accumulated, reflectPrompt);
                reflection = callLlm(systemPrompt, reflectFull);
                System.out.printf("[AutoPlan] Reflection: %s%n",
                    reflection.length() > 80
                        ? reflection.substring(0, 80) + "..." : reflection);
            }

            long duration = System.currentTimeMillis() - start;
            iterations.add(new PlanIteration(
                i, plan, stepOutput, reflection, goalMet, duration));

            if (goalMet) {
                System.out.printf("[AutoPlan] Goal met on iteration %d ✓%n", i);
                return new AutoPlanResult(accumulated, iterations, true);
            }
        }

        // Max iterations reached
        System.out.printf("[AutoPlan] Max iterations (%d) reached — policy: %s%n",
            maxIter, ann.onMaxIterations());

        return switch (ann.onMaxIterations()) {
            case THROW -> throw new AutoPlanMaxIterationsException(
                method.getName(), maxIter, goal);
            case RETURN_BEST -> new AutoPlanResult(bestOutput, iterations, false);
            case RETURN_LAST -> new AutoPlanResult(accumulated, iterations, false);
        };
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean meetsGoal(String output, String stopCondition) {
        if (output == null || stopCondition == null || stopCondition.isBlank())
            return false;
        return output.contains(stopCondition);
    }

    private String buildPlanPrompt(String input, String goal,
                                    String current, int iteration) {
        return "TASK: " + input + "\n\n" +
               "GOAL: " + goal + "\n\n" +
               "CURRENT OUTPUT SO FAR:\n" + current + "\n\n" +
               "This is iteration " + iteration + ".\n" +
               "What is the single most important next step to achieve the goal?\n" +
               "Respond with a one-sentence action plan.";
    }

    private String buildExecutePrompt(String input, String goal,
                                       String current, String plan,
                                       int iteration) {
        return "TASK: " + input + "\n\n" +
               "GOAL: " + goal + "\n\n" +
               "CURRENT OUTPUT SO FAR:\n" + current + "\n\n" +
               "NEXT STEP: " + plan + "\n\n" +
               "Execute this step. Produce the output for this step only.\n" +
               "When the overall goal is fully met, include the word: COMPLETE";
    }

    private String buildReflectPrompt(String goal, String current,
                                       String reflectQuestion) {
        return "GOAL: " + goal + "\n\n" +
               "CURRENT OUTPUT:\n" + current + "\n\n" +
               reflectQuestion + "\n" +
               "Be brief — one or two sentences only.";
    }

    private String callLlm(String systemPrompt, String userMessage) {
        LlmOptions opts    = new LlmOptions(0.5f, 1024, null);
        LlmResponse resp   = llm.chat(systemPrompt, userMessage, opts);
        return resp.content() == null ? "" : resp.content().trim();
    }
}