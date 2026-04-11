package io.squados.pipeline;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.Pipeline;
import io.squados.annotation.Step;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;

import java.util.*;

/**
 * Sequential step executor for @Pipeline agents.
 *
 * Step ordering rules:
 *   Step 0 always receives the original input.
 *   Step N (no inputFrom) receives output of step N-1.
 *   Step N (inputFrom="name") receives output of named prior step (fallback: original input).
 *
 * Condition false → AgentResponse.skipped() recorded; pipeline continues.
 * failFast=true + step failure → PipelineResult.failure() returned immediately.
 */
public class PipelineEngine {

    private final AgentRegistry registry;

    public PipelineEngine(AgentRegistry registry) {
        this.registry = registry;
    }

    public PipelineResult execute(Pipeline pipeline, String originalInput, String sessionId) {
        long start = System.currentTimeMillis();
        Step[] steps = pipeline.steps();

        Map<String, AgentResponse> results = new LinkedHashMap<>();
        List<String>               order   = new ArrayList<>();

        AgentResponse lastResponse = null;

        for (int i = 0; i < steps.length; i++) {
            Step step = steps[i];
            String stepName = resolveStepName(step, i);
            order.add(stepName);

            // Resolve input for this step
            String input = resolveInput(step, i, originalInput, results);

            // Evaluate condition
            boolean conditionMet = evaluateCondition(step, input, lastResponse);
            if (!conditionMet) {
                AgentResponse skipped = AgentResponse.skipped(step.role(), stepName);
                results.put(stepName, skipped);
                lastResponse = skipped;
                continue;
            }

            // Find and execute agent
            AgentWrapper wrapper = registry.getByRole(step.role());
            if (wrapper == null) {
                AgentResponse failure = AgentResponse.failure(
                    step.role(), stepName,
                    "No agent registered for role: " + step.role(), java.time.Instant.now());
                results.put(stepName, failure);
                if (pipeline.failFast()) {
                    return PipelineResult.failure(results, order, stepName,
                        System.currentTimeMillis() - start);
                }
                lastResponse = failure;
                continue;
            }

            TaskContext ctx = new TaskContext(input, sessionId, null);
            AgentResponse response = wrapper.execute(ctx);
            results.put(stepName, response);
            lastResponse = response;

            if (!response.isSuccess()) {
                boolean failFast = step.failFast() && pipeline.failFast();
                if (failFast) {
                    return PipelineResult.failure(results, order, stepName,
                        System.currentTimeMillis() - start);
                }
            }
        }

        return PipelineResult.success(results, order, System.currentTimeMillis() - start);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String resolveStepName(Step step, int index) {
        return (step.name() == null || step.name().isBlank())
            ? step.role().name().toLowerCase() + "_" + index
            : step.name();
    }

    private String resolveInput(Step step, int stepIndex, String original,
                                 Map<String, AgentResponse> results) {
        if (stepIndex == 0) return original;

        // inputFrom="name" → use that step's output
        if (step.inputFrom() != null && !step.inputFrom().isBlank()) {
            AgentResponse prior = results.get(step.inputFrom());
            if (prior != null && prior.isSuccess() && prior.content() != null) {
                return prior.content();
            }
            return original; // fallback
        }

        // Default: use previous step's output
        List<String> keys = new ArrayList<>(results.keySet());
        if (!keys.isEmpty()) {
            AgentResponse prev = results.get(keys.get(keys.size() - 1));
            if (prev != null && prev.isSuccess() && prev.content() != null) {
                return prev.content();
            }
        }
        return original;
    }

    private boolean evaluateCondition(Step step, String input, AgentResponse lastResponse) {
        String condition = step.condition();
        if (condition == null || condition.isBlank()) return true;

        boolean lastSuccess = lastResponse == null || lastResponse.isSuccess();
        String  text        = lastResponse != null && lastResponse.content() != null
            ? lastResponse.content() : input;

        return ConditionEvaluator.evaluate(condition, text, lastSuccess);
    }
}
