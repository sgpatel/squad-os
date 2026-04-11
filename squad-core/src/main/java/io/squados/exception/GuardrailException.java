package io.squados.exception;

/**
 * Thrown when a @Guardrails filter blocks a request (BLOCK_AND_LOG action).
 * Not retried by RetryEngine.
 */
public class GuardrailException extends RuntimeException {

    private final String filterName;
    private final String violationType;

    public GuardrailException(String filterName, String violationType, String message) {
        super(message);
        this.filterName    = filterName;
        this.violationType = violationType;
    }

    public String getFilterName()    { return filterName; }
    public String getViolationType() { return violationType; }
}
