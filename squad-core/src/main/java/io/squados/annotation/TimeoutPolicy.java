package io.squados.annotation;
/** What happens when an @AwaitApproval request times out. */
public enum TimeoutPolicy {
    /** Reject the decision — safest default for regulated decisions. */
    REJECT,
    /** Approve the decision — use only for low-risk workflows. */
    APPROVE,
    /** Escalate to a higher authority and reset the timeout. */
    ESCALATE
}