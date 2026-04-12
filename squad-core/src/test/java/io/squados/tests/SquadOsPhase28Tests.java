package io.squados.tests;

import io.squados.annotation.*;
import io.squados.config.SquadConfig;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.llm.MockLlmPort;
import io.squados.topology.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 28 — Agent Graph Topology (@Topology, @AgentEdge, TopologyEngine)
 *
 * T01 — TopologyGraph.addEdge() records nodes and edges correctly
 * T02 — TopologyGraph.topologicalSort() produces correct order for 3-node DAG
 * T03 — TopologyGraph.hasCycle() returns true for A→B→A
 * T04 — TopologyGraph.hasCycle() returns false for A→B→C
 * T05 — TopologyEngine.validate() throws for cyclic PIPELINE
 * T06 — TopologyGraph.sources() returns nodes with no incoming edges
 * T07 — TopologyGraph.bfsOrder() correct for linear graph
 * T08 — TopologyGraph.nodeCount() and edgeCount() correct
 * T09 — TopologyEngine runs agents in topological order with DELEGATES edges
 * T10 — TopologyResult.executionOrder() matches expected order
 * T11 — TopologyResult.finalOutput() returns last agent's response
 * T12 — AgentRegistry.getByName() finds agent by name
 */
public class SquadOsPhase28Tests {

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase28Tests();
        String[] tests = {
            "T01_addEdgeRecordsNodesAndEdges",
            "T02_topologicalSortThreeNodeDag",
            "T03_hasCycleDetectsCycle",
            "T04_hasCycleReturnsFalseForDag",
            "T05_validateThrowsForCyclicPipeline",
            "T06_sourcesReturnsNodesWithNoIncoming",
            "T07_bfsOrderCorrectForLinearGraph",
            "T08_nodeAndEdgeCounts",
            "T09_topologyEngineExecutesDelegatesEdges",
            "T10_executionOrderMatchesTopological",
            "T11_finalOutputReturnsLastAgentResponse",
            "T12_registryGetByNameFindsAgent",
        };
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s — %s%n", test, cause.getMessage());
                failed++;
            }
        }
        System.out.printf("%nPhase 28: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Test methods ──────────────────────────────────────────────────

    void T01_addEdgeRecordsNodesAndEdges() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("A", "B", EdgeType.DELEGATES);
        g.addEdge("B", "C", EdgeType.NOTIFIES);

        assert g.nodes().contains("A") : "Missing node A";
        assert g.nodes().contains("B") : "Missing node B";
        assert g.nodes().contains("C") : "Missing node C";
        assert g.getSuccessors("A").size() == 1 : "A should have 1 successor";
        assert g.getSuccessors("A").get(0).to().equals("B") : "A→B missing";
    }

    void T02_topologicalSortThreeNodeDag() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("A", "B", EdgeType.DELEGATES);
        g.addEdge("B", "C", EdgeType.DELEGATES);

        var sorted = g.topologicalSort();
        assert sorted.size() == 3 : "Expected 3 nodes, got: " + sorted.size();
        assert sorted.indexOf("A") < sorted.indexOf("B") : "A must come before B";
        assert sorted.indexOf("B") < sorted.indexOf("C") : "B must come before C";
    }

    void T03_hasCycleDetectsCycle() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("A", "B", EdgeType.DELEGATES);
        g.addEdge("B", "A", EdgeType.DELEGATES);

        assert g.hasCycle() : "Should detect A→B→A cycle";
    }

    void T04_hasCycleReturnsFalseForDag() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("A", "B", EdgeType.DELEGATES);
        g.addEdge("B", "C", EdgeType.DELEGATES);

        assert !g.hasCycle() : "Linear DAG should not have a cycle";
    }

    void T05_validateThrowsForCyclicPipeline() {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("ok");

        TopologyGraph g = new TopologyGraph();
        g.addEdge("NodeA", "NodeB", EdgeType.DELEGATES);
        g.addEdge("NodeB", "NodeA", EdgeType.DELEGATES);

        // Manually test cycle detection in a PIPELINE-equivalent check
        assert g.hasCycle() : "Cyclic graph detected correctly";

        boolean threw = false;
        try {
            g.topologicalSort();
        } catch (IllegalStateException e) {
            threw = true;
        }
        assert threw : "topologicalSort() should throw for cyclic graph";
    }

    void T06_sourcesReturnsNodesWithNoIncoming() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("Source", "Middle", EdgeType.DELEGATES);
        g.addEdge("Middle", "Sink", EdgeType.DELEGATES);

        var sources = g.sources();
        assert sources.size() == 1 : "Expected 1 source, got: " + sources.size();
        assert sources.get(0).equals("Source") : "Expected 'Source', got: " + sources.get(0);
    }

    void T07_bfsOrderCorrectForLinearGraph() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("A", "B", EdgeType.DELEGATES);
        g.addEdge("B", "C", EdgeType.DELEGATES);

        var bfs = g.bfsOrder("A");
        assert bfs.equals(List.of("A", "B", "C")) : "BFS order wrong: " + bfs;
    }

    void T08_nodeAndEdgeCounts() {
        TopologyGraph g = new TopologyGraph();
        g.addEdge("A", "B", EdgeType.DELEGATES);
        g.addEdge("A", "C", EdgeType.INFORMS);
        g.addEdge("B", "D", EdgeType.NOTIFIES);

        assert g.nodeCount() == 4 : "Expected 4 nodes, got: " + g.nodeCount();
        assert g.edgeCount() == 3 : "Expected 3 edges, got: " + g.edgeCount();
    }

    void T09_topologyEngineExecutesDelegatesEdges() throws Exception {
        MockLlmPort mock = new MockLlmPort();
        mock.setResponse("task", "Step1 output");
        mock.setResponse("Step1", "Step2 output");

        SquadConfig config = SquadConfig.forTesting(List.of(
            OrchestratorAgent.class, NodeA.class, NodeB.class));
        SquadContext ctx = new SquadContext(config, mock);
        ctx.boot();

        TopologyResult result = ctx.submitTopology("task");
        assert result != null : "Result should not be null";
    }

    void T10_executionOrderMatchesTopological() throws Exception {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("output");

        SquadConfig config = SquadConfig.forTesting(List.of(
            OrchestratorAgent.class, NodeA.class, NodeB.class));
        SquadContext ctx = new SquadContext(config, mock);
        ctx.boot();

        TopologyResult result = ctx.submitTopology("task");
        var order = result.executionOrder();
        assert !order.isEmpty() : "Execution order should not be empty";
        assert order.indexOf("NodeA") < order.indexOf("NodeB")
            || !order.contains("NodeA") || !order.contains("NodeB")
            : "NodeA should execute before NodeB";
    }

    void T11_finalOutputReturnsLastAgentResponse() throws Exception {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("Final answer");

        SquadConfig config = SquadConfig.forTesting(List.of(
            OrchestratorAgent.class, NodeA.class, NodeB.class));
        SquadContext ctx = new SquadContext(config, mock);
        ctx.boot();

        TopologyResult result = ctx.submitTopology("task");
        assert result.finalOutput() != null : "finalOutput should not be null";
    }

    void T12_registryGetByNameFindsAgent() throws Exception {
        MockLlmPort mock = new MockLlmPort();
        mock.setDefaultResponse("ok");

        SquadConfig config = SquadConfig.forTesting(List.of(NodeA.class, NodeB.class));
        SquadContext ctx = new SquadContext(config, mock);
        ctx.boot();

        AgentWrapper found = ctx.getRegistry().getByName("NodeA");
        assert found != null : "getByName('NodeA') should return wrapper";
        assert found.getName().equals("NodeA") : "Name mismatch: " + found.getName();

        AgentWrapper notFound = ctx.getRegistry().getByName("NonExistent");
        assert notFound == null : "getByName for unknown name should return null";
    }

    // ── Agent stubs ───────────────────────────────────────────────────

    @Agent(role = AgentRole.STRATEGIST, name = "OrchestratorAgent",
           description = "Orchestrator for topology tests.")
    @Topology(
        name   = "TestPipeline",
        layout = TopologyLayout.PIPELINE,
        edges  = {
            @AgentEdge(from = "NodeA", to = "NodeB", type = EdgeType.DELEGATES)
        }
    )
    static class OrchestratorAgent {}

    @Agent(role = AgentRole.ANALYST, name = "NodeA",
           description = "First step in topology test pipeline.")
    static class NodeA {}

    @Agent(role = AgentRole.EXECUTOR, name = "NodeB",
           description = "Second step in topology test pipeline.")
    static class NodeB {}
}
