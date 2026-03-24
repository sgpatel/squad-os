package io.sentinel.shared.model;
import java.util.List;
import java.util.Map;
public class SentimentResult {
    public SentimentLabel label;         // POSITIVE / NEGATIVE / NEUTRAL
    public double         score;         // 0.0 - 1.0 confidence
    public EmotionLabel   primaryEmotion; // FRUSTRATION / JOY / ANGER / SADNESS...
    public double         emotionScore;
    public UrgencyLevel   urgency;       // LOW / MEDIUM / HIGH / CRITICAL
    public String         topic;         // PAYMENT_FAILURE / APP_ISSUE / BILLING...
    public String         summary;       // one-line AI summary
    public List<String>   keywords;
    public Map<String,Double> topicScores; // multi-label topic probabilities
    public boolean        requiresHumanReview;
    public String         suggestedTeam; // TECH_SUPPORT / BILLING / ESCALATIONS
}