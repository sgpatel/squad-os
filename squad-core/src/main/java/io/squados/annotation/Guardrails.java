package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Attach a pluggable safety/compliance filter pipeline to this agent.
 *
 * filters     — Ordered list of GuardrailFilter implementation classes to run
 * inputCheck  — Apply filters to the user input (before LLM call)
 * outputCheck — Apply filters to the LLM response (after LLM call)
 *
 * Built-in filters (in guardrail.filter package):
 *   PiiDetector, PromptInjectionDetector, ToxicityFilter,
 *   HallucinationDetector, GroundingFilter, SensitiveTopicFilter,
 *   RegulatoryComplianceFilter, ConfidentialDataFilter
 *
 * Violations are recorded in GuardrailAuditLog and may throw GuardrailException.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Guardrails {
    Class<?>[] filters()      default {};
    boolean    inputCheck()   default true;
    boolean    outputCheck()  default true;
}
