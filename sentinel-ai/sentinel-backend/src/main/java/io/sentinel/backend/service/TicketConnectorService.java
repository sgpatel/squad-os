package io.sentinel.backend.service;
import io.sentinel.backend.agents.TicketAgent;
import io.sentinel.backend.repository.MentionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
@Service
public class TicketConnectorService {
    @Value("${sentinel.ticket.system:MOCK}") private String ticketSystem;
    private final Map<String, TicketRecord> ticketStore = new ConcurrentHashMap<>();
    public record TicketRecord(String id, String title, String status, String priority, String team, String mentionId, String mentionText, String resolution, long createdAt) {}
    public String createTicket(MentionEntity mention, TicketAgent.TicketPayload payload) {
        String id = "TKT-" + System.currentTimeMillis();
        ticketStore.put(id, new TicketRecord(id,
            payload.title != null ? payload.title : "Issue: " + mention.topic,
            "OPEN", payload.priority != null ? payload.priority : "P3",
            payload.category != null ? payload.category : mention.assignedTeam,
            mention.id, mention.text, null, System.currentTimeMillis()));
        System.out.println("[TicketConnector] Created ticket " + id + " in " + ticketSystem);
        return id;
    }
    public boolean updateTicket(String id, String status, String resolution) {
        TicketRecord t = ticketStore.get(id);
        if (t == null) return false;
        ticketStore.put(id, new TicketRecord(t.id(), t.title(), status, t.priority(), t.team(), t.mentionId(), t.mentionText(), resolution, t.createdAt()));
        return true;
    }
    public List<TicketRecord> getAllTickets() { return new ArrayList<>(ticketStore.values()); }
    public TicketRecord getTicket(String id) { return ticketStore.get(id); }
    public Map<String, Long> getStatusCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        ticketStore.values().forEach(t -> counts.merge(t.status(), 1L, Long::sum));
        return counts;
    }
}