package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Pauses agent execution and waits for human approval before proceeding.
 *
 * When placed on a method, the framework:
 *   1. Captures the agent output (the "decision")
 *   2. Persists an ApprovalRequest to the ApprovalStore
 *   3. Notifies the escalation target (email/Slack/webhook — pluggable)
 *   4. BLOCKS until a human calls ApprovalStore.approve() or .reject()
 *   5. If approved: returns the decision to the caller
 *   6. If rejected: throws ApprovalRejectedException with reason
 *   7. If timed out: applies the timeout policy
 *
 * Usage:
 * <pre>
 * {@literal @}AwaitApproval(
 *     reason      = "Loan amount exceeds auto-approval threshold",
 *     timeoutHours = 24,
 *     onTimeout   = TimeoutPolicy.REJECT,
 *     escalateTo  = "senior-underwriter"
 * )
 * public LoanDecision underwriteLoan(LoanApplication app) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface AwaitApproval {
    /** Human-readable reason shown to the approver. */
    String reason() default "Agent decision requires human approval";

    /** Hours before the request times out. Default: 24h. */
    int timeoutHours() default 24;

    /** What to do when the request times out. */
    TimeoutPolicy onTimeout() default TimeoutPolicy.REJECT;

    /** Identifier of the approver — email, role, Slack channel. */
    String escalateTo() default "default-approver";

    /** Priority level shown to approver. */
    ApprovalPriority priority() default ApprovalPriority.NORMAL;
}