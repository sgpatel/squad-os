package io.squados.event;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * In-process event bus — no Kafka, no network.
 * Used for testing, single-node apps, and local development.
 *
 * Also acts as the backbone for @OnEvent in squad-core.
 * Swap with KafkaEventSource for production.
 *
 * Usage:
 * <pre>
 * InProcessEventBus bus = new InProcessEventBus();
 * bus.subscribe("payments.incoming", event -> process(event));
 * bus.publish("payments.incoming", "{\"amount\": 50000}");
 * </pre>
 */
public class InProcessEventBus implements EventSource {

    private final Map<String, List<Consumer<SquadEvent>>> handlers
        = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final int concurrency;

    public InProcessEventBus() {
        this(1);
    }

    public InProcessEventBus(int concurrency) {
        this.concurrency = concurrency;
        this.executor = concurrency == 1
            ? Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "squad-event-bus");
                t.setDaemon(true); return t;
            })
            : Executors.newFixedThreadPool(concurrency, r -> {
                Thread t = new Thread(r, "squad-event-bus");
                t.setDaemon(true); return t;
            });
    }

    @Override
    public String sourceType() { return "in-process"; }

    @Override
    public void subscribe(String topic, Consumer<SquadEvent> handler) {
        handlers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
        System.out.printf("[EventBus] Subscribed to topic: %s%n", topic);
    }

    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
    }

    @Override
    public void publish(String topic, String payload) {
        SquadEvent event = new SquadEvent(topic, payload, "in-process");
        dispatch(topic, event);
    }

    /** Publish a pre-built SquadEvent. */
    public void publish(SquadEvent event) {
        dispatch(event.getTopic(), event);
    }

    private void dispatch(String topic, SquadEvent event) {
        List<Consumer<SquadEvent>> hs = handlers.get(topic);
        if (hs == null || hs.isEmpty()) {
            // Also try wildcard subscribers
            hs = handlers.get("*");
        }
        if (hs == null || hs.isEmpty()) return;
        final List<Consumer<SquadEvent>> finalHs = hs;
        executor.submit(() -> {
            for (Consumer<SquadEvent> h : finalHs) {
                try { h.accept(event); }
                catch (Exception e) {
                    System.err.printf("[EventBus] Handler error on %s: %s%n",
                        topic, e.getMessage());
                }
            }
        });
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        handlers.clear();
    }

    public int subscriberCount(String topic) {
        return handlers.getOrDefault(topic, List.of()).size();
    }

    public boolean hasSubscribers(String topic) {
        return subscriberCount(topic) > 0;
    }
}