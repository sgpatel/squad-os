package io.squados.guardrail;

import io.squados.annotation.Guardrails;
import io.squados.exception.GuardrailException;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the pluggable guardrail filter pipeline for @Guardrails agents.
 *
 * Input check  → runs before LLM call (checks user input)
 * Output check → runs after LLM response (checks LLM output)
 *
 * BLOCK_AND_LOG violations throw GuardrailException (not retried by RetryEngine).
 */
public class GuardrailEngine {

    private final GuardrailAuditLog auditLog = new GuardrailAuditLog();

    /**
     * Check input text through all filters in the @Guardrails annotation.
     *
     * @throws GuardrailException if any filter returns BLOCK_AND_LOG
     */
    public GuardrailResult checkInput(Guardrails guardrails, String text, String agentName) {
        if (!guardrails.inputCheck()) return GuardrailResult.clean(text);
        return runFilters(guardrails, text, agentName, "input");
    }

    /**
     * Check output text through all filters.
     *
     * @throws GuardrailException if any filter returns BLOCK_AND_LOG
     */
    public GuardrailResult checkOutput(Guardrails guardrails, String text, String agentName) {
        if (!guardrails.outputCheck()) return GuardrailResult.clean(text);
        return runFilters(guardrails, text, agentName, "output");
    }

    public GuardrailAuditLog getAuditLog() { return auditLog; }

    // ── Internal ──────────────────────────────────────────────────────

    private GuardrailResult runFilters(Guardrails guardrails, String text,
                                        String agentName, String phase) {
        FilterContext ctx        = new FilterContext(text, agentName, phase);
        String        current   = text;
        List<GuardrailViolation> violations = new ArrayList<>();

        for (Class<?> filterClass : guardrails.filters()) {
            GuardrailFilter filter = instantiateFilter(filterClass);
            FilterResult result = filter.apply(new FilterContext(current, agentName, phase));

            if (result.hasViolation()) {
                GuardrailViolation v = result.violation();
                violations.add(v);
                auditLog.record(v);

                if (v.action() == GuardrailAction.BLOCK_AND_LOG) {
                    throw new GuardrailException(v.filterName(), v.violationType(),
                        "Guardrail blocked in " + agentName + " [" + phase + "]: "
                        + v.violationType() + " detected (confidence=" + v.confidence() + ")");
                }
                if (v.action() == GuardrailAction.REDACT && result.processedText() != null) {
                    current = result.processedText();
                }
            } else if (result.processedText() != null) {
                current = result.processedText();
            }
        }

        return GuardrailResult.withViolations(current, violations);
    }

    private GuardrailFilter instantiateFilter(Class<?> cls) {
        try {
            var ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (GuardrailFilter) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate GuardrailFilter: "
                + cls.getSimpleName(), e);
        }
    }
}
