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
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LinkedIn Marketing API mention ingestion for organization updates and comments.
 * Polls for new posts and comments.
 * Set sentinel.linkedin.enabled=true + access-token to activate.
 */
@Service
public class LinkedInMentionIngestionService implements MentionSource {

    private static final String API_URL = "https://api.linkedin.com/v2";

    private final MentionProcessingService processor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${sentinel.linkedin.access-token:}") private String accessToken;
    @Value("${sentinel.linkedin.enabled:false}") private boolean enabled;
    @Value("${sentinel.handle:@AirtelPaymentsBank}") private String handle;

    private final OkHttpClient client;

    public LinkedInMentionIngestionService(MentionProcessingService processor) {
        this.processor = processor;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "LinkedIn";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !accessToken.isEmpty();
    }

    @Override
    public void start() {
        if (!isEnabled()) return;
        running.set(true);
        System.out.println("[LinkedInIngestion] Starting LinkedIn mention ingestion for " + handle);
    }

    @Override
    public void stop() {
        running.set(false);
        System.out.println("[LinkedInIngestion] Stopped LinkedIn mention ingestion");
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @Scheduled(fixedDelayString = "${sentinel.polling.interval-ms:30000}")
    public void pollMentions() {
        if (!running.get() || !isEnabled()) return;

        try {
            // Get organization updates
            // First, need to get organization URN
            String orgUrn = "urn:li:organization:123456"; // Placeholder - need to resolve from handle

            String url = API_URL + "/posts?author=" + orgUrn + "&oauth2_access_token=" + accessToken;

            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("[LinkedInIngestion] API error: " + response.code() + " " + response.message());
                    return;
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode elements = root.get("elements");
                if (elements != null && elements.isArray()) {
                    for (JsonNode post : elements) {
                        processPost(post);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LinkedInIngestion] Error polling mentions: " + e.getMessage());
        }
    }

    private void processPost(JsonNode post) {
        try {
            String id = post.get("id").asText();
            JsonNode commentary = post.get("commentary");
            String text = commentary != null ? commentary.asText() : "";

            if (text.isEmpty()) return;

            // Author info
            JsonNode author = post.get("author");
            String authorName = author.get("name").asText();
            String authorUrn = author.get("urn").asText();

            String permalink = "https://www.linkedin.com/posts/" + id;

            MentionEntity mention = new MentionEntity();
            mention.platform = "LINKEDIN";
            mention.id = id;
            mention.handle = handle;
            mention.authorUsername = authorUrn;
            mention.authorName = authorName;
            mention.text = text;
            mention.url = permalink;
            mention.postedAt = Instant.now(); // LinkedIn API may provide timestamp
            mention.ingestedAt = Instant.now();

            processor.process(mention);

            // Process comments if available
            getCommentsForPost(id);
        } catch (Exception e) {
            System.err.println("[LinkedInIngestion] Error processing post: " + e.getMessage());
        }
    }

    private void getCommentsForPost(String postId) {
        try {
            String url = API_URL + "/posts/" + postId + "/comments?oauth2_access_token=" + accessToken;

            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return;

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode elements = root.get("elements");
                if (elements != null && elements.isArray()) {
                    for (JsonNode comment : elements) {
                        String commentId = comment.get("id").asText();
                        String text = comment.get("message").get("text").asText();
                        JsonNode author = comment.get("actor");
                        String authorName = author.get("name").asText();
                        String authorUrn = author.get("urn").asText();

                        MentionEntity mention = new MentionEntity();
                        mention.platform = "LINKEDIN";
                        mention.id = commentId;
                        mention.handle = handle;
                        mention.authorUsername = authorUrn;
                        mention.authorName = authorName;
                        mention.text = text;
                        mention.postedAt = Instant.now();
                        mention.ingestedAt = Instant.now();

                        processor.process(mention);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LinkedInIngestion] Error getting comments: " + e.getMessage());
        }
    }
}
