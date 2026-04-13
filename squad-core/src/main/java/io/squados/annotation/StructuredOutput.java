package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Instructs SquadOS to parse the agent's LLM output as a typed Java object.
 *
 * The framework:
 *   1. Reflects over {@code schema} to generate a JSON schema
 *   2. Appends the schema to the agent system prompt with format instructions
 *   3. Calls the LLM, extracts JSON from the response (even if embedded in prose)
 *   4. Deserialises into an instance of {@code schema}
 *   5. If parsing fails and {@code retryOnMalformed=true}: re-prompts up to
 *      {@code maxRetries} times with the parse error injected
 *   6. Stores the result in {@link io.squados.structured.StructuredOutputResult}
 *      accessible via {@code AgentResponse.structuredOutput()}
 *
 * Fields on the schema class may be annotated with {@link OutputField} to
 * provide descriptions, examples, and required/optional status that are
 * reflected into the JSON schema prompt.
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.ANALYST, name = "SentimentAnalyser")
 * {@literal @}StructuredOutput(schema = SentimentReport.class)
 * public class SentimentAnalyserAgent {}
 *
 * public class SentimentReport {
 *   {@literal @}OutputField(description = "overall sentiment", example = "POSITIVE")
 *   public String sentiment;
 *
 *   {@literal @}OutputField(description = "confidence 0.0–1.0", example = "0.92")
 *   public double confidence;
 *
 *   {@literal @}OutputField(description = "one-sentence rationale")
 *   public String rationale;
 * }
 *
 * // Retrieve result:
 * AgentResponse r = ctx.submit("Analyse: 'Best product ever!'");
 * SentimentReport report = r.structuredOutput(SentimentReport.class);
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface StructuredOutput {

    /** The POJO class the LLM output should be deserialised into. */
    Class<?> schema();

    /**
     * Retry if the LLM returns JSON that cannot be parsed into {@code schema}.
     * The parse error is injected into the retry prompt so the model can self-correct.
     */
    boolean retryOnMalformed() default true;

    /** Maximum retries on malformed JSON before throwing {@link io.squados.exception.StructuredOutputException}. */
    int maxRetries() default 2;

    /**
     * Where in the system prompt to inject the JSON schema instructions.
     * APPEND (default) adds after the base prompt; PREPEND adds before.
     */
    SchemaInjection inject() default SchemaInjection.APPEND;

    enum SchemaInjection { APPEND, PREPEND }
}
