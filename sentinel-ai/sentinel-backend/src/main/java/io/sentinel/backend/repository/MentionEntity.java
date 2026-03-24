package io.sentinel.backend.repository;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "mentions")
public class MentionEntity {
    @Id public String id;
    public String  platform;
    public String  handle;
    public String  authorUsername;
    public String  authorName;
    public long    authorFollowers;
    @Column(length = 2000) public String text;
    public String  language;
    public Instant postedAt;
    public Instant ingestedAt;
    public String  url;
    public long    likeCount;
    public long    retweetCount;
    public String  sentimentLabel;
    public double  sentimentScore;
    public String  primaryEmotion;
    public String  urgency;
    public String  topic;
    @Column(length = 500) public String summary;
    @Column(length = 20) public String priority;
    public String  escalationPath;
    public String  assignedTeam;
    public String  ticketId;
    public String  ticketSystem;
    public String  ticketStatus;
    @Column(length = 1000) public String replyText;
    public String  replyStatus;
    public String  processingStatus;
    public int     urgencyScore;
    public int     viralRiskScore;
    public boolean isViral;
    public Instant createdAt;
    public Instant updatedAt;
}