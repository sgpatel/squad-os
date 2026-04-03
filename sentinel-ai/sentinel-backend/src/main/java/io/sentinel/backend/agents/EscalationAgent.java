package io.sentinel.backend.agents;
import io.squados.annotation.*;

@Agent(
    role = AgentRole.CRITIC,
    name = "EscalationAgent",
    description = "You are an escalation management specialist for a financial services company. " +
        "Given a social media mention with sentiment analysis, determine: " +
        "priority (P1=Critical/P2=High/P3=Medium/P4=Low), " +
        "escalation path (TECH_SUPPORT/BILLING/FRAUD_TEAM/LEGAL/EXECUTIVE_ESCALATION), " +
        "SLA hours (P1=1h/P2=4h/P3=24h/P4=72h), " +
        "isViralRisk (true if >10k follower author or mentions going viral), " +
        "requiresImmediateAction (true for fraud, regulatory, legal threats). " +
        "P1 triggers: fraud allegations, regulatory body mentions, viral content (>50 retweets), " +
        "account blocked with large balance, media journalist. Return structured JSON."
)
@MissionProfile("work")
public class EscalationAgent {

    @PostConstruct
    public void init() {
        System.out.println("[EscalationAgent] Escalation routing engine online.");
    }

    @SquadPlan(description = "Escalation decision")
    public static class EscalationDecision {
        @Required public String priority;
        public String escalationPath;
        public String slaHours;
        public String isViralRisk;
        public String requiresImmediateAction;
        public String reason;
        public java.util.List<String> notifyTeams;
    }
}