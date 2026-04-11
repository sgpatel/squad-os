package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Detects potential hallucination signals in LLM output.
 * Applied to output phase only.
 * Default action: LOG_ONLY (hallucinations are flagged, not blocked).
 *
 * Detects: invented citations, confident false claims, made-up statistics.
 */
public class HallucinationDetector implements GuardrailFilter {

    private static final List<String> SIGNALS = List.of(
        "according to a study that",
        "research shows that 100%",
        "it is a proven fact",
        "studies confirm that all",
        "as stated in [",
        "published in [",
        "source: [",
        "citation: ["
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        if (!ctx.isOutput()) return FilterResult.pass(ctx.text());

        String lower = ctx.text().toLowerCase();
        for (String signal : SIGNALS) {
            if (lower.contains(signal)) {
                return FilterResult.violation(
                    "HallucinationDetector", "PotentialHallucination",
                    signal, 0.60f, GuardrailAction.LOG_ONLY);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
