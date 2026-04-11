package io.squados.guardrail;

/**
 * Input context passed to each GuardrailFilter.
 *
 * text      — The text to check (input or output)
 * agentName — Name of the agent being filtered
 * phase     — "input" (before LLM) | "output" (after LLM)
 */
public record FilterContext(
        String text,
        String agentName,
        String phase
) {
    public static FilterContext input(String text, String agentName) {
        return new FilterContext(text, agentName, "input");
    }

    public static FilterContext output(String text, String agentName) {
        return new FilterContext(text, agentName, "output");
    }

    public boolean isInput()  { return "input".equals(phase); }
    public boolean isOutput() { return "output".equals(phase); }
}
