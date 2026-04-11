package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Detects prompt injection attempts — instructions trying to override the agent's behaviour.
 * Default action: BLOCK_AND_LOG.
 */
public class PromptInjectionDetector implements GuardrailFilter {

    private static final List<String> PATTERNS = List.of(
        "ignore previous instructions",
        "ignore all previous",
        "forget your instructions",
        "disregard your",
        "you are now",
        "act as if",
        "pretend you are",
        "new instruction:",
        "system prompt:",
        "jailbreak",
        "override your",
        "bypass your"
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        // Only check user input, not LLM output
        if (!ctx.isInput()) return FilterResult.pass(ctx.text());

        String lower = ctx.text().toLowerCase();
        for (String pattern : PATTERNS) {
            if (lower.contains(pattern)) {
                return FilterResult.violation(
                    "PromptInjectionDetector", "PromptInjection",
                    pattern, 0.95f, GuardrailAction.BLOCK_AND_LOG);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
