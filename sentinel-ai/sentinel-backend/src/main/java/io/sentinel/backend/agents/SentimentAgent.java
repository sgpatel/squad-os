package io.sentinel.backend.agents;
import io.squados.annotation.*;
import io.sentinel.shared.model.*;

@Agent(
    role = AgentRole.ANALYST,
    name = "SentimentAgent",
    description = "You are an expert social media sentiment analyst for a financial services company. " +
        "Analyse the given social media mention and return a structured JSON analysis with: " +
        "sentiment (POSITIVE/NEGATIVE/NEUTRAL), score (0.0-1.0), " +
        "primaryEmotion (FRUSTRATION/ANGER/SADNESS/JOY/SURPRISE/FEAR/NEUTRAL/SARCASM), " +
        "urgency (LOW/MEDIUM/HIGH/CRITICAL), " +
        "topic (PAYMENT_FAILURE/APP_CRASH/SLOW_PERFORMANCE/WRONG_CHARGE/KYC_ISSUE/ACCOUNT_BLOCKED/" +
        "FRAUD_REPORT/GENERAL_COMPLAINT/FEATURE_REQUEST/COMPLIMENT/QUERY/REGULATORY/OTHER), " +
        "summary (one-line summary max 100 chars), " +
        "keywords (list of 3-5 key terms), " +
        "requiresHumanReview (true if ambiguous or high stakes), " +
        "suggestedTeam (TECH_SUPPORT/BILLING/ESCALATIONS/CUSTOMER_CARE). " +
        "Be precise. Consider context, sarcasm, and implicit complaints. " +
        "CRITICAL mentions: fraud reports, regulatory complaints, viral threats. " +
        "Return ONLY valid JSON, no preamble."
)
@MissionProfile("work")
public class SentimentAgent {

    @PostConstruct
    public void init() {
        System.out.println("[SentimentAgent] Multi-dimensional sentiment engine online.");
    }

    @SquadPlan(description = "Structured sentiment analysis result")
    public static class SentimentAnalysis {
        @Required public String sentiment;
        public String score;
        public String primaryEmotion;
        public String urgency;
        public String topic;
        public String summary;
        public java.util.List<String> keywords;
        public String requiresHumanReview;
        public String suggestedTeam;
    }
}