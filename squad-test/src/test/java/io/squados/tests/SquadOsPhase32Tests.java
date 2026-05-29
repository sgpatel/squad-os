package io.squados.tests;

import io.squados.agent.AgentResponse;
import io.squados.annotation.*;
import io.squados.router.SemanticRouteResult;
import io.squados.test.AgentTestHarness;
import io.squados.test.ResponseCaptor;
import io.squados.test.ScenarioBuilder;
import io.squados.test.SquadAssertions;
import io.squados.topology.TopologyResult;

/**
 * Phase 32 — squad-test module validation.
 *
 * Tests the squad-test public API:
 *   TK01–TK04  AgentTestHarness (boot, submit, reset, embedding)
 *   TK05–TK08  SquadAssertions (success, failure, content, topology)
 *   TK09–TK11  ResponseCaptor (capture, forRole, assertAllSucceeded)
 *   TK12–TK14  ScenarioBuilder DSL (when/thenAgent, when/thenTopology)
 *   TK15       Full test-kit pipeline scenario
 */
public class SquadOsPhase32Tests {

    // ── Agent stubs ───────────────────────────────────────────────────

    @Agent(role = AgentRole.RESEARCHER, name = "ResearchAgent",
           description = "Researches factual topics with evidence-based summaries.")
    static class ResearchAgent {}

    @Agent(role = AgentRole.WRITER, name = "WriterAgent",
           description = "Writes blog posts, articles, and narrative content.")
    static class WriterAgent {}

    @Agent(role = AgentRole.CRITIC, name = "ReviewAgent",
           description = "Reviews and approves content for final publication.")
    static class ReviewAgent {}

    @Agent(role = AgentRole.ANALYST, name = "GeneralistAgent",
           description = "General purpose analyst handling any unrouted task.")
    static class GeneralistAgent {}

    @Agent(role = AgentRole.STRATEGIST, name = "RouterAgent",
           description = "Routes incoming tasks to the appropriate specialist agent.")
    @SemanticRouter(fallback = "GeneralistAgent", minConfidence = 0.40f)
    static class RouterAgent {}

    @Agent(role = AgentRole.EXECUTOR, name = "OrchestratorAgent",
           description = "Orchestrates the research-write-review pipeline.")
    @Topology(
        name   = "ContentPipeline",
        layout = TopologyLayout.PIPELINE,
        edges  = {
            @AgentEdge(from = "ResearchAgent", to = "WriterAgent",  type = EdgeType.DELEGATES),
            @AgentEdge(from = "WriterAgent",   to = "ReviewAgent",  type = EdgeType.DELEGATES)
        }
    )
    static class OrchestratorAgent {}

