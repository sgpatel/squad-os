package io.squados.topology;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.*;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;

import java.time.Instant;
import java.util.*;

/**
 * Executes an agent graph defined by {@link Topology} and {@link AgentEdge} annotations.
 *
 * Validation at boot:
 *   - Acyclic layouts (PIPELINE, STAR, HIERARCHY) reject cyclic graphs.
 *   - All named agents must be registered in {@link AgentRegistry}.
 *   - No agent may carry both {@code @Pipeline} and {@code @Topology}.
 *
 * Execution strategy:
 *   - PIPELINE / HIERARCHY: topological order, each step receives output of its predecessor.
 *   - STAR: hub runs first, leaves run in parallel (fan-out), results aggregated.
 *   - MESH / RING: BFS rounds bounded by {@code Topology.maxRounds()}.
 */
public class TopologyEngine {

    private final AgentRegistry registry;
    private       TopologyGraph graph;
    private       Topology      topologyAnn;

    public TopologyEngine(AgentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Parse the topology annotation and build the internal graph.
     * Call once per annotated orchestrator class.
     */
    public void load(Topology ann) {
        this.topologyAnn = ann;
        this.graph = TopologyGraph.from(ann.edges());
    }

    /**
     * Validate the topology at boot time.
     *
     * @throws IllegalStateException if the graph is invalid.
     */
    public void validate() {
        if (graph == null) {
            throw new IllegalStateException("[TopologyEngine] No topology loaded. Call load() first.");
        }

        TopologyLayout layout = topologyAnn.layout();
        String name = topologyAnn.name().isBlank() ? "unnamed" : topologyAnn.name();

        // All referenced agent names must be registered
        for (String agentName : graph.nodes()) {
            if (registry.getByName(agentName) == null) {
                throw new IllegalStateException(
                    "[TopologyEngine] Topology '" + name + "' references unknown agent: '"
                    + agentName + "'. Ensure it is declared with @Agent(name=\"" + agentName + "\").");
            }
        }

        // Acyclic layouts must not have cycles
        boolean acyclic = layout == TopologyLayout.PIPELINE
                       || layout == TopologyLayout.STAR
                       || layout == TopologyLayout.HIERARCHY;
        if (acyclic && graph.hasCycle()) {
            throw new IllegalStateException(
                "[TopologyEngine] Topology '" + name + "' with layout=" + layout
                + " contains a cycle. Use TopologyLayout.MESH or RING for cyclic graphs.");
        }

        System.out.printf("[TopologyEngine] Validated '%s' — %d nodes, %d edges, layout=%s%n",
            name, graph.nodeCount(), graph.edgeCount(), layout);
    }

    /**
     * Execute the topology for a given input.
     *
     * @param input     Task/input for the first agent(s) in the graph.
     * @param sessionId Session identifier propagated to all {@link TaskContext}s.
     * @return          {@link TopologyResult} with per-step outputs and metadata.
     */
    public TopologyResult execute(String input, String sessionId) {
        Instant start    = Instant.now();
        Map<String, AgentResponse> outputs = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();

        try {
            TopologyLayout layout = topologyAnn.layout();
            switch (layout) {
                case PIPELINE, HIERARCHY -> executeDag(input, sessionId, outputs, order);
                case STAR                -> executeStar(input, sessionId, outputs, order);
                case MESH, RING          -> executeMesh(input, sessionId, outputs, order);
            }
        } catch (Exception e) {
            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
            return TopologyResult.failure(outputs, order, elapsed, e.getMessage());
        }

        long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
        boolean allOk = outputs.values().stream().allMatch(AgentResponse::isSuccess);
        return allOk
            ? TopologyResult.success(outputs, order, elapsed)
            : TopologyResult.failure(outputs, order, elapsed, "One or more steps failed");
    }

    // ── Layout strategies ─────────────────────────────────────────────

    private void executeDag(String input, String sessionId,
                            Map<String, AgentResponse> outputs, List<String> order) {
        List<String> sorted = graph.topologicalSort();
        String currentInput = input;

        for (String agentName : sorted) {
            AgentWrapper wrapper = registry.getByName(agentName);
            if (wrapper == null) continue;

            // Determine the effective input based on incoming DELEGATES edges
            String effectiveInput = resolveInput(agentName, currentInput, outputs);

            TaskContext ctx = new TaskContext(effectiveInput, sessionId, "topology");
            AgentResponse response = wrapper.execute(ctx);
            outputs.put(agentName, response);
            order.add(agentName);

            if (response.isSuccess() && response.content() != null) {
                currentInput = response.content();
            }
        }
    }

    private void executeStar(String input, String sessionId,
                             Map<String, AgentResponse> outputs, List<String> order) {
        List<String> sources = graph.sources();
        if (sources.isEmpty()) {
            executeDag(input, sessionId, outputs, order);
            return;
        }
        // Hub = first source
        String hub = sources.get(0);
        AgentWrapper hubWrapper = registry.getByName(hub);
        if (hubWrapper != null) {
            TaskContext ctx = new TaskContext(input, sessionId, "topology");
            AgentResponse hubResp = hubWrapper.execute(ctx);
            outputs.put(hub, hubResp);
            order.add(hub);

            // Leaves = all successors of hub
            String hubOutput = hubResp.isSuccess() ? hubResp.content() : input;
            for (TopologyEdge edge : graph.getSuccessors(hub)) {
                AgentWrapper leaf = registry.getByName(edge.to());
                if (leaf == null) continue;
                String leafInput = resolveEdgeInput(edge.type(), hubOutput, input);
                TaskContext leafCtx = new TaskContext(leafInput, sessionId, "topology");
                AgentResponse leafResp = leaf.execute(leafCtx);
                outputs.put(edge.to(), leafResp);
                order.add(edge.to());
            }
        }
    }

    private void executeMesh(String input, String sessionId,
                             Map<String, AgentResponse> outputs, List<String> order) {
        int maxRounds = Math.max(1, topologyAnn.maxRounds());
        List<String> bfsOrder = graph.bfsOrder(graph.sources().isEmpty()
            ? graph.nodes().iterator().next()
            : graph.sources().get(0));

        String currentInput = input;
        for (int round = 0; round < maxRounds; round++) {
            for (String agentName : bfsOrder) {
                AgentWrapper wrapper = registry.getByName(agentName);
                if (wrapper == null) continue;
                String effectiveInput = resolveInput(agentName, currentInput, outputs);
                TaskContext ctx = new TaskContext(effectiveInput, sessionId, "topology-r" + round);
                AgentResponse resp = wrapper.execute(ctx);
                outputs.put(agentName, resp);
                if (!order.contains(agentName)) order.add(agentName);
                if (resp.isSuccess() && resp.content() != null) {
                    currentInput = resp.content();
                }
            }
        }
    }

    // ── Edge semantics ────────────────────────────────────────────────

    private String resolveInput(String agentName, String defaultInput,
                                Map<String, AgentResponse> outputs) {
        for (TopologyEdge edge : graph.allEdges()) {
            if (edge.to().equals(agentName)) {
                AgentResponse fromResp = outputs.get(edge.from());
                if (fromResp != null && fromResp.isSuccess()) {
                    return resolveEdgeInput(edge.type(),
                        fromResp.content() != null ? fromResp.content() : defaultInput,
                        defaultInput);
                }
            }
        }
        return defaultInput;
    }

    private String resolveEdgeInput(EdgeType type, String predecessorOutput, String originalInput) {
        return switch (type) {
            case DELEGATES -> predecessorOutput;
            case INFORMS   -> predecessorOutput + "\n\n[Context from previous agent]\n" + originalInput;
            case APPROVES  -> "APPROVAL REQUESTED:\n" + predecessorOutput;
            case NOTIFIES  -> originalInput;   // fire-and-forget: target gets original input
            case COMPETES  -> originalInput;   // parallel: same input
        };
    }
}
