package io.squados.annotation;
/** What happens when votes are exactly tied. */
public enum TieBreaker {
    /** Escalate to human via @AwaitApproval. */
    ESCALATE,
    /** Reject the decision on tie. */
    REJECT,
    /** Approve the decision on tie. */
    APPROVE,
    /** Return null — no decision made. */
    ABSTAIN
}