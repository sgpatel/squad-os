package io.squados.router;

import io.squados.annotation.SemanticRouter;
import io.squados.context.AgentRegistry;
import io.squados.context.AgentWrapper;
import io.squados.memory.retrieval.EmbeddingPort;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes tasks to the best-matching agent using cosine similarity on embeddings.
 *
 * At boot time: embeds each agent's {@code @Agent.description} field.
 * At runtime: embeds the incoming task, computes cosine similarity,
 *             returns the highest-scoring agent above {@code minConfidence}.
 *
 * Requires an {@link EmbeddingPort} to be wired before {@code boot()}.
 */
public class SemanticRouterEngine {

    private final AgentRegistry  registry;
    private final EmbeddingPort  embeddingPort;

    /** Agent name → pre-computed embedding of the agent description. */
    private final Map<String, float[]> agentEmbeddings = new HashMap<>();

    /** Bootstrap agent: the fallback wrapper resolved at init time. */
    private AgentWrapper fallbackWrapper;

    /** The @SemanticRouter annotation from the orchestrator agent class. */
    private SemanticRouter config;

    public SemanticRouterEngine(AgentRegistry registry, EmbeddingPort embeddingPort) {
        this.registry      = registry;
        this.embeddingPort = embeddingPort;
    }

    /**
     * Pre-compute embeddings for all registered agents.
     * Called once during {@code SquadContext.boot()}.
     *
     * @param routerAnnotation The {@code @SemanticRouter} annotation from the orchestrator.
     */
    public void init(SemanticRouter routerAnnotation) {
        this.config = routerAnnotation;

        for (AgentWrapper wrapper : registry.all()) {
            String description = wrapper.getAgentClass()
                .getAnnotation(io.squados.annotation.Agent.class).description();
            if (description != null && !description.isBlank()) {
                float[] vec = embeddingPort.embed(description);
                agentEmbeddings.put(wrapper.getName(), vec);
            }
        }

        // Resolve fallback agent
        String fallbackName = routerAnnotation.fallback();
        if (fallbackName != null && !fallbackName.isBlank()) {
            fallbackWrapper = registry.getByName(fallbackName);
        }
        if (fallbackWrapper == null) {
            fallbackWrapper = registry.getLead();
        }

        if (routerAnnotation.logRouting()) {
            System.out.printf(
                "[SemanticRouter] Indexed %d agent embeddings. Fallback: %s%n",
                agentEmbeddings.size(),
                fallbackWrapper != null ? fallbackWrapper.getName() : "none");
        }
    }

    /**
     * Route a task to the best-matching registered agent.
     *
     * @param task  Incoming task description.
     * @return      Routing decision with agent name, role, confidence, and fallback flag.
     */
    public SemanticRouteResult route(String task) {
        if (task == null || task.isBlank()) {
            return fallbackResult();
        }

        float[] taskVec    = embeddingPort.embed(task);
        float   bestScore  = -1f;
        AgentWrapper best  = null;

        for (AgentWrapper wrapper : registry.all()) {
            float[] agentVec = agentEmbeddings.get(wrapper.getName());
            if (agentVec == null) continue;
            float sim = CosineSimilarity.compute(taskVec, agentVec);
            if (sim > bestScore) {
                bestScore = sim;
                best      = wrapper;
            }
        }

        float minConf = (config != null) ? config.minConfidence() : 0.60f;
        boolean usedFallback = (best == null || bestScore < minConf);
        AgentWrapper chosen  = usedFallback ? fallbackWrapper : best;
        float reportedScore  = usedFallback ? 0f : bestScore;

        if (chosen == null) {
            throw new IllegalStateException(
                "[SemanticRouter] No agents available and no fallback configured.");
        }

        if (config != null && config.logRouting()) {
            System.out.printf(
                "[SemanticRouter] Task routed to '%s' (confidence=%.3f, fallback=%b)%n",
                chosen.getName(), reportedScore, usedFallback);
        }

        return new SemanticRouteResult(
            chosen.getName(), chosen.getRole(), reportedScore, usedFallback);
    }

    private SemanticRouteResult fallbackResult() {
        if (fallbackWrapper == null) {
            throw new IllegalStateException("[SemanticRouter] No fallback agent configured.");
        }
        return new SemanticRouteResult(
            fallbackWrapper.getName(), fallbackWrapper.getRole(), 0f, true);
    }
}
