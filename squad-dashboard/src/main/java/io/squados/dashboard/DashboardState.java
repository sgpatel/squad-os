package io.squados.dashboard;

import io.squados.approval.ApprovalRequest;
import io.squados.approval.InProcessApprovalStore;
import io.squados.improve.FeedbackExample;
import io.squados.improve.InProcessFeedbackStore;
import io.squados.security.AuditLog;
import io.squados.trace.AgentSpan;
import io.squados.trace.RedisTraceExporter;

import io.squados.vote.VoteResult;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared live state for the SquadOS Dashboard.
 * Holds references to all monitoring stores.
 */
@Component
public class DashboardState {

    private final io.squados.trace.RedisTraceExporter   traceExporter;
    private final InProcessApprovalStore  approvalStore;
    private final InProcessFeedbackStore  feedbackStore;
    private final AuditLog                auditLog;

    // Live vote history
    private final List<VoteResult> voteHistory = new CopyOnWriteArrayList<>();

    // Live activity feed
    private final List<ActivityEntry> activityFeed = new CopyOnWriteArrayList<>();

    public record ActivityEntry(Instant timestamp, String type, String agent, String message) {}

    public DashboardState(RedisTraceExporter traceExporter,
                          InProcessApprovalStore approvalStore,
                          InProcessFeedbackStore feedbackStore,
                          AuditLog auditLog) {
        this.traceExporter = traceExporter;
        this.approvalStore = approvalStore;
        this.feedbackStore = feedbackStore;
        this.auditLog      = auditLog;
    }

    public void recordVote(VoteResult result) {
        voteHistory.add(0, result); // newest first
        if (voteHistory.size() > 50) voteHistory.remove(voteHistory.size() - 1);
    }

    public void recordActivity(String type, String agent, String message) {
        activityFeed.add(0, new ActivityEntry(Instant.now(), type, agent, message));
        if (activityFeed.size() > 100) activityFeed.remove(activityFeed.size() - 1);
    }

    public List<AgentSpan>         getSpans()        { return traceExporter.getSpans(); }
    public List<ApprovalRequest>   getApprovals()    { return approvalStore.findAll(); }
    public List<io.squados.improve.FeedbackExample> getFeedback() {
        // Collect from all known labels
        java.util.List<io.squados.improve.FeedbackExample> all = new java.util.ArrayList<>();
        for (String label : java.util.List.of("fraud-risk-assessment", "loan-check", "loan-underwriting", "fraud-check")) {
            all.addAll(feedbackStore.findAll(label));
        }
        return all;
    }
    public List<AuditLog.AuditEntry> getAuditLog()  { return auditLog.getAll(); }
    public List<VoteResult>        getVoteHistory()  { return Collections.unmodifiableList(voteHistory); }
    public List<ActivityEntry>     getActivityFeed() { return Collections.unmodifiableList(activityFeed); }
    public long                    getTotalTokens()  { return traceExporter.totalTokens(); }
    public double                  getAvgLatency()   { return traceExporter.avgDurationMs(); }
    public int                     getErrorCount()   { return traceExporter.getErrors().size(); }
}