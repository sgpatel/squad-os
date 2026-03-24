package io.sentinel.backend.connector;
import io.sentinel.backend.agents.TicketAgent;
import io.sentinel.backend.repository.MentionEntity;
public interface TicketConnector {
    String getName();
    boolean isEnabled();
    String createTicket(MentionEntity mention, TicketAgent.TicketPayload payload);
    boolean updateStatus(String ticketId, String status, String resolution);
    String getTicketUrl(String ticketId);
}