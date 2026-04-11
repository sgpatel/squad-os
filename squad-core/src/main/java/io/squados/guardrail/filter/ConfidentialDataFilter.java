package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Detects confidential data patterns: API keys, passwords, private keys, tokens.
 * Default action: REDACT.
 */
public class ConfidentialDataFilter implements GuardrailFilter {

    private static final List<String> KEYWORDS = List.of(
        "api_key", "api-key", "apikey",
        "password=", "passwd=", "secret=",
        "private_key", "privatekey",
        "bearer ", "token=",
        "aws_access_key", "aws_secret",
        "-----begin rsa private key",
        "-----begin private key"
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        String lower = ctx.text().toLowerCase();
        for (String kw : KEYWORDS) {
            if (lower.contains(kw)) {
                return FilterResult.violation(
                    "ConfidentialDataFilter", "ConfidentialData",
                    kw, 0.95f, GuardrailAction.REDACT);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
