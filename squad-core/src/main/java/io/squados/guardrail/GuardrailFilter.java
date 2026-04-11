package io.squados.guardrail;

/**
 * SPI for custom guardrail filters.
 *
 * Implement this interface to add a custom compliance or safety check.
 * Register via @Guardrails(filters = { MyFilter.class }).
 */
@FunctionalInterface
public interface GuardrailFilter {

    /**
     * Apply this filter to the given context.
     *
     * @param ctx  Contains the text to check, agent name, and phase (input/output).
     * @return     FilterResult — pass, redact, or block.
     */
    FilterResult apply(FilterContext ctx);
}
