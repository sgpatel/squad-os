package io.squados.structured;

/**
 * Wraps the result of a {@link io.squados.annotation.StructuredOutput} parse.
 *
 * Accessible via {@code AgentResponse.structuredOutput(Class<T>)}.
 */
public class StructuredOutputResult<T> {

    private final T       value;
    private final String  rawResponse;
    private final int     parseAttempts;
    private final boolean success;
    private final String  errorMessage;

    private StructuredOutputResult(T value, String rawResponse,
                                   int parseAttempts, boolean success, String error) {
        this.value         = value;
        this.rawResponse   = rawResponse;
        this.parseAttempts = parseAttempts;
        this.success       = success;
        this.errorMessage  = error;
    }

    public static <T> StructuredOutputResult<T> success(T value, String raw, int attempts) {
        return new StructuredOutputResult<>(value, raw, attempts, true, null);
    }

    public static <T> StructuredOutputResult<T> failure(String raw, int attempts, String error) {
        return new StructuredOutputResult<>(null, raw, attempts, false, error);
    }

    /** The deserialised POJO, or null if parsing failed. */
    public T       value()         { return value; }
    /** The raw LLM text before JSON extraction. */
    public String  rawResponse()   { return rawResponse; }
    /** Number of parse attempts (1 = first try succeeded). */
    public int     parseAttempts() { return parseAttempts; }
    public boolean isSuccess()     { return success; }
    public String  errorMessage()  { return errorMessage; }

    @Override
    public String toString() {
        return "StructuredOutputResult{success=" + success
               + ", attempts=" + parseAttempts
               + ", value=" + value + "}";
    }
}
