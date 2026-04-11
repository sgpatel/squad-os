package io.squados.pipeline;

import io.squados.agent.AgentResponse;

import java.util.*;

/**
 * Result of a @Pipeline execution — one AgentResponse per @Step.
 */
public class PipelineResult {

    private final Map<String, AgentResponse> stepResults;    // stepName → response
    private final List<String>               stepOrder;      // ordered step names
    private final boolean                    succeeded;
    private final String                     failedStep;     // null if succeeded
    private final long                       wallClockMs;

    private PipelineResult(Map<String, AgentResponse> results,
                            List<String> order,
                            boolean succeeded,
                            String failedStep,
                            long wallClockMs) {
        this.stepResults  = Map.copyOf(results);
        this.stepOrder    = List.copyOf(order);
        this.succeeded    = succeeded;
        this.failedStep   = failedStep;
        this.wallClockMs  = wallClockMs;
    }

    // ── Factory methods ───────────────────────────────────────────────

    public static PipelineResult success(Map<String, AgentResponse> results,
                                          List<String> order, long wallClockMs) {
        return new PipelineResult(results, order, true, null, wallClockMs);
    }

    public static PipelineResult failure(Map<String, AgentResponse> results,
                                          List<String> order, String failedStep,
                                          long wallClockMs) {
        return new PipelineResult(results, order, false, failedStep, wallClockMs);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** Get response for a specific step by name. */
    public AgentResponse get(String stepName) {
        return stepResults.get(stepName);
    }

    /** Output of the last executed step. */
    public String finalOutput() {
        if (stepOrder.isEmpty()) return null;
        for (int i = stepOrder.size() - 1; i >= 0; i--) {
            AgentResponse r = stepResults.get(stepOrder.get(i));
            if (r != null && !r.isSkipped() && r.isSuccess()) {
                return r.content();
            }
        }
        return null;
    }

    /** Names of steps that were skipped due to condition evaluating false. */
    public List<String> skippedSteps() {
        return stepOrder.stream()
            .filter(name -> {
                AgentResponse r = stepResults.get(name);
                return r != null && r.isSkipped();
            })
            .toList();
    }

    /** Total tokens consumed across all steps. */
    public int totalTokens() {
        return stepResults.values().stream()
            .mapToInt(AgentResponse::totalTokens)
            .sum();
    }

    public boolean succeeded()    { return succeeded; }
    public String  failedStep()   { return failedStep; }
    public long    wallClockMs()  { return wallClockMs; }
    public Map<String, AgentResponse> allResults() { return stepResults; }
}
