package io.sentinel.backend.agents;
import io.squados.annotation.*;

@Agent(
    role = AgentRole.RESEARCHER,
    name = "TrendAgent",
    description = "You are a social media trend analyst. Given a batch of recent mentions, " +
        "identify: emerging topic clusters, sentiment shifts (improving/worsening), " +
        "spike detection (sudden volume increase), viral risk assessment, " +
        "recurring complaint patterns, and brand health trend (0-100 score). " +
        "Return structured JSON analysis. Focus on actionable insights."
)
@MissionProfile("work")
public class TrendAgent {

    @PostConstruct
    public void init() {
        System.out.println("[TrendAgent] Trend detection engine online.");
    }

    @SquadPlan(description = "Trend analysis result")
    public static class TrendAnalysis {
        public java.util.List<String> emergingTopics;
        public String                 sentimentTrend;  // IMPROVING/WORSENING/STABLE
        public String                 brandHealthScore; // 0-100
        public java.util.List<String> recurringComplaints;
        public String                 viralRiskLevel;  // LOW/MEDIUM/HIGH
        public String                 actionRequired;
        public String                 summary;
    }
}