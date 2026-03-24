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
    private final TicketConnectorService tickets;
    public AnalyticsService(MentionRepository repo, TicketConnectorService tickets) {
        this.repo = repo; this.tickets = tickets;
    }
    public Map<String, Object> getSummary(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<MentionEntity> all = repo.findByPostedAtAfterOrderByPostedAtDesc(since);
        long total   = all.size();
        long positive = all.stream().filter(m -> "POSITIVE".equals(m.sentimentLabel)).count();
        long negative = all.stream().filter(m -> "NEGATIVE".equals(m.sentimentLabel)).count();
        long neutral  = all.stream().filter(m -> "NEUTRAL".equals(m.sentimentLabel)).count();
        long critical = all.stream().filter(m -> "P1".equals(m.priority)).count();
        long pending  = all.stream().filter(m -> "PENDING".equals(m.replyStatus)).count();
        double healthScore = total == 0 ? 75.0 :
            Math.max(0, Math.min(100, 50 + (positive - negative * 2) * 10.0 / Math.max(total, 1)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMentions", total);
        result.put("positiveMentions", positive);
        result.put("negativeMentions", negative);
        result.put("neutralMentions", neutral);
        result.put("brandHealthScore", Math.round(healthScore * 10.0) / 10.0);
        result.put("criticalAlerts", critical);
        result.put("pendingReplies", pending);
        result.put("openTickets", tickets.getAllTickets().stream().filter(t -> "OPEN".equals(t.status())).count());
        result.put("resolvedTickets", tickets.getAllTickets().stream().filter(t -> "RESOLVED".equals(t.status())).count());
        result.put("avgSentimentScore", all.stream().mapToDouble(m->m.sentimentScore).average().orElse(0.5));
        return result;
    }
    public List<Map<String,Object>> getSentimentTrend(int hours) {
        List<Map<String,Object>> trend = new ArrayList<>();
        for (int h = hours; h >= 0; h -= 2) {
            Instant from = Instant.now().minus(h, ChronoUnit.HOURS);
            Instant to   = Instant.now().minus(Math.max(0, h-2), ChronoUnit.HOURS);
            List<MentionEntity> bucket = repo.findAll().stream()
                .filter(m -> m.postedAt != null && m.postedAt.isAfter(from) && m.postedAt.isBefore(to))
                .toList();
            Map<String,Object> point = new LinkedHashMap<>();
            point.put("hour", hours - h);
            point.put("positive", bucket.stream().filter(m->"POSITIVE".equals(m.sentimentLabel)).count());
            point.put("negative", bucket.stream().filter(m->"NEGATIVE".equals(m.sentimentLabel)).count());
            point.put("neutral",  bucket.stream().filter(m->"NEUTRAL".equals(m.sentimentLabel)).count());
            point.put("total", bucket.size());
            trend.add(point);
        }
        return trend;
    }
    public Map<String, Long> getCategoryBreakdown(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return repo.findByPostedAtAfterOrderByPostedAtDesc(since).stream()
            .filter(m -> m.topic != null)
            .collect(Collectors.groupingBy(m -> m.topic, Collectors.counting()));
    }
    public Map<String, Object> getBrandHealth() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("score", getSummary(24).get("brandHealthScore"));
        r.put("trend", getSummary(1).get("brandHealthScore"));
        r.put("last24h", getSummary(24));
        r.put("last1h",  getSummary(1));
        return r;
    }
}