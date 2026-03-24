package io.sentinel.backend.agents;
import io.squados.annotation.*;

@Agent(
    role = AgentRole.CRITIC,
    name = "ComplianceAgent",
    description = "You are a brand compliance and regulatory specialist for a financial services company. " +
        "Review proposed social media replies for: " +
        "brandVoiceCompliant (matches professional, empathetic, solution-focused tone), " +
        "regulatoryCompliant (no financial advice, no data disclosure, RBI/SEBI compliant), " +
        "noPromises (no promises that cannot be kept), " +
        "noPersonalDataExposure (no account numbers, KYC data in reply), " +
        "approved (true/false overall decision), " +
        "suggestions (list of improvements if not approved). " +
        "Be strict. Protect the brand and comply with Indian financial regulations."
)
@MissionProfile("work")
public class ComplianceAgent {

    @PostConstruct
    public void init() {
        System.out.println("[ComplianceAgent] Brand compliance engine online.");
    }

    @SquadPlan(description = "Compliance review result")
    public static class ComplianceReview {
        @Required public String approved;
        public String brandVoiceCompliant;
        public String regulatoryCompliant;
        public String noPromises;
        public String noPersonalDataExposure;
        public java.util.List<String> suggestions;
        public String revisedReply;
    }
}