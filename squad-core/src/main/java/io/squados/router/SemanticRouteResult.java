package io.squados.router;

import io.squados.annotation.AgentRole;

/**
 * The outcome of a single semantic routing decision.
 *
 * @param agentName    Name of the chosen agent.
 * @param role         Role of the chosen agent.
 * @param confidence   Cosine similarity score that drove the decision (0.0–1.0).
 * @param usedFallback True when no agent exceeded {@code minConfidence} and the
 *                     fallback agent was selected instead.
 */
public record SemanticRouteResult(
        String    agentName,
        AgentRole role,
        float     confidence,
        boolean   usedFallback
) {

    @Override
    public String toString() {
        return String.format(
            "SemanticRouteResult{agent='%s', role=%s, confidence=%.3f, fallback=%b}",
            agentName, role, confidence, usedFallback);
    }
}
