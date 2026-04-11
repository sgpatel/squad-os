package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Checks that LLM output stays grounded — doesn't claim capabilities or facts
 * outside the agent's defined scope.
 * Applied to output phase. Default action: LOG_ONLY.
 */
public class GroundingFilter implements GuardrailFilter {

    private static final List<String> UNGROUNDED_CLAIMS = List.of(
        "i can access the internet",
        "i can browse",
        "i have real-time",
        "i can make phone calls",
        "i can send emails",
        "i can access your",
        "i know what happened today",
        "my latest update was"
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        if (!ctx.isOutput()) return FilterResult.pass(ctx.text());

        String lower = ctx.text().toLowerCase();
        for (String claim : UNGROUNDED_CLAIMS) {
            if (lower.contains(claim)) {
                return FilterResult.violation(
                    "GroundingFilter", "UngroundedClaim",
                    claim, 0.75f, GuardrailAction.LOG_ONLY);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
