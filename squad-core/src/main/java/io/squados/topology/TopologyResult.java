package io.squados.topology;

import io.squados.agent.AgentResponse;

import java.util.*;

/**
 * The outcome of executing an agent graph topology.
 *
 * Contains per-step outputs indexed by agent name, the execution order,
 * whether all steps succeeded, and total elapsed time.
 */
public class TopologyResult {

    private final Map<String, AgentResponse> stepOutputs;
    private final List<String>               executionOrder;
    private final boolean                    success;
    private final long                       elapsedMs;
    private final String                     errorMessage;

    private TopologyResult(Map<String, AgentResponse> stepOutputs,
                           List<String> executionOrder,
                           boolean success,
                           long elapsedMs,
                           String errorMessage) {
        this.stepOutputs    = Collections.unmodifiableMap(new LinkedHashMap<>(stepOutputs));
        this.executionOrder = Collections.unmodifiableList(new ArrayList<>(executionOrder));
        this.success        = success;
        this.elapsedMs      = elapsedMs;
        this.errorMessage   = errorMessage;
    }

    public static TopologyResult success(Map<String, AgentResponse> outputs,
                                         List<String> order,
                                         long elapsedMs) {
        return new TopologyResult(outputs, order, true, elapsedMs, null);
    }

    public static TopologyResult failure(Map<String, AgentResponse> outputs,
                                          List<String> order,
                                          long elapsedMs,
                                          String errorMessage) {
        return new TopologyResult(outputs, order, false, elapsedMs, errorMessage);
    }

    /** Per-agent outputs indexed by agent name. */
    public Map<String, AgentResponse> stepOutputs()    { return stepOutputs; }

    /** Agents executed, in execution order. */
    public List<String> executionOrder()               { return executionOrder; }

    /** True when all agent steps succeeded. */
    public boolean isSuccess()                         { return success; }

    /** Total wall-clock execution time in milliseconds. */
    public long elapsedMs()                            { return elapsedMs; }

    /** Error message if any step failed; null on success. */
    public String errorMessage()                       { return errorMessage; }

    /**
     * Output of the final agent in the execution order.
     * Null if no agents were executed.
     */
    public AgentResponse finalOutput() {
        if (executionOrder.isEmpty()) return null;
        return stepOutputs.get(executionOrder.get(executionOrder.size() - 1));
    }

    @Override
    public String toString() {
        return String.format(
            "TopologyResult{steps=%d, success=%b, elapsedMs=%d}",
            stepOutputs.size(), success, elapsedMs);
    }
}
