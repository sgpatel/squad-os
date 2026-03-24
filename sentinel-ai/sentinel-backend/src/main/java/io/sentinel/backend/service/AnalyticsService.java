package io.sentinel.backend.service;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final MentionRepository repo;
    public AnalyticsService(MentionRepository repo) { this.repo = repo; }

    public Map<String,Object> getSummary(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<MentionEntity> all = repo.findByPostedAtAfterOrderByPostedAtDesc(since);
        long total    = all.size();
        long positive = count(all, "POSITIVE");
        long negative = count(all, "NEGATIVE");
        long neutral  = count(all, "NEUTRAL");
        long critical = all.stream().filter(m -> "P1".equals(m.priority)).count();
        long pending  = all.stream().filter(m -> "PENDING".equals(m.replyStatus)).count();
        long openTkts = repo.findAll().stream().filter(m -> "OPEN".equals(m.ticketStatus)).count();
        long resvTkts = repo.findAll().stream().filter(m -> "RESOLVED".equals(m.ticketStatus)).count();
        double health = total == 0 ? 75.0 :
            Math.max(0, Math.min(100,
                50 + (positive - negative * 2.0) / Math.max(total, 1) * 50));
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("totalMentions",   total);
        r.put("positiveMentions",positive);
        r.put("negativeMentions",negative);
        r.put("neutralMentions", neutral);
        r.put("brandHealthScore",Math.round(health * 10.0) / 10.0);
        r.put("criticalAlerts",  critical);
        r.put("pendingReplies",  pending);
        r.put("openTickets",     openTkts);
        r.put("resolvedTickets", resvTkts);
        r.put("avgSentimentScore",
            all.stream().mapToDouble(m->m.sentimentScore).average().orElse(0.5));
        return r;
    }

    public List<Map<String,Object>> getSentimentTrend(int hours) {
        List<Map<String,Object>> trend = new ArrayList<>();
        for (int h = hours; h >= 0; h -= 2) {
            Instant from = Instant.now().minus(h, ChronoUnit.HOURS);
            Instant to   = Instant.now().minus(Math.max(0,h-2), ChronoUnit.HOURS);
            List<MentionEntity> bucket = repo.findAll().stream()
                .filter(m -> m.postedAt != null && m.postedAt.isAfter(from) && m.postedAt.isBefore(to))
                .toList();
            Map<String,Object> pt = new LinkedHashMap<>();
            pt.put("hour",     hours - h);
            pt.put("positive", count(bucket, "POSITIVE"));
            pt.put("negative", count(bucket, "NEGATIVE"));
            pt.put("neutral",  count(bucket, "NEUTRAL"));
            pt.put("total",    (long)bucket.size());
            trend.add(pt);
        }
        return trend;
    }

    public Map<String,Long> getCategoryBreakdown(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return repo.findByPostedAtAfterOrderByPostedAtDesc(since).stream()
            .filter(m -> m.topic != null)
            .collect(Collectors.groupingBy(m -> m.topic, Collectors.counting()));
    }

    public Map<String,Object> getBrandHealth() {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("score",  getSummary(24).get("brandHealthScore"));
        r.put("trend",  getSummary(1) .get("brandHealthScore"));
        r.put("last24h",getSummary(24));
        r.put("last1h", getSummary(1));
        return r;
    }

    private long count(List<MentionEntity> list, String label) {
        return list.stream().filter(m -> label.equals(m.sentimentLabel)).count();
    }
}