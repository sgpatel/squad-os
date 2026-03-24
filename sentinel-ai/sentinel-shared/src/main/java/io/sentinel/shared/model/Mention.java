package io.sentinel.shared.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class Mention {
    public String      id;
    public String      platform;        // TWITTER, LINKEDIN, INSTAGRAM
    public String      handle;          // @AirtelPaymentsBank
    public String      authorUsername;
    public String      authorName;
    public String      authorProfileUrl;
    public long        authorFollowers;
    public String      text;
    public String      originalText;    // before cleanup
    public String      language;        // en, hi, ta
    public Instant     postedAt;
    public Instant     ingestedAt;
    public String      url;
    public long        likeCount;
    public long        replyCount;
    public long        retweetCount;
    public List<String> hashtags;
    public List<String> mentions;
    public SentimentResult sentiment;
    public TicketInfo  ticket;
    public String      replyText;
    public String      replyStatus;     // PENDING / APPROVED / POSTED / SKIPPED
    public String      processingStatus; // NEW / ANALYSING / DONE / ERROR
    public String      assignedTeam;
    public MentionCategory category;
    public int         urgencyScore;    // 0-100
    public int         viralRiskScore;  // 0-100
    public boolean     isViral;
    public String      parentMentionId; // for thread tracking
}