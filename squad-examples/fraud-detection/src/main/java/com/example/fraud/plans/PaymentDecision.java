package com.example.fraud.plans;
import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;

/** Final payment decision from UnderwriterAgent. */
@SquadPlan(description = "Final payment approval or rejection decision")
public class PaymentDecision {
    @Required public String transactionId;
    @Required public String decision;       // "APPROVED" / "REJECTED" / "PENDING_REVIEW"
    @Required public String reason;
    public String           policyApplied;  // which rule triggered
    public String           reviewAssignedTo; // escalation target if PENDING_REVIEW
    public boolean          appealAllowed;
}