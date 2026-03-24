package io.sentinel.backend.agents;
import io.squados.annotation.*;

@Agent(
    role = AgentRole.SUPPORT,
    name = "ReplyAgent",
    description = "You are a customer service reply specialist for Airtel Payments Bank. " +
        "Generate a social media reply (Twitter/X) for the given mention. Rules: " +
        "1) Always be empathetic, professional, and solution-focused. " +
        "2) NEGATIVE mentions: Acknowledge the issue, apologise sincerely, " +
        "   offer resolution path (DM/support channel), do NOT make promises. " +
        "3) POSITIVE mentions: Thank warmly, reinforce brand value. " +
        "4) NEUTRAL/QUERY mentions: Answer helpfully or redirect to support. " +
        "5) Keep reply under 280 characters for Twitter. " +
        "6) Include relevant hashtags if appropriate (#AirtelPaymentsBank). " +
        "7) For P1/fraud issues: escalate tone, provide direct hotline reference. " +
        "8) Never reveal internal systems, ticket IDs, or employee names. " +
        "9) End with a call-to-action when appropriate. " +
        "Return JSON with: replyText, replyTone (APOLOGETIC/THANKFUL/HELPFUL/URGENT), " +
        "includesCallToAction (bool), estimatedEngagementScore (0-10)."
)
@MissionProfile("work")
public class ReplyAgent {

    @PostConstruct
    public void init() {
        System.out.println("[ReplyAgent] Brand response engine online.");
    }

    @Improve(label = "reply-quality", topK = 5, minExamples = 3, includeNegativeExamples = true)
    public String generateReply(String mentionContext) {
        return mentionContext;
    }

    @SquadPlan(description = "Generated reply result")
    public static class GeneratedReply {
        @Required public String replyText;
        public String replyTone;
        public String includesCallToAction;
        public String estimatedEngagementScore;
        public String alternativeReply;
    }
}