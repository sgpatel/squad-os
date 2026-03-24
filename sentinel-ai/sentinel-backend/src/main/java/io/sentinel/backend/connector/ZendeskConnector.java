package io.sentinel.backend.connector;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinel.backend.agents.TicketAgent;
import io.sentinel.backend.repository.MentionEntity;
import okhttp3.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ZendeskConnector implements TicketConnector {
    @Value("${sentinel.zendesk.subdomain:}") private String subdomain;
    @Value("${sentinel.zendesk.email:}")     private String email;
    @Value("${sentinel.zendesk.api-token:}") private String apiToken;
    @Value("${sentinel.zendesk.enabled:false}") private boolean enabled;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String getName() { return "Zendesk"; }
    @Override public boolean isEnabled() { return enabled && !subdomain.isBlank() && !apiToken.isBlank(); }

    @Override
    public String createTicket(MentionEntity m, TicketAgent.TicketPayload p) {
        try {
            String priority = switch (p.priority != null ? p.priority : "P3") {
                case "P1" -> "urgent"; case "P2" -> "high";
                case "P3" -> "normal"; default -> "low";
            };
            Map<String,Object> ticket = new LinkedHashMap<>();
            ticket.put("subject", p.title != null ? p.title : "Social Media Issue");
            ticket.put("priority", priority);
            ticket.put("tags", p.tags != null ? p.tags : List.of("social-media", "sentinel-ai"));
            Map<String,Object> comment = new LinkedHashMap<>();
            comment.put("body", buildDescription(m, p));
            ticket.put("comment", comment);

            String body = mapper.writeValueAsString(Map.of("ticket", ticket));
            String url   = "https://" + subdomain + ".zendesk.com/api/v2/tickets.json";
            String creds = Base64.getEncoder().encodeToString((email + "/token:" + apiToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            Request req = new Request.Builder().url(url)
                .header("Authorization", "Basic " + creds)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    Map<?,?> result = mapper.readValue(resp.body().string(), Map.class);
                    Map<?,?> t = (Map<?,?>) result.get("ticket");
                    String id = String.valueOf(t.get("id"));
                    System.out.println("[Zendesk] Ticket created: #" + id);
                    return "ZD-" + id;
                }
            }
        } catch (Exception e) { System.err.println("[Zendesk] Error: " + e.getMessage()); }
        return null;
    }

    @Override
    public boolean updateStatus(String ticketId, String status, String resolution) {
        try {
            String id = ticketId.replace("ZD-", "");
            String zdStatus = "resolved".equals(status.toLowerCase()) ? "solved" : status.toLowerCase();
            String body = mapper.writeValueAsString(Map.of("ticket",
                Map.of("status", zdStatus, "comment", Map.of("body", resolution, "public", false))));
            String url   = "https://" + subdomain + ".zendesk.com/api/v2/tickets/" + id + ".json";
            String creds = Base64.getEncoder().encodeToString((email + "/token:" + apiToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Request req = new Request.Builder().url(url)
                .header("Authorization", "Basic " + creds)
                .header("Content-Type", "application/json")
                .put(RequestBody.create(body, MediaType.parse("application/json"))).build();
            try (Response resp = client.newCall(req).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    @Override
    public String getTicketUrl(String ticketId) {
        return "https://" + subdomain + ".zendesk.com/agent/tickets/" + ticketId.replace("ZD-","");
    }

    private String buildDescription(MentionEntity m, TicketAgent.TicketPayload p) {
        return "== Social Media Mention ==\n" +
            "Platform: " + m.platform + "\n" +
            "Author: @" + m.authorUsername + " (" + m.authorFollowers + " followers)\n" +
            "URL: " + m.url + "\n" +
            "Text: " + m.text + "\n\n" +
            "== AI Analysis ==\n" +
            "Sentiment: " + m.sentimentLabel + " (score: " + m.sentimentScore + ")\n" +
            "Emotion: " + m.primaryEmotion + "\n" +
            "Urgency: " + m.urgency + "\n" +
            "Topic: " + m.topic + "\n" +
            "Priority: " + m.priority + "\n\n" +
            "== AI Summary ==\n" + (m.summary != null ? m.summary : "") + "\n\n" +
            "== Suggested Resolution ==\n" + (p.suggestedResolution != null ? p.suggestedResolution : "Follow standard procedure") + "\n\n" +
            "== Internal Notes ==\n" + (p.internalNotes != null ? p.internalNotes : "") + "\n" +
            "\n[Generated by SentinelAI · Powered by SquadOS]";
    }
}