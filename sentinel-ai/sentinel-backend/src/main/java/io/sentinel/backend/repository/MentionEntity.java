package io.sentinel.backend.repository;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mentions")
public class MentionEntity {

    @Id
    public String id;

    public String platform;
    public String handle;
    public String tenantId = "default";   // default — set by ingestion service

    public String authorUsername;
    public String authorName;
    public long   authorFollowers;

    @Column(columnDefinition = "TEXT")
    public String text;

    public String language;
    public Instant postedAt;
    public Instant ingestedAt;

    @Column(length = 1000)
    public String url;

    public long likeCount;
    public long retweetCount;

    // ── AI Analysis ───────────────────────────────────────────────
    public String  sentimentLabel;   // POSITIVE | NEGATIVE | NEUTRAL
    public double  sentimentScore;   // 0.0 – 1.0
    public String  primaryEmotion;   // FRUSTRATION | JOY | ANGER | ...
    public String  urgency;          // LOW | MEDIUM | HIGH | CRITICAL

    @Column(length = 500)
    public String topic;             // widened: LLM may return multiple topics joined by /

    @Column(length = 1000)
    public String summary;

    @Column(length = 20)
    public String priority;          // P1 | P2 | P3 | P4

    @Column(length = 500)
    public String escalationPath;

    public String  assignedTeam;
    public boolean isViral;
    public int     viralRiskScore;
    public int     urgencyScore;

    // ── Reply ─────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    public String replyText;

    public String replyStatus;       // PENDING | APPROVED | REJECTED | POSTED

    // ── Ticket ────────────────────────────────────────────────────
    public String ticketId;
    public String ticketStatus;
    public String ticketSystem;

    // ── Metadata ──────────────────────────────────────────────────
    public String  processingStatus; // NEW | ANALYSING | DONE | ERROR
    public Instant createdAt;
    public Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
