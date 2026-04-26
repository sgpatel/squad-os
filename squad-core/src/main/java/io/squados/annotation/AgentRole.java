package io.squados.annotation;

import io.squados.llm.LlmOptions;

/**
 * All supported agent roles in SquadOS.
 * Each role ships with opinionated default LlmOptions.
 * These can be overridden in squad.yml per agent.
 */
public enum AgentRole {

    // ── Gaming roles ──────────────────────────────────────────────────
    STRATEGIST,  // Plans, coordinates, highest token budget
    TANK,        // Defensive, deterministic, low temperature
    DPS,         // Aggressive, creative, high temperature
    SUPPORT,     // Stable, consistent, low temperature
    HEALER,      // Restores/updates state, stable, slightly higher budget
    SCOUT,       // Recon, fast, low token usage

    // ── Work / task roles ─────────────────────────────────────────────
    ANALYST,     // Data-driven, balanced reasoning
    EXECUTOR,    // Builds deliverables, structured output
    CRITIC,      // Reviews, flags gaps, moderate temperature
    RESEARCHER,  // Sources facts, low temperature for accuracy
    WRITER,      // Long-form content, moderate temperature
    EDITOR,      // Refines and cuts, low temperature
    VISIONARY,   // Bold concepts, high temperature

    // ── Meta role ─────────────────────────────────────────────────────
    WILDCARD;    // No default constraints, all options explicit

    /**
     * Default LlmOptions for this role.
     * Temperature and maxTokens are opinionated per role personality.
     * model is null — resolved from squad.yml llm.model at runtime.
     */
    public LlmOptions defaultOptions() {
        return switch (this) {
            // Strategist: balanced reasoning, needs room to plan
            case STRATEGIST -> new LlmOptions(0.5f, 2048, null);

            // Tank: precise and deterministic — holds the line
            case TANK       -> new LlmOptions(0.3f,  512, null);

            // DPS: creative, aggressive — high variance is fine
            case DPS        -> new LlmOptions(0.8f,  512, null);

            // Support: very stable — heal calls must not hallucinate
            case SUPPORT    -> new LlmOptions(0.2f,  512, null);

            // Healer: stable state-updater — needs slightly more room
            // than Support to emit structured updates (progress, mastery).
            case HEALER     -> new LlmOptions(0.2f, 1024, null);

            // Scout: fast, minimal — recon not essays
            case SCOUT      -> new LlmOptions(0.4f,  256, null);

            // Analyst: data-driven, balanced
            case ANALYST    -> new LlmOptions(0.4f, 1024, null);

            // Executor: structured output, low variance
            case EXECUTOR   -> new LlmOptions(0.3f, 1024, null);

            // Critic: needs reasoning quality for review work
            case CRITIC     -> new LlmOptions(0.4f, 1024, null);

            // Researcher: factual, low temperature for accuracy
            case RESEARCHER -> new LlmOptions(0.2f, 1024, null);

            // Writer: long-form, moderate temperature
            case WRITER     -> new LlmOptions(0.6f, 2048, null);

            // Editor: cutting and refining — precise
            case EDITOR     -> new LlmOptions(0.3f,  512, null);

            // Visionary: bold concepts, high creativity
            case VISIONARY  -> new LlmOptions(0.9f, 1024, null);

            // Wildcard: developer sets everything explicitly
            case WILDCARD   -> LlmOptions.defaults();
        };
    }
}
