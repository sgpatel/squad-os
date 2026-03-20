package io.squados.llm;

/**
 * Wraps the raw response from any LLM provider.
 *
 * content        — The text content of the response.
 * promptTokens   — Tokens consumed by the prompt (for cost tracking).
 * completionTokens — Tokens in the response.
 * model          — The actual model that served the request.
 */
public record LlmResponse(
        String content,
        int    promptTokens,
        int    completionTokens,
        String model
) {
    /** Convenience constructor — used by tests and simple adapters */
    public LlmResponse(String content) {
        this(content, 0, 0, "unknown");
    }

    /** Total tokens used in this call */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    /** True if content is non-null and non-blank */
    public boolean hasContent() {
        return content != null && !content.isBlank();
    }

    @Override
    public String toString() {
        return "LlmResponse{model='" + model
                + "', tokens=" + totalTokens()
                + ", content='" + (content != null
                    ? content.substring(0, Math.min(80, content.length())) + "..."
                    : "null") + "'}";
    }
}
