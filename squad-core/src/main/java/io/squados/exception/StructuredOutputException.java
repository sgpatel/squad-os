package io.squados.exception;

/**
 * Thrown when {@link io.squados.structured.StructuredOutputParser} cannot
 * produce a valid typed object after all retries are exhausted.
 */
public class StructuredOutputException extends RuntimeException {

    private final String schemaClass;
    private final int    attempts;

    public StructuredOutputException(String schemaClass, String parseError, int attempts) {
        super(String.format(
            "StructuredOutput failed for '%s' after %d attempt(s): %s",
            schemaClass, attempts, parseError));
        this.schemaClass = schemaClass;
        this.attempts    = attempts;
    }

    public String getSchemaClass() { return schemaClass; }
    public int    getAttempts()    { return attempts; }
}
