package io.squados.approval;

import io.squados.annotation.ApprovalPriority;
import io.squados.annotation.TimeoutPolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a pending approval request.
 * Persisted to ApprovalStore until approved, rejected, or timed out.
 */
public class ApprovalRequest {

    public enum Status { PENDING, APPROVED, REJECTED, TIMED_OUT, AUTO_APPROVED, AUTO_REJECTED }

    private final String          id;
    private final String          agentName;
    private final String          methodName;
    private final String          decision;      // stringified agent output
    private final String          reason;
    private final String          escalateTo;
    private final ApprovalPriority priority;
    private final TimeoutPolicy   onTimeout;
    private final Instant         createdAt;
    private final Instant         expiresAt;
    private volatile Status       status;
    private volatile String       reviewNote;
    private volatile Instant      reviewedAt;

    public ApprovalRequest(String agentName, String methodName,
                           String decision, String reason,
                           String escalateTo, ApprovalPriority priority,
                           TimeoutPolicy onTimeout, int timeoutHours) {
        this.id          = UUID.randomUUID().toString();
        this.agentName   = agentName;
        this.methodName  = methodName;
        this.decision    = decision;
        this.reason      = reason;
        this.escalateTo  = escalateTo;
        this.priority    = priority;
        this.onTimeout   = onTimeout;
        this.createdAt   = Instant.now();
        this.expiresAt   = createdAt.plusSeconds(timeoutHours * 3600L);
        this.status      = Status.PENDING;
    }

    // ── Accessors ─────────────────────────────────────────────────
    public String           getId()          { return id; }
    public String           getAgentName()   { return agentName; }
    public String           getMethodName()  { return methodName; }
    public String           getDecision()    { return decision; }
    public String           getReason()      { return reason; }
    public String           getEscalateTo()  { return escalateTo; }
    public ApprovalPriority getPriority()    { return priority; }
    public TimeoutPolicy    getOnTimeout()   { return onTimeout; }
    public Instant          getCreatedAt()   { return createdAt; }
    public Instant          getExpiresAt()   { return expiresAt; }
    public Status           getStatus()      { return status; }
    public String           getReviewNote()  { return reviewNote; }
    public Instant          getReviewedAt()  { return reviewedAt; }

    public boolean isPending()   { return status == Status.PENDING; }
    public boolean isExpired()   { return Instant.now().isAfter(expiresAt); }
    public boolean isTerminal()  {
        return status != Status.PENDING;
    }

    // ── State transitions (package-private) ───────────────────────
    void approve(String note) {
        this.status     = Status.APPROVED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    void reject(String note) {
        this.status     = Status.REJECTED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    void timeout() {
        this.status     = status == Status.PENDING ? Status.TIMED_OUT : status;
        this.reviewedAt = Instant.now();
    }

    void autoApprove(String note) {
        this.status     = Status.AUTO_APPROVED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    void autoReject(String note) {
        this.status     = Status.AUTO_REJECTED;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("ApprovalRequest{id=%s, agent=%s, status=%s, priority=%s}",
            id, agentName, status, priority);
    }
}