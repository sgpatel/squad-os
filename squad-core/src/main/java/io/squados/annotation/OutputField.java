package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Documents a field in a {@link StructuredOutput} schema class.
 *
 * Used by {@link io.squados.structured.JsonSchemaGenerator} to enrich
 * the JSON schema injected into the agent system prompt.
 *
 * Usage:
 * <pre>
 * public class RiskReport {
 *   {@literal @}OutputField(description = "risk level", example = "HIGH", required = true)
 *   public String riskLevel;
 *
 *   {@literal @}OutputField(description = "score from 0.0 to 1.0", example = "0.87")
 *   public double score;
 *
 *   {@literal @}OutputField(description = "brief explanation", required = false)
 *   public String rationale;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface OutputField {

    /** Human-readable description of what this field should contain. */
    String description() default "";

    /** Example value shown to the LLM in the schema prompt. */
    String example() default "";

    /** Whether this field is required in the LLM output. Defaults to true. */
    boolean required() default true;
}
