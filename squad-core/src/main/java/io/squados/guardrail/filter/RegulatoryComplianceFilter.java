package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.List;

/**
 * Flags potential regulatory compliance issues: GDPR, HIPAA, FINRA violations.
 * Default action: LOG_ONLY (compliance team review required).
 */
public class RegulatoryComplianceFilter implements GuardrailFilter {

    private static final List<String> COMPLIANCE_SIGNALS = List.of(
        "patient record", "medical record", "health information",
        "account balance", "routing number", "tax id",
        "insider information", "non-public information",
        "personal data of", "data subject", "gdpr",
        "hipaa", "pii of", "protected health"
    );

    @Override
    public FilterResult apply(FilterContext ctx) {
        String lower = ctx.text().toLowerCase();
        for (String signal : COMPLIANCE_SIGNALS) {
            if (lower.contains(signal)) {
                return FilterResult.violation(
                    "RegulatoryComplianceFilter", "ComplianceRisk",
                    signal, 0.80f, GuardrailAction.LOG_ONLY);
            }
        }
        return FilterResult.pass(ctx.text());
    }
}
