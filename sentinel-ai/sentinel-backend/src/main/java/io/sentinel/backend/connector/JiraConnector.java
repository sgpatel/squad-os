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
public class JiraConnector implements TicketConnector {
    @Value("${sentinel.jira.url:}")         private String jiraUrl;
    @Value("${sentinel.jira.email:}")       private String email;
    @Value("${sentinel.jira.api-token:}")   private String apiToken;
    @Value("${sentinel.jira.project-key:SENT}") private String projectKey;
    @Value("${sentinel.jira.enabled:false}") private boolean enabled;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String getName() { return "Jira"; }
    @Override public boolean isEnabled() { return enabled && !jiraUrl.isBlank() && !apiToken.isBlank(); }

    @Override
    public String createTicket(MentionEntity m, TicketAgent.TicketPayload p) {
        try {
            String priority = switch (p.priority != null ? p.priority : "P3") {
                case "P1" -> "Highest"; case "P2" -> "High";
                case "P3" -> "Medium";  default -> "Low";
            };
            Map<String,Object> fields = new LinkedHashMap<>();
            fields.put("project", Map.of("key", projectKey));
            fields.put("summary", p.title != null ? p.title : "Social media issue");
            fields.put("description", Map.of(
                "type", "doc", "version", 1,
                "content", List.of(Map.of("type","paragraph","content",
                    List.of(Map.of("type","text","text",
                    "Mention: " + m.text + "\nSentiment: " + m.sentimentLabel +
                    "\nUrgency: " + m.urgency + "\nURL: " + m.url))))));
            fields.put("issuetype", Map.of("name", "Bug"));
            fields.put("priority", Map.of("name", priority));
            if (p.tags != null && !p.tags.isEmpty()) {
                fields.put("labels", p.tags);
            }
            String body = mapper.writeValueAsString(Map.of("fields", fields));
            String creds = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Request req = new Request.Builder()
                .url(jiraUrl + "/rest/api/3/issue")
                .header("Authorization", "Basic " + creds)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json"))).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    Map<?,?> result = mapper.readValue(resp.body().string(), Map.class);
                    String key = (String) result.get("key");
                    System.out.println("[Jira] Issue created: " + key);
                    return key;
                }
            }
        } catch (Exception e) { System.err.println("[Jira] Error: " + e.getMessage()); }
        return null;
    }

    @Override
    public boolean updateStatus(String ticketId, String status, String resolution) {
        try {
            String transitionId = "done".equalsIgnoreCase(status) || "resolved".equalsIgnoreCase(status) ? "31" : "21";
            String body = mapper.writeValueAsString(Map.of("transition", Map.of("id", transitionId)));
            String creds = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Request req = new Request.Builder()
                .url(jiraUrl + "/rest/api/3/issue/" + ticketId + "/transitions")
                .header("Authorization", "Basic " + creds)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json"))).build();
            try (Response resp = client.newCall(req).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    @Override
    public String getTicketUrl(String ticketId) { return jiraUrl + "/browse/" + ticketId; }
}