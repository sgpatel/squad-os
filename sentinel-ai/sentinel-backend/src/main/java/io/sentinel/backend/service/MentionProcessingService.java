package io.sentinel.backend.service;
import io.sentinel.backend.agents.*;
import io.sentinel.backend.connector.TicketConnectorFactory;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import io.sentinel.backend.websocket.MentionWebSocketHandler;
import io.squados.annotation.AgentRole;
import io.squados.context.SquadContext;
import io.squados.improve.FeedbackExample;
import io.squados.improve.InProcessFeedbackStore;
import io.squados.trace.AgentSpan;
import io.squados.trace.SquadTracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class MentionProcessingService {

    private final SquadContext            ctx;
    private final MentionRepository       repo;
    private final MentionWebSocketHandler ws;
    private final TicketConnectorFactory  ticketFactory;
    private final InProcessFeedbackStore  feedback;

    @Value("${sentinel.auto-reply.enabled:true}") private boolean autoReplyEnabled;
    @Value("${sentinel.brand.name:Company}")       private String brandName;

    public MentionProcessingService(SquadContext ctx, MentionRepository repo,
        MentionWebSocketHandler ws, TicketConnectorFactory ticketFactory,
        InProcessFeedbackStore feedback) {
        this.ctx = ctx; this.repo = repo; this.ws = ws;
        this.ticketFactory = ticketFactory; this.feedback = feedback;
    }

    public MentionEntity process(MentionEntity mention) {
        mention.ingestedAt = Instant.now();
        mention.processingStatus = "ANALYSING";
        mention.createdAt = Instant.now();
        mention.updatedAt = Instant.now();
        repo.save(mention);
        ws.broadcast("mention.new", mention);

        try {
            long start = System.currentTimeMillis();
            String mentionCtx = buildContext(mention);

            // Step 1 — Sentiment analysis
            SentimentAgent.SentimentAnalysis sentiment = ctx.submitTo(
                AgentRole.ANALYST,
                "Analyse the sentiment of this social media mention:\n" + mentionCtx +
                "\nReturn ONLY valid JSON. No preamble.",
                SentimentAgent.SentimentAnalysis.class);
            applySentiment(mention, sentiment);

            // Step 2 — Escalation priority
            EscalationAgent.EscalationDecision escalation = ctx.submitTo(
                AgentRole.CRITIC,
                "Determine escalation for:\n" + mentionCtx +
                "\nSentiment: " + mention.sentimentLabel +
                ", Urgency: " + mention.urgency +
                ", Followers: " + mention.authorFollowers +
                "\nReturn ONLY valid JSON.",
                EscalationAgent.EscalationDecision.class);
            applyEscalation(mention, escalation);

            // Step 3 — Generate reply (all sentiments)
            if (autoReplyEnabled) {
                ReplyAgent.GeneratedReply reply = ctx.submitTo(
                    AgentRole.SUPPORT,
                    "Generate a reply for this " + mention.sentimentLabel + " mention:\n" +
                    mentionCtx + "\nBrand: " + brandName +
                    ", Priority: " + mention.priority +
                    "\nReturn ONLY valid JSON.",
                    ReplyAgent.GeneratedReply.class);

                // Step 3b — Compliance check
                ComplianceAgent.ComplianceReview compliance = ctx.submitTo(
                    AgentRole.CRITIC,
                    "Review this reply for brand compliance:\n" +
                    "Mention: " + mention.text + "\nProposed reply: " +
                    (reply.replyText != null ? reply.replyText : "") +
                    "\nReturn ONLY valid JSON.",
                    ComplianceAgent.ComplianceReview.class);

                mention.replyText = "true".equalsIgnoreCase(compliance.approved)
                    ? reply.replyText
                    : (compliance.revisedReply != null ? compliance.revisedReply : reply.replyText);
                mention.replyStatus = "PENDING";
            }

            // Step 4 — Create ticket for NEGATIVE / P1 / P2
            if ("NEGATIVE".equals(mention.sentimentLabel)
                || "P1".equals(mention.priority) || "P2".equals(mention.priority)) {
                TicketAgent.TicketPayload tp = ctx.submitTo(
                    AgentRole.SUPPORT,
                    "Create a CRM ticket for:\n" + mentionCtx +
                    "\nSentiment: " + mention.sentimentLabel +
                    ", Priority: " + mention.priority +
                    ", Category: " + mention.topic +
                    "\nReturn ONLY valid JSON.",
                    TicketAgent.TicketPayload.class);

                String ticketId = ticketFactory.get().createTicket(mention, tp);
                if (ticketId != null) {
                    mention.ticketId     = ticketId;
                    mention.ticketSystem = ticketFactory.get().getName();
                    mention.ticketStatus = "OPEN";
                }
            }

            // Step 5 — Trace span
            SquadTracer.getExporter().export(AgentSpan.builder("mention-processing")
                .agentRole(AgentRole.STRATEGIST).agentName("MonitorAgent")
                .status(AgentSpan.Status.OK)
                .durationMs(System.currentTimeMillis() - start)
                .build());

            mention.processingStatus = "DONE";
            mention.updatedAt = Instant.now();
            repo.save(mention);
            ws.broadcast("mention.processed", mention);

            feedback.save(new FeedbackExample(
                "sentiment-analysis", mention.text,
                mention.sentimentLabel + ":" + mention.topic,
                FeedbackExample.Label.GOOD, "auto"));

        } catch (Exception e) {
            mention.processingStatus = "ERROR";
            mention.updatedAt = Instant.now();
            repo.save(mention);
            ws.broadcast("mention.error", mention);
            System.err.println("[MentionService] Error processing " + mention.id + ": " + e.getMessage());
        }
        return mention;
    }

    private String buildContext(MentionEntity m) {
        return "Platform: " + m.platform +
            "\nAuthor: @" + m.authorUsername + " (" + m.authorFollowers + " followers)" +
            "\nText: " + m.text +
            "\nLikes: " + m.likeCount + ", Retweets: " + m.retweetCount +
            "\nPosted: " + m.postedAt;
    }

    private void applySentiment(MentionEntity m, SentimentAgent.SentimentAnalysis s) {
        m.sentimentLabel = s.sentiment;
        try { m.sentimentScore = Double.parseDouble(s.score); } catch (Exception ignored) {}
        m.primaryEmotion = s.primaryEmotion;
        m.urgency        = s.urgency;
        m.topic          = s.topic;
        m.summary        = s.summary;
        m.assignedTeam   = s.suggestedTeam;
        m.urgencyScore   = "CRITICAL".equals(s.urgency) ? 95 :
                           "HIGH".equals(s.urgency)     ? 75 :
                           "MEDIUM".equals(s.urgency)   ? 50 : 25;
    }

    private void applyEscalation(MentionEntity m, EscalationAgent.EscalationDecision e) {
        // Normalize LLM output to P1/P2/P3/P4 regardless of what the model returns
        m.priority = normalizePriority(e.priority);
        m.assignedTeam  = e.escalationPath;
        m.isViral       = "true".equalsIgnoreCase(e.isViralRisk);
        m.viralRiskScore = m.isViral ? 80 : 20;
    }

    private String normalizePriority(String raw) {
        if (raw == null) return "P3";
        return switch (raw.toUpperCase().trim()) {
            case "P1", "CRITICAL", "URGENT"  -> "P1";
            case "P2", "HIGH"                -> "P2";
            case "P3", "MEDIUM", "NORMAL"    -> "P3";
            case "P4", "LOW"                 -> "P4";
            default -> raw.startsWith("P") && raw.length() == 2 ? raw : "P3";
        };
    }
}