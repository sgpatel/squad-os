package io.squados.llm;

/**
 * SquadOS never calls an LLM directly.
 * It always calls this interface.
 *
 * Spring AI implements it via SpringAiLlmAdapter.
 * LangChain4j can implement it via LangChain4jLlmAdapter.
 * Tests use MockLlmPort for deterministic, zero-cost testing.
 *
 * This is the Dependency Inversion Principle applied to LLMs:
 * SquadOS owns the interface; the ecosystem owns the implementation.
 *
 * ── Architectural contract ───────────────────────────────────────────
 * ONLY SpringAiLlmAdapter (or equivalent adapter) may import
 * org.springframework.ai.** in the entire SquadOS codebase.
 * This is enforced by AT-3 (ArchUnit test).
 */
public interface LlmPort {

    /**
     * Single-turn chat call. Used by most agents.
     *
     * @param systemPrompt  Instructions that define the agent's identity
     *                      and behaviour. Built from {@literal @}Agent metadata.
     * @param userMessage   The task or user input for this invocation.
     * @param options       Temperature, maxTokens, model override.
     * @return              LlmResponse wrapping the raw content string
     *                      and usage metadata.
     */
    LlmResponse chat(String systemPrompt, String userMessage, LlmOptions options);

    /**
     * Structured output call — parses the LLM response into a typed POJO.
     * Use when the agent must return a machine-readable result
     * (e.g. SquadPlan, HealPlaybook, AnalysisReport).
     *
     * @param systemPrompt  System prompt including output format instructions.
     * @param userMessage   The task input.
     * @param responseType  The class to deserialise the LLM response into.
     * @param options       Call options.
     * @param <T>           The expected return type.
     * @return              Parsed instance of T.
     */
    <T> T chatStructured(
            String systemPrompt,
            String userMessage,
            Class<T> responseType,
            LlmOptions options
    );
}
