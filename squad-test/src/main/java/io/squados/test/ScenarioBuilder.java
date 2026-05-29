package io.squados.test;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.squados.topology.TopologyResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multi-step scenario DSL for agent integration tests.
 *
 * A scenario models a complete test case as a sequence of:
 *   given → configure responses for specific agents
 *   when  → execute a task through the harness
 *   then  → assert the outcome
 *
 * <pre>
 *   harness.scenario("Research Pipeline")
 *       .given("research", "Climate data: CO2 +50%, temp +1.2°C.")
 *       .given("Climate data", "DRAFT: Climate article.")
 *       .given("DRAFT:", "PUBLISHED: Climate article ready.")
 *       .when("research climate change for article")
 *       .thenTopology()
 *           .hasSteps(3)
 *           .stepIs(0, "ResearchAgent")
 *           .finalOutputContains("PUBLISHED")
 *           .isSuccess();
 * </pre>
 */
public class ScenarioBuilder {

    private final String           name;
    private final AgentTestHarness harness;

    ScenarioBuilder(String name, AgentTestHarness harness) {
        this.name    = name;
        this.harness = harness;
    }

    // ── Given ─────────────────────────────────────────────────────────

    /** Register a response for tasks containing {@code key}. */
    public ScenarioBuilder given(String key, String response) {
        harness.llm().setResponse(key, response);
        return this;
    }

    /** Register a response keyed on the class simple name (convenient for role-based routing). */
    public ScenarioBuilder given(Class<?> agentClass, String key, String response) {
        harness.llm().setResponse(key, response);
        return this;
    }

    /** Set the fallback response. */
    public ScenarioBuilder givenDefault(String response) {
        harness.llm().setDefaultResponse(response);
        return this;
    }

    // ── When ──────────────────────────────────────────────────────────

    /** Execute via the default submit and return assertions for AgentResponse. */
    public AgentResponseAssert when(String task) {
        AgentResponse r = harness.submit(task);
        return new AgentResponseAssert(r, name);
    }

    /** Execute to a specific role and return assertions for AgentResponse. */
    public AgentResponseAssert whenSubmitTo(AgentRole role, String task) {
        AgentResponse r = harness.submitTo(role, task);
        return new AgentResponseAssert(r, name);
    }

    /** Execute via semantic router and return assertions for AgentResponse. */
    public AgentResponseAssert whenSemantic(String task) {
        AgentResponse r = harness.submitSemantic(task);
        return new AgentResponseAssert(r, name);
    }

    /** Execute via topology and return assertions for TopologyResult. */
    public TopologyAssert whenTopology(String input) {
        TopologyResult r = harness.submitTopology(input);
        return new TopologyAssert(r, name);
    }

    // ══════════════════════════════════════════════════════════════════
    // Inner assertion DSLs — returned by when*() methods
    // ══════════════════════════════════════════════════════════════════

    /** Chainable assertions on an AgentResponse. */
    public static final class AgentResponseAssert {
        private final AgentResponse r;
        private final String        scenario;

        AgentResponseAssert(AgentResponse r, String scenario) {
            this.r        = r;
            this.scenario = scenario;
        }

        /** Asserts success and returns {@code this} for chaining. */
        public AgentResponseAssert isSuccess() {
            SquadAssertions.assertSuccess(r);
            return this;
        }

        /** Asserts failure and returns {@code this} for chaining. */
        public AgentResponseAssert isFailure() {
            SquadAssertions.assertFailure(r);
            return this;
        }

        public AgentResponseAssert contentContains(String fragment) {
            SquadAssertions.assertContentContains(r, fragment);
            return this;
        }

        public AgentResponseAssert hasRole(AgentRole role) {
            SquadAssertions.assertRole(r, role);
            return this;
        }

        public AgentResponseAssert fromAgent(String name) {
            SquadAssertions.assertAgentName(r, name);
            return this;
        }

        /** Apply a custom assertion lambda. */
        public AgentResponseAssert satisfies(Consumer<AgentResponse> assertion) {
            assertion.accept(r);
            return this;
        }

        /** Return the underlying response for further inspection. */
        public AgentResponse get() { return r; }
    }

    /** Chainable assertions on a TopologyResult. */
    public static final class TopologyAssert {
        private final TopologyResult r;
        private final String         scenario;

        TopologyAssert(TopologyResult r, String scenario) {
            this.r        = r;
            this.scenario = scenario;
        }

        /** Asserts topology succeeded and returns {@code this} for chaining. */
        public TopologyAssert isSuccess() {
            SquadAssertions.assertTopologySuccess(r);
            return this;
        }

        /** Asserts topology failed and returns {@code this} for chaining. */
        public TopologyAssert isFailure() {
            SquadAssertions.assertTopologyFailure(r);
            return this;
        }

        public TopologyAssert hasSteps(int count) {
            SquadAssertions.assertTopologySteps(r, count);
            return this;
        }

        /** Assert that the agent at position {@code pos} (0-based) is {@code agentName}. */
        public TopologyAssert stepIs(int pos, String agentName) {
            SquadAssertions.assertTopologyStep(r, pos, agentName);
            return this;
        }

        public TopologyAssert stepOutputContains(String agentName, String fragment) {
            SquadAssertions.assertTopologyOutputContains(r, agentName, fragment);
            return this;
        }

        public TopologyAssert finalOutputContains(String fragment) {
            SquadAssertions.assertTopologyFinalOutputContains(r, fragment);
            return this;
        }

        /** Apply a custom assertion lambda. */
        public TopologyAssert satisfies(Consumer<TopologyResult> assertion) {
            assertion.accept(r);
            return this;
        }

        /** Return the underlying result for further inspection. */
        public TopologyResult get() { return r; }
    }
}
