package io.sentinel.backend.connector;

import io.sentinel.backend.agents.TicketAgent;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.repository.MentionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TicketConnectorFactory — selects the active connector at startup.
 * Priority: Zendesk > Jira > Mock (default).
 *
 * MockTicketConnector seeds its counter from the database on startup
 * so ticket numbers never reset or repeat across restarts.
 */
@Component
public class TicketConnectorFactory {

    private final ZendeskConnector  zendesk;
    private final JiraConnector     jira;
    private final MockTicketConnector mock;

    public TicketConnectorFactory(ZendeskConnector zendesk,
                                  JiraConnector jira,
                                  MockTicketConnector mock) {
        this.zendesk = zendesk;
        this.jira    = jira;
        this.mock    = mock;
    }

    public TicketConnector get() {
        if (zendesk.isEnabled()) return zendesk;
        if (jira.isEnabled())    return jira;
        return mock;
    }

    // ── Inner mock connector ──────────────────────────────────────
    @Component
    public static class MockTicketConnector implements TicketConnector {

        private final AtomicInteger counter;
        private final MentionRepository mentionRepo;

        public MockTicketConnector(MentionRepository mentionRepo) {
            this.mentionRepo = mentionRepo;
            // Seed with 1000 — will be updated in @PostConstruct from DB
            this.counter = new AtomicInteger(1000);
        }

        /**
         * Seed the counter from the database after startup.
         * Finds the highest existing TKT-XXXX number and continues from there.
         * This ensures ticket IDs never repeat across application restarts.
         */
        @PostConstruct
        public void seedCounter() {
            try {
                int maxSeed = mentionRepo.findAll().stream()
                    .map(m -> m.ticketId)
                    .filter(id -> id != null && id.startsWith("TKT-"))
                    .mapToInt(id -> {
                        try { return Integer.parseInt(id.substring(4)); }
                        catch (NumberFormatException e) { return 0; }
                    })
                    .max()
                    .orElse(1000);
                counter.set(maxSeed);
                System.out.println("[MockTicket] Counter seeded to " + maxSeed
                    + " — next ticket: TKT-" + (maxSeed + 1));
            } catch (Exception e) {
                System.err.println("[MockTicket] Counter seed failed, starting at 1000: " + e.getMessage());
                counter.set(1000);
            }
        }

        @Override
        public String createTicket(MentionEntity mention, TicketAgent.TicketPayload payload) {
            String ticketId = "TKT-" + counter.incrementAndGet();
            System.out.println("[MockTicket] Created: " + ticketId + " — " + payload.title);
            return ticketId;
        }

        @Override
        public boolean updateStatus(String ticketId, String status, String resolution) {
            System.out.println("[MockTicket] Updated: " + ticketId + " -> " + status);
            return true;
        }

        @Override
        public String getTicketUrl(String ticketId) {
            return "https://mock-tickets.internal/" + ticketId;
        }

        @Override
        public String getName() { return "MOCK"; }

        @Override
        public boolean isEnabled() { return true; }
    }
}
