package io.sentinel.backend.connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
@Component
public class TicketConnectorFactory {
    private final List<TicketConnector> connectors;
    @Value("${sentinel.ticket.system:MOCK}") private String system;

    public TicketConnectorFactory(List<TicketConnector> connectors) {
        this.connectors = connectors;
    }

    public TicketConnector get() {
        return connectors.stream()
            .filter(TicketConnector::isEnabled)
            .findFirst()
            .orElseGet(() -> new MockTicketConnector());
    }

    public static class MockTicketConnector implements TicketConnector {
        private int counter = 1000;
        @Override public String getName() { return "MOCK"; }
        @Override public boolean isEnabled() { return true; }
        @Override public String createTicket(io.sentinel.backend.repository.MentionEntity m,
            io.sentinel.backend.agents.TicketAgent.TicketPayload p) {
            String id = "TKT-" + (++counter);
            System.out.println("[MockTicket] Created: " + id + " — " + (p.title != null ? p.title : "Issue"));
            return id;
        }
        @Override public boolean updateStatus(String id, String s, String r) {
            System.out.println("[MockTicket] Updated " + id + " -> " + s); return true;
        }
        @Override public String getTicketUrl(String id) { return "http://localhost/tickets/" + id; }
    }
}