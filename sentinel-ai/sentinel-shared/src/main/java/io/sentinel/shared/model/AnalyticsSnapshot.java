package io.sentinel.shared.model;
import java.time.Instant;
import java.util.Map;
public class AnalyticsSnapshot {
    public Instant    timestamp;
    public String     handle;
    public long       totalMentions;
    public long       positiveMentions;
    public long       negativeMentions;
    public long       neutralMentions;
    public double     brandHealthScore;   // 0-100
    public double     avgSentimentScore;
    public long       openTickets;
    public long       resolvedTickets;
    public long       slaBreach;
    public long       criticalAlerts;
    public double     avgResponseTimeMs;
    public Map<String, Long>  categoryBreakdown;
    public Map<String, Long>  platformBreakdown;
    public Map<String, Long>  topKeywords;
    public double     viralRiskIndex;
    public long       pendingReplies;
}