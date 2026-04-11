package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Detects toxic content: hate speech, harassment, explicit threats.
 * Default action: BLOCK_AND_LOG.
 *
 * Uses keyword heuristics (production implementations would use an ML model).
 */
public class ToxicityFilter implements GuardrailFilter {

    private static final List<String> TOXIC_KEYWORDS = List.of(
        "kill yourself", "kys", "die in a fire",
        "i will hurt you", "i will kill", "you should die",
        "hate you all", "go to hell", "screw you"
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        String lower = ctx.text().toLowerCase();
        for (String kw : TOXIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return FilterResult.violation(
                    "ToxicityFilter", "Toxicity",
                    kw, 0.90f, GuardrailAction.BLOCK_AND_LOG);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
