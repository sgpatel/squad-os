package io.sentinel.backend.ingestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinel.backend.repository.MentionEntity;
import io.sentinel.backend.service.MentionProcessingService;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Facebook Graph API mention ingestion for page mentions.
 * Polls for new posts and comments mentioning the page.
 * Set sentinel.facebook.enabled=true + access-token to activate.
 */
@Service
public class FacebookMentionIngestionService implements MentionSource {

    private static final String GRAPH_URL = "https://graph.facebook.com/v18.0";
    private static final String FIELDS = "id,message,created_time,from{id,name,link},comments{id,message,created_time,from{id,name,link}},permalink_url";

    private final MentionProcessingService processor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${sentinel.facebook.access-token:}") private String accessToken;
    @Value("${sentinel.facebook.enabled:false}") private boolean enabled;
    @Value("${sentinel.handle:@AirtelPaymentsBank}") private String handle;

    private final OkHttpClient client;

    public FacebookMentionIngestionService(MentionProcessingService processor) {
        this.processor = processor;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "Facebook";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !accessToken.isEmpty();
    }

    @Override
    public void start() {
        if (!isEnabled()) return;
        running.set(true);
        System.out.println("[FacebookIngestion] Starting Facebook mention ingestion for " + handle);
    }

    @Override
    public void stop() {
        running.set(false);
        System.out.println("[FacebookIngestion] Stopped Facebook mention ingestion");
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @Scheduled(fixedDelayString = "${sentinel.polling.interval-ms:30000}")
    public void pollMentions() {
        if (!running.get() || !isEnabled()) return;

        try {
            // For simplicity, assuming page ID is derived from handle or configured
            // In real implementation, need to resolve page ID from handle
            String pageId = "123456789"; // Placeholder - need to implement page ID resolution

            String url = GRAPH_URL + "/" + pageId + "/feed?access_token=" + accessToken + "&fields=" + FIELDS + "&limit=10";

            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("[FacebookIngestion] API error: " + response.code() + " " + response.message());
                    return;
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode post : data) {
                        processPost(post);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FacebookIngestion] Error polling mentions: " + e.getMessage());
        }
    }

    private void processPost(JsonNode post) {
        try {
            String id = post.get("id").asText();
            String message = post.has("message") ? post.get("message").asText() : "";
            if (message.isEmpty()) return;

            JsonNode from = post.get("from");
            String authorName = from.get("name").asText();
            String authorId = from.get("id").asText();
            String permalink = post.get("permalink_url").asText();

            // Create mention
            MentionEntity mention = new MentionEntity();
            mention.id = id;
            mention.platform = "FACEBOOK";
            mention.handle = handle;
            mention.authorUsername = authorId;
            mention.authorName = authorName;
            mention.text = message;
            mention.url = permalink;
            mention.postedAt = Instant.parse(post.get("created_time").asText());
            mention.ingestedAt = Instant.now();

            processor.process(mention);

            // Process comments if any
            JsonNode comments = post.get("comments");
            if (comments != null && comments.has("data")) {
                for (JsonNode comment : comments.get("data")) {
                    processComment(comment, id);
                }
            }
        } catch (Exception e) {
            System.err.println("[FacebookIngestion] Error processing post: " + e.getMessage());
        }
    }

    private void processComment(JsonNode comment, String parentId) {
        try {
            String id = comment.get("id").asText();
            String message = comment.get("message").asText();

            JsonNode from = comment.get("from");
            String authorName = from.get("name").asText();
            String authorId = from.get("id").asText();

            MentionEntity mention = new MentionEntity();
            mention.id = id;
            mention.platform = "FACEBOOK";
            mention.handle = handle;
            mention.authorUsername = authorId;
            mention.authorName = authorName;
            mention.text = message;
            mention.postedAt = Instant.parse(comment.get("created_time").asText());
            mention.ingestedAt = Instant.now();

            processor.process(mention);
        } catch (Exception e) {
            System.err.println("[FacebookIngestion] Error processing comment: " + e.getMessage());
        }
    }
}
