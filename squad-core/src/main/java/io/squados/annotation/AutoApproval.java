package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Automatically approves or rejects an agent decision based on rules.
 * No human involvement — the framework evaluates conditions at runtime.
 *
 * Rules are SpEL-like expressions evaluated against the method return value.
 * If the condition passes: auto-approved, execution continues immediately.
 * If the condition fails: falls through to {@literal @}AwaitApproval if present,
 *   or throws AutoApprovalFailedException if not.
 *
 * Usage:
 * <pre>
 * // Auto-approve loans under £10,000 with risk score below 0.3
 * {@literal @}AutoApproval(
 *     condition  = "amount &lt; 10000 AND riskScore &lt; 0.3",
 *     reason     = "Within auto-approval limits"
 * )
 * {@literal @}AwaitApproval(reason = "Exceeds auto-approval threshold")
 * public LoanDecision underwriteLoan(LoanApplication app) { ... }
 * </pre>
 *
 * When both annotations are present:
 *   condition passes  -> auto-approved, skip @AwaitApproval entirely
 *   condition fails   -> falls through to @AwaitApproval for human review
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface AutoApproval {
    /**
     * Condition expression evaluated against the result object.
     * Supported operators: AND, OR, NOT, <, >, <=, >=, ==, !=
     * Supported field access: result.fieldName
     * Example: "amount &lt; 10000 AND riskScore &lt; 0.3"
     */
    String condition();

    /** Reason logged when auto-approved. */
    String reason() default "Auto-approved by policy";

    /** Reason logged when auto-rejected (condition evaluates to false for reject). */
    String rejectReason() default "Auto-rejected by policy";

    /**
     * If true: reject when condition is true (block-list mode).
     * If false (default): approve when condition is true (allow-list mode).
     */
    boolean rejectOnMatch() default false;
}