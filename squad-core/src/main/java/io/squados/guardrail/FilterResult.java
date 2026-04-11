package io.squados.guardrail;

/**
 * Result returned by a GuardrailFilter.
 */
public class FilterResult {

    private final boolean         passed;
    private final String          processedText;
    private final GuardrailViolation violation;

    private FilterResult(boolean passed, String processedText, GuardrailViolation violation) {
        this.passed        = passed;
        this.processedText = processedText;
        this.violation     = violation;
    }

    /** Filter passed — text is clean or redacted. */
    public static FilterResult pass(String text) {
        return new FilterResult(true, text, null);
    }

    /** Filter detected a violation. */
    public static FilterResult violation(String filterName, String matchedValue,
                                          float confidence, GuardrailAction action) {
        GuardrailViolation v = new GuardrailViolation(
            filterName, filterName, matchedValue, confidence, action);
        boolean passed = (action != GuardrailAction.BLOCK_AND_LOG);
        return new FilterResult(passed, "[REDACTED]", v);
    }

    /** Filter detected a violation with explicit type. */
    public static FilterResult violation(String filterName, String violationType,
                                          String matchedValue, float confidence,
                                          GuardrailAction action) {
        GuardrailViolation v = new GuardrailViolation(
            filterName, violationType, matchedValue, confidence, action);
        boolean passed = (action != GuardrailAction.BLOCK_AND_LOG);
        String processed = (action == GuardrailAction.REDACT) ? "[REDACTED]" : null;
        return new FilterResult(passed, processed, v);
    }

    public boolean          passed()        { return passed; }
    public String           processedText() { return processedText; }
    public GuardrailViolation violation()   { return violation; }
    public boolean          hasViolation()  { return violation != null; }
}
