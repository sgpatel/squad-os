package io.squados.test;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.squados.config.SquadConfig;
import io.squados.context.SquadContext;
import io.squados.llm.MockLlmPort;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.router.SemanticRouteResult;
import io.squados.topology.TopologyResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent test harness for SquadOS agents.
 *
 * Removes boilerplate from agent tests: creates a SquadConfig, wires a
 * MockLlmPort, boots the SquadContext, and exposes typed submit methods.
 *
 * <pre>
 *   AgentTestHarness harness = AgentTestHarness
 *       .of(ResearchAgent.class, DraftAgent.class)
 *       .withResponse("research", "Climate data: CO2 +50%.")
 *       .boot();
 *
 *   AgentResponse r = harness.submit("research climate change");
 *   SquadAssertions.assertSuccess(r);
 *   SquadAssertions.assertContentContains(r, "CO2");
 * </pre>
 */
public class AgentTestHarness {

    private final SquadContext context;
    private final MockLlmPort  llm;
    private final ResponseCaptor captor;

    private AgentTestHarness(SquadContext context, MockLlmPort llm, ResponseCaptor captor) {
        this.context = context;
        this.llm     = llm;
        this.captor  = captor;
    }

    // ── Builder ───────────────────────────────────────────────────────

    public static Builder of(Class<?>... agentClasses) {
        return new Builder(Arrays.asList(agentClasses));
    }

    public static Builder of(List<Class<?>> agentClasses) {
        return new Builder(agentClasses);
    }

    public static final class Builder {
        private final List<Class<?>> agents;
        private final MockLlmPort    llm    = new MockLlmPort();
        private EmbeddingPort        emb    = new MockEmbeddingPort();
        private String               squadName = "test-squad";

        private Builder(List<Class<?>> agents) {
            this.agents = new ArrayList<>(agents);
            // Friendly default: return a generic success string
            llm.setDefaultResponse("Mock agent response — configure via withResponse()");
        }

        /** Add a fixed response for tasks whose text contains {@code key}. */
        public Builder withResponse(String key, String response) {
            llm.setResponse(key, response);
            return this;
        }

        /** Override the fallback response used when no key matches. */
        public Builder withDefaultResponse(String response) {
            llm.setDefaultResponse(response);
            return this;
        }

        /** Provide a custom embedding port (default: MockEmbeddingPort). */
        public Builder withEmbedding(EmbeddingPort port) {
            this.emb = port;
            return this;
        }

        /** Override the squad name (default: "test-squad"). */
        public Builder named(String name) {
            this.squadName = name;
            return this;
        }

        /** Boot the SquadContext and return a ready-to-use harness. */
        public AgentTestHarness boot() {
            SquadConfig cfg = SquadConfig.forTesting(agents);
            cfg.setName(squadName);
            SquadContext ctx = new SquadContext(cfg, llm);
            ctx.setEmbeddingPort(emb);
            ResponseCaptor captor = new ResponseCaptor();
            ctx.boot();
            return new AgentTestHarness(ctx, llm, captor);
        }
    }

    // ── Submission ────────────────────────────────────────────────────

    /** Submit to the default (first matching) agent. */
    public AgentResponse submit(String task) {
        AgentResponse r = context.submit(task);
        captor.capture(r);
        return r;
    }

    /** Submit to a specific agent role. */
    public AgentResponse submitTo(AgentRole role, String task) {
        AgentResponse r = context.submitTo(role, task);
        captor.capture(r);
        return r;
    }

    /** Submit via the @SemanticRouter (requires @SemanticRouter agent in the harness). */
    public AgentResponse submitSemantic(String task) {
        AgentResponse r = context.submitSemantic(task);
        captor.capture(r);
        return r;
    }

    /** Route via semantic router and return the route decision (agent name + confidence). */
    public SemanticRouteResult route(String task) {
        if (context.getSemanticRouter() == null) {
            throw new IllegalStateException(
                "No @SemanticRouter agent found. Add a @SemanticRouter-annotated agent class.");
        }
        return context.getSemanticRouter().route(task);
    }

    /** Execute the @Topology pipeline (requires a @Topology agent in the harness). */
    public TopologyResult submitTopology(String input) {
        return context.submitTopology(input);
    }

    // ── Scenario DSL ──────────────────────────────────────────────────

    /** Start building a multi-step scenario. */
    public ScenarioBuilder scenario(String name) {
        return new ScenarioBuilder(name, this);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** Direct access to the underlying SquadContext. */
    public SquadContext context()        { return context; }

    /** Direct access to the underlying MockLlmPort for advanced configuration. */
    public MockLlmPort  llm()            { return llm; }

    /** All responses captured during this harness's lifetime. */
    public ResponseCaptor captor()       { return captor; }

    /**
     * Reset call history on the mock LLM.
     * Registered responses are preserved — call this between test methods.
     */
    public AgentTestHarness resetCalls() {
        llm.reset();
        captor.clear();
        return this;
    }
}