    // ── Main runner ───────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 32 — squad-test module                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "TK01_harnessBootsSuccessfully",
            "TK02_harnessSubmitReturnsConfiguredResponse",
            "TK03_harnessResetClearsCalls",
            "TK04_harnessSubmitToRole",
            "TK05_assertSuccessPassesOnSuccess",
            "TK06_assertFailurePassesOnFailure",
            "TK07_assertContentContainsMatchesFragment",
            "TK08_assertTopologySuccess",
            "TK09_captorRecordsEachResponse",
            "TK10_captorForRoleFilters",
            "TK11_captorAssertAllSucceeded",
            "TK12_scenarioDslAgentResponse",
            "TK13_scenarioDslTopology",
            "TK14_scenarioDslSemanticRouter",
            "TK15_fullTestKitPipelineScenario",
        };

        var t = new SquadOsPhase32Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, cause.getMessage());
                if (System.getProperty("verbose") != null) cause.printStackTrace();
                failed++;
            }
        }

        System.out.println();
        System.out.printf("Phase 32 result: %d passed, %d failed%n", passed, failed);
        System.out.println();
        if (failed > 0) System.exit(1);
    }

    // ══════════════════════════════════════════════════════════════════
    // TK01–TK04  AgentTestHarness
    // ══════════════════════════════════════════════════════════════════

    void TK01_harnessBootsSuccessfully() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class, WriterAgent.class)
            .boot();

        SquadAssertions.assertNotNull(harness, "Harness must not be null");
        SquadAssertions.assertNotNull(harness.context(), "SquadContext must be initialised");
        SquadAssertions.assertNotNull(harness.llm(), "MockLlmPort must be wired");
        SquadAssertions.assertNotNull(harness.captor(), "ResponseCaptor must be initialised");
    }

    void TK02_harnessSubmitReturnsConfiguredResponse() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class)
            .withResponse("research", "Climate data: CO2 +50%, temp +1.2°C.")
            .boot();

        AgentResponse r = harness.submit("research climate change");
        SquadAssertions.assertSuccess(r);
        SquadAssertions.assertContentContains(r, "CO2");
        SquadAssertions.assertRole(r, AgentRole.RESEARCHER);
    }

    void TK03_harnessResetClearsCalls() {
        AgentTestHarness harness = AgentTestHarness
            .of(WriterAgent.class)
            .withResponse("write", "Blog post about AI.")
            .boot();

        harness.submit("write blog post");
        assert harness.llm().getCallCount() > 0 : "LLM should have been called";
        assert harness.captor().count() == 1 : "Captor should have 1 entry";

        harness.resetCalls();
        assert harness.llm().getCallCount() == 0 : "Call count should reset to 0";
        assert harness.captor().count() == 0 : "Captor should be empty after reset";
    }

    void TK04_harnessSubmitToRole() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class, WriterAgent.class)
            .withResponse("write", "Article draft ready.")
            .boot();

        AgentResponse r = harness.submitTo(AgentRole.WRITER, "write about quantum computing");
        SquadAssertions.assertSuccess(r);
        SquadAssertions.assertRole(r, AgentRole.WRITER);
        SquadAssertions.assertContentContains(r, "draft");
    }

    // ══════════════════════════════════════════════════════════════════
    // TK05–TK08  SquadAssertions
    // ══════════════════════════════════════════════════════════════════

    void TK05_assertSuccessPassesOnSuccess() {
        AgentTestHarness harness = AgentTestHarness
            .of(WriterAgent.class)
            .withDefaultResponse("Mock success output")
            .boot();

        AgentResponse r = harness.submit("any task");
        // Should not throw
        SquadAssertions.assertSuccess(r);
        SquadAssertions.assertTokensRecorded(r);
    }

    void TK06_assertFailurePassesOnFailure() {
        // Verify that assertSuccess correctly throws on a failed response
        AgentResponse failed = AgentResponse.failure(
            AgentRole.RESEARCHER, "ResearchAgent", "timeout", java.time.Instant.now());

        boolean threw = false;
        try {
            SquadAssertions.assertSuccess(failed);
        } catch (AssertionError e) {
            threw = true;
            assert e.getMessage().contains("failure") || e.getMessage().contains("timeout")
                : "Error message should be descriptive: " + e.getMessage();
        }
        assert threw : "assertSuccess should throw on a failed response";

        // And assertFailure should pass
        SquadAssertions.assertFailure(failed);
    }

    void TK07_assertContentContainsMatchesFragment() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class)
            .withResponse("climate", "Arctic ice extent reduced by 40% since 1980.")
            .boot();

        AgentResponse r = harness.submit("research climate Arctic");
        SquadAssertions.assertContentContains(r, "Arctic");
        SquadAssertions.assertContentContains(r, "40%");
        SquadAssertions.assertContentContains(r, "arctic"); // case-insensitive

        // Negative: fragment not present
        boolean threw = false;
        try {
            SquadAssertions.assertContentContains(r, "quantum");
        } catch (AssertionError e) {
            threw = true;
        }
        assert threw : "assertContentContains should throw when fragment is absent";
    }

    void TK08_assertTopologySuccess() {
        AgentTestHarness harness = AgentTestHarness
            .of(OrchestratorAgent.class, ResearchAgent.class, WriterAgent.class, ReviewAgent.class)
            .withResponse("research", "Research findings: data confirmed.")
            .withResponse("findings", "DRAFT: Article ready.")
            .withResponse("DRAFT", "APPROVED: Article published.")
            .boot();

        TopologyResult r = harness.submitTopology("research and write about AI");
        SquadAssertions.assertTopologySuccess(r);
        SquadAssertions.assertTopologySteps(r, 3);
        SquadAssertions.assertTopologyFinalOutputContains(r, "APPROVED");
    }

    // ══════════════════════════════════════════════════════════════════
    // TK09–TK11  ResponseCaptor
    // ══════════════════════════════════════════════════════════════════

    void TK09_captorRecordsEachResponse() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class, WriterAgent.class)
            .withResponse("research", "Research findings.")
            .withResponse("write", "Article draft.")
            .boot();

        harness.submit("research climate");
        harness.submitTo(AgentRole.WRITER, "write climate article");

        ResponseCaptor cap = harness.captor();
        cap.assertCount(2);
        cap.assertAllSucceeded();
        assert cap.anyContentContains("Research") : "Captor should contain research output";
        assert cap.anyContentContains("Article") : "Captor should contain writer output";
    }

    void TK10_captorForRoleFilters() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class, WriterAgent.class)
            .withResponse("science", "Research complete.")
            .withResponse("write", "Draft complete.")
            .boot();

        harness.submitTo(AgentRole.RESEARCHER, "science fact-check");
        harness.submitTo(AgentRole.WRITER,     "write about topic");

        ResponseCaptor cap = harness.captor();
        var researchers = cap.forRole(AgentRole.RESEARCHER);
        var writers     = cap.forRole(AgentRole.WRITER);

        assert researchers.size() == 1 : "Should have 1 researcher response";
        assert writers.size()     == 1 : "Should have 1 writer response";
        SquadAssertions.assertContentContains(researchers.get(0), "Research");
        SquadAssertions.assertContentContains(writers.get(0), "Draft");
    }

    void TK11_captorAssertAllSucceeded() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class)
            .withDefaultResponse("All good.")
            .boot();

        harness.submit("task 1");
        harness.submit("task 2");
        harness.submit("task 3");

        // Should not throw
        harness.captor().assertAllSucceeded();
        harness.captor().assertCount(3);
    }

    // ══════════════════════════════════════════════════════════════════
    // TK12–TK14  ScenarioBuilder DSL
    // ══════════════════════════════════════════════════════════════════

    void TK12_scenarioDslAgentResponse() {
        AgentTestHarness harness = AgentTestHarness
            .of(ResearchAgent.class)
            .boot();

        harness.scenario("Basic Research")
            .given("neural", "Neural networks: deep learning models with billions of parameters.")
            .when("research neural networks")
            .isSuccess()
            .contentContains("Neural networks")
            .hasRole(AgentRole.RESEARCHER);
    }

    void TK13_scenarioDslTopology() {
        AgentTestHarness harness = AgentTestHarness
            .of(OrchestratorAgent.class, ResearchAgent.class, WriterAgent.class, ReviewAgent.class)
            .boot();

        // Keys chosen so they do NOT overlap with the original task or each other:
        //   task → ResearchAgent (matches "quantum")
        //   ResearchAgent output → WriterAgent (matches "photon" which is only in research output)
        //   WriterAgent output → ReviewAgent (matches "DRAFT:" which is only in writer output)
        harness.scenario("Content Pipeline")
            .given("quantum",  "Photon entanglement enables quantum key distribution at 99.9% fidelity.")
            .given("photon",   "DRAFT: Quantum cryptography breaks RSA in 2026.")
            .given("DRAFT:",   "PUBLISHED: Quantum crypto article approved.")
            .whenTopology("quantum computing security overview")
            .isSuccess()
            .hasSteps(3)
            .stepIs(0, "ResearchAgent")
            .stepIs(1, "WriterAgent")
            .stepIs(2, "ReviewAgent")
            .finalOutputContains("PUBLISHED");
    }

    void TK14_scenarioDslSemanticRouter() {
        AgentTestHarness harness = AgentTestHarness
            .of(RouterAgent.class, ResearchAgent.class, WriterAgent.class, GeneralistAgent.class)
            .boot();

        // Semantic router: unknown task should fallback
        SemanticRouteResult route = harness.route("calculate the molecular weight of caffeine");
        SquadAssertions.assertFallback(route);
        SquadAssertions.assertRouted(route, "GeneralistAgent");
    }

    // ══════════════════════════════════════════════════════════════════
    // TK15  Full test-kit scenario
    // ══════════════════════════════════════════════════════════════════

    void TK15_fullTestKitPipelineScenario() {
        // Complete end-to-end example as a downstream consumer would write it
        AgentTestHarness harness = AgentTestHarness
            .of(OrchestratorAgent.class, ResearchAgent.class, WriterAgent.class, ReviewAgent.class)
            .named("content-squad")
            .withResponse("research", "IPCC 2025: Global temps up 1.5°C. Tipping points imminent.")
            .withResponse("IPCC",     "DRAFT: Climate tipping points: what science says.")
            .withResponse("DRAFT:",   "PUBLISHED: 'Climate Tipping Points' — peer-reviewed summary.")
            .boot();

        // Execute the topology
        TopologyResult result = harness.submitTopology("research climate tipping points for article");

        // Assert end-to-end using both SquadAssertions and ResponseCaptor
        SquadAssertions.assertTopologySuccess(result);
        SquadAssertions.assertTopologySteps(result, 3);
        SquadAssertions.assertTopologyStep(result, 0, "ResearchAgent");
        SquadAssertions.assertTopologyOutputContains(result, "ResearchAgent", "IPCC");
        SquadAssertions.assertTopologyOutputContains(result, "WriterAgent",   "DRAFT");
        SquadAssertions.assertTopologyOutputContains(result, "ReviewAgent",   "PUBLISHED");
        SquadAssertions.assertTopologyFinalOutputContains(result, "PUBLISHED");

        System.out.printf("         → pipeline: %s steps, final='%s…'%n",
            result.executionOrder().size(),
            result.finalOutput().content().substring(0, 50));
    }
}
