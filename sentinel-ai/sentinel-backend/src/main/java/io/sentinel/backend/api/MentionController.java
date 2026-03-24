package io.sentinel.backend.api;
import io.sentinel.backend.ingestion.MockMentionIngestionService;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import io.sentinel.backend.service.AnalyticsService;
import io.sentinel.backend.service.TicketConnectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MentionController {
    private final MentionRepository repo;
    private final AnalyticsService analytics;
    private final TicketConnectorService tickets;
    private final MockMentionIngestionService ingestion;
    public MentionController(MentionRepository repo, AnalyticsService analytics,
        TicketConnectorService tickets, MockMentionIngestionService ingestion) {
        this.repo = repo; this.analytics = analytics;
        this.tickets = tickets; this.ingestion = ingestion;
    }
    @GetMapping("/mentions")
    public List<MentionEntity> getMentions(
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(required = false) String sentiment,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String priority) {
        List<MentionEntity> all = repo.findAll();
        all.sort(Comparator.comparing((MentionEntity m) -> m.postedAt != null ? m.postedAt : Instant.EPOCH).reversed());
        if (sentiment != null) all.removeIf(m -> !sentiment.equalsIgnoreCase(m.sentimentLabel));
        if (status   != null) all.removeIf(m -> !status.equalsIgnoreCase(m.processingStatus));
        if (priority != null) all.removeIf(m -> !priority.equalsIgnoreCase(m.priority));
        return all.stream().limit(limit).toList();
    }
    @GetMapping("/mentions/{id}")
    public ResponseEntity<MentionEntity> getMention(@PathVariable String id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/mentions/ingest")
    public ResponseEntity<Map<String,String>> ingestMention(@RequestBody Map<String,Object> body) {
        String text    = (String) body.get("text");
        String author  = (String) body.getOrDefault("author", "test_user");
        long followers = body.containsKey("followers") ? Long.parseLong(body.get("followers").toString()) : 100L;
        ingestion.ingestCustomMention(text, author, followers);
        return ResponseEntity.ok(Map.of("status", "ingested", "message", "Processing in background"));
    }
    @PostMapping("/mentions/{id}/reply/approve")
    public ResponseEntity<MentionEntity> approveReply(@PathVariable String id) {
        return repo.findById(id).map(m -> {
            m.replyStatus = "APPROVED"; m.updatedAt = Instant.now();
            return ResponseEntity.ok(repo.save(m));
        }).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/mentions/{id}/reply/reject")
    public ResponseEntity<MentionEntity> rejectReply(@PathVariable String id,
        @RequestBody(required = false) Map<String,String> body) {
        return repo.findById(id).map(m -> {
            m.replyStatus = "REJECTED";
            if (body != null && body.containsKey("revisedReply")) m.replyText = body.get("revisedReply");
            m.updatedAt = Instant.now();
            return ResponseEntity.ok(repo.save(m));
        }).orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/analytics/summary")
    public Map<String, Object> getAnalyticsSummary(
        @RequestParam(defaultValue = "24") int hours) {
        return analytics.getSummary(hours);
    }
    @GetMapping("/analytics/trend")
    public List<Map<String,Object>> getSentimentTrend(
        @RequestParam(defaultValue = "24") int hours) {
        return analytics.getSentimentTrend(hours);
    }
    @GetMapping("/analytics/categories")
    public Map<String, Long> getCategoryBreakdown(
        @RequestParam(defaultValue = "24") int hours) {
        return analytics.getCategoryBreakdown(hours);
    }
    @GetMapping("/analytics/health")
    public Map<String, Object> getBrandHealth() {
        return analytics.getBrandHealth();
    }
    @GetMapping("/tickets")
    public List<TicketConnectorService.TicketRecord> getTickets() {
        return tickets.getAllTickets();
    }
    @PostMapping("/tickets/{id}/resolve")
    public ResponseEntity<Map<String,Object>> resolveTicket(@PathVariable String id,
        @RequestBody Map<String,String> body) {
        boolean ok = tickets.updateTicket(id, "RESOLVED", body.get("resolution"));
        if (!ok) return ResponseEntity.notFound().build();
        // Update related mention
        repo.findAll().stream().filter(m -> id.equals(m.ticketId)).findFirst().ifPresent(m -> {
            m.ticketStatus = "RESOLVED"; m.updatedAt = Instant.now(); repo.save(m);
        });
        return ResponseEntity.ok(Map.of("status", "resolved"));
    }
    @GetMapping("/pending-replies")
    public List<MentionEntity> getPendingReplies() {
        return repo.findByReplyStatusOrderByPostedAtDesc("PENDING");
    }
    @GetMapping("/alerts")
    public List<MentionEntity> getAlerts() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        return repo.findUrgentByHandle("@AirtelPaymentsBank", since).stream()
            .filter(m -> m.urgencyScore > 70 || "P1".equals(m.priority)).toList();
    }
}