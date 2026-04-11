package io.squados.guardrail.filter;

import io.squados.guardrail.*;

import java.util.regex.Pattern;

/**
 * Detects common PII patterns: SSN, credit card numbers, email addresses, phone numbers.
 * Default action: REDACT.
 */
public class PiiDetector implements GuardrailFilter {

    private static final Pattern SSN   = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CC    = Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?1[-.]?)?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b");

    @Override
    public FilterResult apply(FilterContext ctx) {
        String text = ctx.text();
        if (SSN.matcher(text).find()) {
            return FilterResult.violation("PiiDetector", "SSN", "***-**-****", 0.99f, GuardrailAction.REDACT);
        }
        if (CC.matcher(text).find()) {
            return FilterResult.violation("PiiDetector", "CreditCard", "****", 0.95f, GuardrailAction.REDACT);
        }
        if (PHONE.matcher(text).find()) {
            return FilterResult.violation("PiiDetector", "PhoneNumber", "***-***-****", 0.85f, GuardrailAction.REDACT);
        }
        if (EMAIL.matcher(text).find()) {
            return FilterResult.violation("PiiDetector", "EmailAddress", "[redacted]", 0.80f, GuardrailAction.REDACT);
        }
        return FilterResult.pass(text);
    }
}
