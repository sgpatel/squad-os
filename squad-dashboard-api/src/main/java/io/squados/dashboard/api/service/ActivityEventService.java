package io.squados.dashboard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.squados.dashboard.api.model.ActivityEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Manages the real-time activity event stream for the dashboard.
 *
 * <ul>
 *   <li>Maintains a ring-buffer of the last 200 events for late-joining clients.</li>
 *   <li>Fans events out to all active SSE subscribers.</li>
 *   <li>Cleans up dead emitters automatically on send failure.</li>
 * </ul>
 */
@Service
public class ActivityEventService {

    private static final Logger log = Logger.getLogger(ActivityEventService.class.getName());
    private static final int MAX_HISTORY = 200;

    /** Thread-safe ring-buffer of recent events (newest at index 0). */
    private final CopyOnWriteArrayList<ActivityEvent> history     = new CopyOnWriteArrayList<>();

    /** Currently connected SSE clients. */
    private final CopyOnWriteArrayList<SseEmitter>   subscribers = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ── Publishing ────────────────────────────────────────────────────────────

    /**
     * Publish an event to the ring-buffer and fan it out to all SSE subscribers.
     */
    public void publish(ActivityEvent event) {
        // Prepend — newest first
        history.add(0, event);
        if (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }

        String json = toJson(event);
        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .name("activity")
                        .data(json));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        subscribers.removeAll(dead);
    }

    /**
     * Convenience publisher for AGENT_CALL events.
     */
    public void publishAgentCall(String agentName, String role, String status,
                                  long latencyMs, int tokens, String message) {
        publish(new ActivityEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "AGENT_CALL",
                agentName,
                role,
                message,
                status,
                latencyMs,
                tokens
        ));
    }

    /**
     * Convenience publisher for SYSTEM events.
     */
    public void publishSystem(String message) {
        publish(new ActivityEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "SYSTEM",
                "SquadOS",
                null,
                message,
                "info",
                null,
                null
        ));
    }

    /**
     * Convenience publisher for generic typed events.
     */
    public void publishEvent(String type, String agentName, String role,
                              String message, String status) {
        publish(new ActivityEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                type,
                agentName,
                role,
                message,
                status,
                null,
                null
        ));
    }

    // ── SSE subscription ──────────────────────────────────────────────────────

    /**
     * Register a new SSE subscriber.  Returns an emitter with no timeout (0L)
     * so the connection stays alive until the client disconnects.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(()    -> subscribers.remove(emitter));
        emitter.onError(e       -> subscribers.remove(emitter));

        // Send recent history immediately so the client has context
        try {
            for (int i = Math.min(history.size(), 20) - 1; i >= 0; i--) {
                emitter.send(SseEmitter.event()
                        .name("activity")
                        .data(toJson(history.get(i))));
            }
        } catch (Exception e) {
            log.warning("[ActivityEventService] Failed to send history to new subscriber: " + e.getMessage());
            subscribers.remove(emitter);
        }

        return emitter;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Returns an unmodifiable view of the event ring-buffer (newest first). */
    public List<ActivityEvent> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Current number of connected SSE subscribers. */
    public int subscriberCount() {
        return subscribers.size();
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private String toJson(ActivityEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{}";
        }
    }
}
