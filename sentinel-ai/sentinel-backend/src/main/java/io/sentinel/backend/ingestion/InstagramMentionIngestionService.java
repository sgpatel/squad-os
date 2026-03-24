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
 * Instagram Basic Display API mention ingestion.
 * Polls for new media and comments.
 * Set sentinel.instagram.enabled=true + access-token to activate.
 */
@Service
public class InstagramMentionIngestionService implements MentionSource {

    private static final String GRAPH_URL = "https://graph.instagram.com";

    private final MentionProcessingService processor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${sentinel.instagram.access-token:}") private String accessToken;
    @Value("${sentinel.instagram.enabled:false}") private boolean enabled;
    @Value("${sentinel.handle:@AirtelPaymentsBank}") private String handle;

    private final OkHttpClient client;

    public InstagramMentionIngestionService(MentionProcessingService processor) {
        this.processor = processor;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "Instagram";
    }

    @Override
    public boolean isEnabled() {
        return enabled && !accessToken.isEmpty();
    }

    @Override
    public void start() {
        if (!isEnabled()) return;
        running.set(true);
        System.out.println("[InstagramIngestion] Starting Instagram mention ingestion for " + handle);
    }

    @Override
    public void stop() {
        running.set(false);
        System.out.println("[InstagramIngestion] Stopped Instagram mention ingestion");
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @Scheduled(fixedDelayString = "${sentinel.polling.interval-ms:30000}")
    public void pollMentions() {
        if (!running.get() || !isEnabled()) return;

        try {
            // Get user media
            String url = GRAPH_URL + "/me/media?access_token=" + accessToken + "&fields=id,media_type,media_url,caption,permalink,timestamp,like_count,comments_count";

            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("[InstagramIngestion] API error: " + response.code() + " " + response.message());
                    return;
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode media : data) {
                        processMedia(media);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[InstagramIngestion] Error polling mentions: " + e.getMessage());
        }
    }

    private void processMedia(JsonNode media) {
        try {
            String id = media.get("id").asText();
            String caption = media.has("caption") ? media.get("caption").asText() : "";
            String permalink = media.get("permalink").asText();
            String timestamp = media.get("timestamp").asText();

            // For mentions, we might need to check if it's a mention or tag
            // For simplicity, treat all media as potential mentions
            if (!caption.isEmpty()) {
                MentionEntity mention = new MentionEntity();
                mention.platform = "INSTAGRAM";
                mention.id = id;
                mention.handle = handle;
                mention.authorUsername = "self"; // Placeholder
                mention.authorName = "Instagram User"; // Placeholder
                mention.text = caption;
                mention.url = permalink;
                mention.postedAt = Instant.parse(timestamp);
                mention.ingestedAt = Instant.now();

                processor.process(mention);
            }

            // Process comments
            getCommentsForMedia(id);
        } catch (Exception e) {
            System.err.println("[InstagramIngestion] Error processing media: " + e.getMessage());
        }
    }

    private void getCommentsForMedia(String mediaId) {
        try {
            String url = GRAPH_URL + "/" + mediaId + "/comments?access_token=" + accessToken + "&fields=id,text,timestamp,username";

            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return;

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode comment : data) {
                        String commentId = comment.get("id").asText();
                        String text = comment.get("text").asText();
                        String username = comment.get("username").asText();
                        String timestamp = comment.get("timestamp").asText();

                        MentionEntity mention = new MentionEntity();
                        mention.platform = "INSTAGRAM";
                        mention.id = commentId;
                        mention.handle = handle;
                        mention.authorUsername = username;
                        mention.authorName = username;
                        mention.text = text;
                        mention.postedAt = Instant.parse(timestamp);
                        mention.ingestedAt = Instant.now();

                        processor.process(mention);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[InstagramIngestion] Error getting comments: " + e.getMessage());
        }
    }
}
