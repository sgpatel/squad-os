package io.squados.event;

import java.time.Instant;
import java.util.*;

/**
 * An event delivered to an {@literal @}OnEvent handler.
 *
 * Contains the raw payload, metadata, and routing information.
 * Payload is always a String — deserialise to your domain type.
 *
 * Usage:
 * <pre>
 * {@literal @}OnEvent(topic = "payments.incoming")
 * public void onPayment(SquadEvent event) {
 *     String json    = event.getPayload();
 *     String topic   = event.getTopic();
 *     Instant ts     = event.getTimestamp();
 *     String source  = event.getSource(); // "kafka", "webhook", "timer"
 * }
 * </pre>
 */
public class SquadEvent {

    private final String              id;
    private final String              topic;
    private final String              payload;
    private final String              source;
    private final Instant             timestamp;
    private final Map<String, String> headers;
    private int                       retryCount;

    public SquadEvent(String topic, String payload, String source) {
        this.id        = UUID.randomUUID().toString();
        this.topic     = topic;
        this.payload   = payload;
        this.source    = source;
        this.timestamp = Instant.now();
        this.headers   = new LinkedHashMap<>();
    }

    public SquadEvent withHeader(String key, String value) {
        headers.put(key, value); return this;
    }

    public String              getId()        { return id; }
    public String              getTopic()     { return topic; }
    public String              getPayload()   { return payload; }
    public String              getSource()    { return source; }
    public Instant             getTimestamp() { return timestamp; }
    public Map<String, String> getHeaders()   { return Collections.unmodifiableMap(headers); }
    public int                 getRetryCount(){ return retryCount; }
    public void                incrementRetry(){ retryCount++; }

    @Override
    public String toString() {
        return String.format("SquadEvent{id=%s, topic=%s, source=%s, ts=%s}",
            id, topic, source, timestamp);
    }
}