package io.squados.event;

import java.util.function.Consumer;

/**
 * Pluggable event source interface.
 *
 * Implement this to connect SquadOS to any event system:
 *   - Kafka: KafkaEventSource
 *   - Webhook: WebhookEventSource
 *   - Timer: ScheduledEventSource
 *   - Redis Pub/Sub: RedisEventSource
 *   - In-process: InProcessEventBus (built-in, for testing)
 *
 * Usage:
 * <pre>
 * EventSource kafka = new KafkaEventSource(bootstrapServers, groupId);
 * eventRouter.register("payments.incoming", kafka);
 * </pre>
 */
public interface EventSource {

    /** Unique identifier for this source type: "kafka", "webhook", "timer". */
    String sourceType();

    /**
     * Subscribe to a topic. The handler is called for every event.
     * Must be non-blocking — spawn a thread internally if needed.
     */
    void subscribe(String topic, Consumer<SquadEvent> handler);

    /** Unsubscribe from a topic. */
    void unsubscribe(String topic);

    /** Publish an event to a topic (for sources that support it). */
    default void publish(String topic, String payload) {
        throw new UnsupportedOperationException(sourceType() + " does not support publish");
    }

    /** Stop the event source and release resources. */
    void shutdown();
}