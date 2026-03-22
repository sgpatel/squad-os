package com.example.fraud.agents;

import io.squados.annotation.*;

/**
 * UnderwriterAgent — makes the final payment decision.
 *
 * Uses @AutoApproval for low-risk transactions (instant pass).
 * Uses @AwaitApproval for high-risk transactions (human review).
 * Listens for vote results via @OnMessage.
 */
@Agent(
    role        = AgentRole.SUPPORT,
    name        = "UnderwriterAgent",
    description = "You are a payment underwriter. You make final approve/reject decisions " +
        "based on the fraud assessment from the squad. " +
        "For low-risk transactions (score below 0.3): approve immediately. " +
        "For medium-risk (0.3-0.7): approve with monitoring flag. " +
        "For high-risk (above 0.7): reject or escalate to human. " +
        "Always provide a clear, specific reason for your decision. " +
        "Cite the specific risk factors that drove the decision."
)
public class UnderwriterAgent {

    @PostConstruct
    public void init() {
        System.out.println("[UnderwriterAgent] Underwriting engine online.");
    }

    @AutoApproval(
        condition    = "riskScore < 0.3 AND velocity < 5",
        reason       = "Low risk score and normal transaction velocity",
        rejectOnMatch = false
    )
    @AwaitApproval(
        reason       = "High-risk transaction requires human review",
        timeoutHours = 4,
        onTimeout    = TimeoutPolicy.REJECT,
        escalateTo   = "senior-fraud-analyst",
        priority     = ApprovalPriority.HIGH
    )
    @Traced(spanName = "underwriter-decision")
    public String makeDecision(String riskAssessmentSummary) {
        return riskAssessmentSummary;
    }

    @OnMessage(from = AgentRole.ANALYST, type = io.squados.bus.MessageType.DIRECTIVE)
    public void onRiskAssessment(io.squados.bus.AgentMessage msg) {
        System.out.println("[UnderwriterAgent] Risk assessment received: " + String.valueOf(msg.getPayload()));
    }

    @OnMessage(from = AgentRole.RESEARCHER, type = io.squados.bus.MessageType.DIRECTIVE)
    public void onBehaviourReport(io.squados.bus.AgentMessage msg) {
        System.out.println("[UnderwriterAgent] Behaviour report received: " + String.valueOf(msg.getPayload()));
    }
}