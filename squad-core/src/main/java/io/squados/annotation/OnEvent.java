package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Triggers an agent method when an event arrives on a channel.
 *
 * Supported event sources (pluggable via EventSource interface):
 *   - Kafka topic
 *   - Webhook HTTP endpoint
 *   - In-process event bus (for testing and single-node)
 *   - Scheduled timer (cron expression)
 *
 * Usage:
 * <pre>
 * // Trigger on Kafka payment events
 * {@literal @}OnEvent(topic = "payments.incoming", filter = "amount &gt; 10000")
 * public void onLargePayment(SquadEvent event) {
 *     // Called for every payment &gt; £10,000
 *     fraudSquad.assess(event.getPayload());
 * }
 *
 * // Trigger on a schedule
 * {@literal @}OnEvent(topic = "schedule", cron = "0 9 * * MON-FRI")
 * public void morningBriefing(SquadEvent event) {
 *     // Run every weekday at 9am
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface OnEvent {
    /** Topic, channel, or event type to subscribe to. */
    String topic();

    /**
     * Optional filter expression.
     * Uses same syntax as @AutoApproval conditions.
     * Example: "amount &gt; 10000 AND currency == USD"
     */
    String filter() default "";

    /**
     * Cron expression for scheduled events.
     * When set, topic is ignored and event fires on schedule.
     * Example: "0 9 * * MON-FRI" (weekdays at 9am)
     */
    String cron() default "";

    /**
     * Maximum number of concurrent event handlers.
     * Default: 1 (sequential). Set higher for parallel processing.
     */
    int concurrency() default 1;

    /**
     * Whether to retry on exception.
     * Default: true — retries up to maxRetries times.
     */
    boolean retryOnError() default true;

    /** Maximum retry attempts before giving up. */
    int maxRetries() default 3;
}