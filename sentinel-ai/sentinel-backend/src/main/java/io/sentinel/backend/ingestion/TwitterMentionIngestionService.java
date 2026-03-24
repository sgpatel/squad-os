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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Twitter/X API v2 mention ingestion.
 * Mode 1: Filtered Stream (real-time via SSE)
 * Mode 2: Recent Search polling (fallback)
 * Set sentinel.twitter.enabled=true + bearer-token to activate.
 */
@Service
public class TwitterMentionIngestionService implements MentionSource {

    private static final String STREAM_URL = "https://api.twitter.com/2/tweets/search/stream";
    private static final String SEARCH_URL = "https://api.twitter.com/2/tweets/search/recent";
    private static final String RULES_URL  = "https://api.twitter.com/2/tweets/search/stream/rules";
    private static final String FIELDS     = "tweet.fields=created_at,author_id,public_metrics,lang&expansions=author_id&user.fields=name,username,public_metrics";

    private final MentionProcessingService processor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${sentinel.twitter.bearer-token:}") private String bearerToken;
    @Value("${sentinel.twitter.enabled:false}") private boolean enabled;
    @Value("${sentinel.handle:@AirtelPaymentsBank}") private String handle;
    @Value("${sentinel.twitter.stream-enabled:true}") private boolean streamEnabled;

    private final OkHttpClient client;
    private Call activeStream;

    public TwitterMentionIngestionService(MentionProcessingService processor) {
        this.processor = processor;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build();
    }

    @Override public String getName()    { return "Twitter API v2"; }
    @Override public boolean isEnabled() { return enabled && bearerToken != null && !bearerToken.isBlank(); }

    @Override
    public void start() {
        if (!isEnabled()) {
            System.out.println("[Twitter] Disabled — configure sentinel.twitter.bearer-token");
            return;
        }
        ensureStreamRules();
        if (streamEnabled) startStream();
        System.out.println("[Twitter] Ingestion started for: " + handle);
    }

    @Override public void stop() { running.set(false); if (activeStream != null) activeStream.cancel(); }
    @PreDestroy public void shutdown() { stop(); }

    private void ensureStreamRules() {
        try {
            String handleClean = handle.replace("@", "");
            // 1. Get existing rules
            Request getR = bearer(new Request.Builder().url(RULES_URL)).build();
            try (Response r = client.newCall(getR).execute()) {
                JsonNode body = mapper.readTree(r.body().string());
                JsonNode data = body.get("data");
                if (data != null && data.isArray() && data.size() > 0) {
                    StringBuilder ids = new StringBuilder("[");
                    data.forEach(rule -> ids.append("\"").append(rule.get("id").asText()).append("\","));
                    ids.setCharAt(ids.length()-1, ']');
                    String del = "{\"delete\":{\"ids\":" + ids + "}}";
                    post(RULES_URL, del).close();
                }
            }
            // 2. Add rule for our handle
            String rule = "{\"add\":[{\"value\":\"@" + handleClean + " OR #" + handleClean + "\",\"tag\":\"" + handleClean + "\"}]}";
            try (Response r = post(RULES_URL, rule)) {
                System.out.println("[Twitter] Rule set: " + r.code());
            }
        } catch (Exception e) { System.err.println("[Twitter] Rules error: " + e.getMessage()); }
    }

    private void startStream() {
        running.set(true);
        Thread.ofVirtual().start(() -> {
            while (running.get()) {
                try {
                    Request req = bearer(new Request.Builder().url(STREAM_URL + "?" + FIELDS)).build();
                    activeStream = client.newCall(req);
                    try (Response resp = activeStream.execute()) {
                        if (!resp.isSuccessful()) { Thread.sleep(5000); continue; }
                        BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body().byteStream()));
                        String line;
                        while ((line = reader.readLine()) != null && running.get()) {
                            if (!line.isBlank()) parseTweet(line);
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) try { Thread.sleep(15000); } catch (InterruptedException ie) { break; }
                }
            }
        });
    }

    @Scheduled(fixedDelayString = "${sentinel.twitter.poll-interval-ms:300000}")
    public void pollRecent() {
        if (!isEnabled() || streamEnabled) return;
        try {
            String q = handle.replace("@", "");
            String url = SEARCH_URL + "?query=@" + q + " -is:retweet&max_results=100&" + FIELDS;
            Request req = bearer(new Request.Builder().url(url)).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    JsonNode root = mapper.readTree(resp.body().string());
                    JsonNode data = root.get("data");
                    if (data != null) data.forEach(t -> parseTweetNode(t, root.get("includes")));
                }
            }
        } catch (Exception e) { System.err.println("[Twitter] Poll error: " + e.getMessage()); }
    }

    private void parseTweet(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : null;
            if (data == null) return;
            parseTweetNode(data, root.get("includes"));
        } catch (Exception e) { System.err.println("[Twitter] Parse error: " + e.getMessage()); }
    }

    private void parseTweetNode(JsonNode data, JsonNode includes) {
        try {
            if (!data.has("id")) return;
            MentionEntity m = new MentionEntity();
            m.id = data.get("id").asText();
            m.platform = "TWITTER"; m.handle = handle;
            m.text = data.has("text") ? data.get("text").asText() : "";
            // originalText removed — use m.text directly
            m.language = data.has("lang") ? data.get("lang").asText() : "en";
            m.postedAt = data.has("created_at") ? OffsetDateTime.parse(data.get("created_at").asText()).toInstant() : Instant.now();
            if (data.has("public_metrics")) {
                JsonNode pm = data.get("public_metrics");
                m.likeCount    = pm.path("like_count").asLong(0);
                m.retweetCount = pm.path("retweet_count").asLong(0);
            }
            // Extract author
            String authorId = data.path("author_id").asText("");
            if (includes != null && includes.has("users")) {
                for (JsonNode u : includes.get("users")) {
                    if (authorId.equals(u.path("id").asText())) {
                        m.authorUsername  = u.path("username").asText("unknown");
                        m.authorName      = u.path("name").asText(m.authorUsername);
                        m.authorFollowers = u.path("public_metrics").path("followers_count").asLong(0);
                        break;
                    }
                }
            }
            if (m.authorUsername == null) m.authorUsername = "unknown";
            if (m.authorName     == null) m.authorName     = m.authorUsername;
            m.url = "https://twitter.com/" + m.authorUsername + "/status/" + m.id;
            m.processingStatus = "NEW";
            System.out.println("[Twitter] @" + m.authorUsername + ": " + m.text.substring(0, Math.min(60, m.text.length())));
            Thread.ofVirtual().start(() -> processor.process(m));
        } catch (Exception e) { System.err.println("[Twitter] Node parse error: " + e.getMessage()); }
    }

    private Request.Builder bearer(Request.Builder b) { return b.header("Authorization", "Bearer " + bearerToken); }

    private Response post(String url, String body) throws Exception {
        Request req = bearer(new Request.Builder().url(url)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body, MediaType.parse("application/json")))).build();
        return client.newCall(req).execute();
    }
}