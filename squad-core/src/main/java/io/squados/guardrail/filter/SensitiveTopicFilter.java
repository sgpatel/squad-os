package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Flags sensitive topics: politics, religion, medical advice, legal advice.
 * Default action: LOG_ONLY (flag for review; agent may still respond).
 */
public class SensitiveTopicFilter implements GuardrailFilter {

    private static final List<String> SENSITIVE_TOPICS = List.of(
        "abortion", "euthanasia", "assisted suicide",
        "political party", "vote for", "election fraud",
        "my medical diagnosis", "prescribe medication", "dosage of",
        "legal advice", "am i liable", "should i sue"
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        String lower = ctx.text().toLowerCase();
        for (String topic : SENSITIVE_TOPICS) {
            if (lower.contains(topic)) {
                return FilterResult.violation(
                    "SensitiveTopicFilter", "SensitiveTopic",
                    topic, 0.70f, GuardrailAction.LOG_ONLY);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
