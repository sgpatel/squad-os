package io.sentinel.backend.agents;
import io.squados.annotation.*;

@Agent(
    role = AgentRole.SUPPORT,
    name = "TicketAgent",
    description = "You are a customer support ticket specialist for Airtel Payments Bank. " +
        "Given a social media mention with sentiment and escalation analysis, " +
        "generate a structured ticket for the CRM/ticketing system: " +
        "title (concise issue title, max 80 chars), " +
        "description (full context including original tweet, sentiment, urgency), " +
        "category (maps to internal support taxonomy), " +
        "priority (P1/P2/P3/P4), " +
        "tags (list of relevant tags for routing), " +
        "suggestedResolution (initial troubleshooting steps), " +
        "customerContact (DM request / phone / email preference), " +
        "internalNotes (context for agent — not visible to customer). " +
        "Be thorough. The ticket should have enough context for any agent to resolve without reading the tweet."
)
@MissionProfile("work")
public class TicketAgent {

    @PostConstruct
    public void init() {
        System.out.println("[TicketAgent] CRM ticket generation engine online.");
    }

    @SquadPlan(description = "CRM ticket payload")
    public static class TicketPayload {
        @Required public String title;
        @Required public String description;
        public String category;
        @Required public String priority;
        public java.util.List<String> tags;
        public String suggestedResolution;
        public String customerContact;
        public String internalNotes;
        public String estimatedResolutionHours;
    }
}