package io.squados.llm;

/**
 * Immutable options bag passed to every LLM call.
 *
 * temperature  — 0.0 (deterministic) to 2.0 (very creative).
 *                Each AgentRole ships an opinionated default.
 * maxTokens    — Hard cap on response length.
 * model        — Model identifier string (e.g. "claude-sonnet-4-6").
 *                Null means "use whatever squad.yml specifies".
 */
public record LlmOptions(
        float  temperature,
        int    maxTokens,
        String model
) {
    /** Validation on construction */
    public LlmOptions {
        if (temperature < 0.0f || temperature > 2.0f) {
            throw new IllegalArgumentException(
                "temperature must be between 0.0 and 2.0, got: " + temperature
            );
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException(
                "maxTokens must be > 0, got: " + maxTokens
            );
        }
    }

    /**
     * Sensible defaults for agents where role defaults don't apply.
     * Temperature 0.5, 1024 tokens, model resolved from squad.yml.
     */
    public static LlmOptions defaults() {
        return new LlmOptions(0.5f, 1024, null);
    }

    /**
     * Returns a copy with the given model set.
     * Used at runtime to inject the squad.yml model string.
     */
    public LlmOptions withModel(String resolvedModel) {
        return new LlmOptions(this.temperature, this.maxTokens, resolvedModel);
    }

    @Override
    public String toString() {
        return "LlmOptions{temperature=" + temperature
                + ", maxTokens=" + maxTokens
                + ", model='" + (model != null ? model : "<from squad.yml>") + "'}";
    }
}
