package io.sentinel.backend.agents;
import io.squados.annotation.*;
import io.squados.bus.AgentMessage;
import io.squados.event.SquadEvent;

@Agent(
    role = AgentRole.STRATEGIST,
    name = "MonitorAgent",
    description = "You are the orchestration agent for SentinelAI — a social media monitoring system. " +
        "You receive incoming social media mentions and coordinate the analysis pipeline. " +
        "Triage mentions: route high-urgency to ANALYST immediately, " +
        "batch low-urgency for efficiency. " +
        "Summarise pipeline status when asked. " +
        "You are the single source of truth for mention processing state."
)
@MissionProfile("work")
public class MonitorAgent {

    @PostConstruct
    public void init() {
        System.out.println("[MonitorAgent] Social media monitoring pipeline online.");
        System.out.println("[MonitorAgent] Watching: @AirtelPaymentsBank");
    }

    @OnEvent(topic = "mention.incoming", concurrency = 10, retryOnError = true, maxRetries = 3)
    public void onMentionReceived(SquadEvent event) {
        System.out.println("[MonitorAgent] New mention received: " + event.getPayload().toString().substring(0, Math.min(80, event.getPayload().toString().length())));
    }

    @OnMessage(from = AgentRole.ANALYST, type = io.squados.bus.MessageType.DIRECTIVE)
    public void onAnalysisComplete(AgentMessage msg) {
        System.out.println("[MonitorAgent] Analysis complete: " + msg.getPayload());
    }

    @Delegate(
        candidates  = {AgentRole.ANALYST, AgentRole.RESEARCHER},
        strategy    = DelegateStrategy.FIRST_MATCH,
        conditions  = {"urgent OR critical OR fraud OR viral OR P1", "trend OR batch OR analytics OR pattern"},
        fallback    = AgentRole.ANALYST,
        logDecision = true
    )
    public String routeMention(String mentionContext) {
        return mentionContext;
    }
}