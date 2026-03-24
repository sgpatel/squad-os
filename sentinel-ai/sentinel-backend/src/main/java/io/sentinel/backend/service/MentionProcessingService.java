package io.sentinel.backend.service;

import io.sentinel.backend.agents.*;
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
import java.util.UUID;

@Service
public class MentionProcessingService {

    private final SquadContext         ctx;
    private final MentionRepository    repo;
    private final MentionWebSocketHandler ws;
    private final TicketConnectorService  tickets;
    private final InProcessFeedbackStore  feedback;

    @Value("${sentinel.auto-reply.enabled:true}")
    private boolean autoReplyEnabled;

    @Value("${sentinel.brand.name:Company}")
    private String brandName;

    public MentionProcessingService(SquadContext ctx, MentionRepository repo,
        MentionWebSocketHandler ws, TicketConnectorService tickets,
        InProcessFeedbackStore feedback) {
        this.ctx = ctx; this.repo = repo; this.ws = ws;
        this.tickets = tickets; this.feedback = feedback;
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

            // ── Step 1: Sentiment Analysis ──────────────────────
            String mentionCtx = buildMentionContext(mention);
            SentimentAgent.SentimentAnalysis sentiment = ctx.submitTo(
                AgentRole.ANALYST,
                "Analyse the sentiment of this social media mention:\n" + mentionCtx +
                "\nReturn structured JSON analysis.",
                SentimentAgent.SentimentAnalysis.class);

            applySentiment(mention, sentiment);

            // ── Step 2: Escalation Decision ─────────────────────
            EscalationAgent.EscalationDecision escalation = ctx.submitTo(
                AgentRole.CRITIC,
                "Determine escalation priority for:\n" + mentionCtx +
                "\nSentiment: " + mention.sentimentLabel +
                ", Urgency: " + mention.urgency +
                ", Author followers: " + mention.authorFollowers,
                EscalationAgent.EscalationDecision.class);

            applyEscalation(mention, escalation);

            // ── Step 3: Generate Reply ───────────────────────────
            if (autoReplyEnabled) {
                ReplyAgent.GeneratedReply reply = ctx.submitTo(
                    AgentRole.SUPPORT,
                    "Generate a reply for this " + mention.sentimentLabel + " mention:\n" + mentionCtx +
                    "\nBrand: " + brandName + ", Priority: " + mention.priority,
                    ReplyAgent.GeneratedReply.class);

                // Compliance check on reply
                ComplianceAgent.ComplianceReview compliance = ctx.submitTo(
                    AgentRole.CRITIC,
                    "Review this reply for brand compliance:\n" +
                    "Original mention: " + mention.text + "\n" +
                    "Proposed reply: " + (reply.replyText != null ? reply.replyText : ""),
                    ComplianceAgent.ComplianceReview.class);

                String finalReply = "true".equalsIgnoreCase(compliance.approved)
                    ? reply.replyText
                    : (compliance.revisedReply != null ? compliance.revisedReply : reply.replyText);

                mention.replyText = finalReply;
                mention.replyStatus = "PENDING"; // requires human approval
            }

            // ── Step 4: Create ticket for NEGATIVE mentions ──────
            if ("NEGATIVE".equals(mention.sentimentLabel) || "P1".equals(mention.priority) || "P2".equals(mention.priority)) {
                TicketAgent.TicketPayload ticketPayload = ctx.submitTo(
                    AgentRole.SUPPORT,
                    "Create a CRM ticket for:\n" + mentionCtx +
                    "\nSentiment: " + mention.sentimentLabel +
                    ", Priority: " + mention.priority +
                    ", Category: " + mention.topic,
                    TicketAgent.TicketPayload.class);

                String ticketId = tickets.createTicket(mention, ticketPayload);
                mention.ticketId = ticketId;
                mention.ticketStatus = "OPEN";
            }

            // ── Step 5: Record trace span ────────────────────────
            SquadTracer.getExporter().export(AgentSpan.builder("mention-processing")
                .agentRole(AgentRole.STRATEGIST)
                .agentName("MonitorAgent")
                .status(AgentSpan.Status.OK)
                .durationMs(System.currentTimeMillis() - start)
                .build());

            mention.processingStatus = "DONE";
            mention.updatedAt = Instant.now();
            repo.save(mention);
            ws.broadcast("mention.processed", mention);

            // Save to @Improve feedback store
            feedback.save(new FeedbackExample(
                "sentiment-analysis", mention.text,
                mention.sentimentLabel + ":" + mention.topic,
                FeedbackExample.Label.GOOD, "auto-processed"));

        } catch (Exception e) {
            mention.processingStatus = "ERROR";
            mention.updatedAt = Instant.now();
            repo.save(mention);
            ws.broadcast("mention.error", mention);
            System.err.println("[MentionProcessingService] Error: " + e.getMessage());
        }
        return mention;
    }

    private String buildMentionContext(MentionEntity m) {
        return "Platform: " + m.platform +
            "\nAuthor: @" + m.authorUsername + " (" + m.authorFollowers + " followers)" +
            "\nText: " + m.text +
            "\nPosted: " + m.postedAt +
            "\nLikes: " + m.likeCount + ", Retweets: " + m.retweetCount;
    }

    private void applySentiment(MentionEntity m, SentimentAgent.SentimentAnalysis s) {
        m.sentimentLabel = s.sentiment;
        try { m.sentimentScore = Double.parseDouble(s.score); } catch (Exception ignored) {}
        m.primaryEmotion = s.primaryEmotion;
        m.urgency = s.urgency;
        m.topic = s.topic;
        m.summary = s.summary;
        m.assignedTeam = s.suggestedTeam;
        m.urgencyScore = "CRITICAL".equals(s.urgency) ? 95 :
            "HIGH".equals(s.urgency) ? 75 :
            "MEDIUM".equals(s.urgency) ? 50 : 25;
    }

    private void applyEscalation(MentionEntity m, EscalationAgent.EscalationDecision e) {
        m.priority = e.priority;
        m.assignedTeam = e.escalationPath;
        m.isViral = "true".equalsIgnoreCase(e.isViralRisk);
        m.viralRiskScore = m.isViral ? 80 : 20;
    }
}