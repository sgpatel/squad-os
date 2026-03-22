package io.squados.event;

import io.squados.annotation.OnEvent;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Discovers all {@literal @}OnEvent methods on agent instances and
 * wires them to the appropriate EventSource subscriptions.
 *
 * Called once during SquadRunner.boot().
 *
 * Usage:
 * <pre>
 * EventRouter router = new EventRouter(new InProcessEventBus());
 * router.register(fraudAgent);   // discovers @OnEvent methods
 * router.start();                 // begins listening
 *
 * // Now fire events:
 * router.publish("payments.incoming", "{\"amount\": 50000}");
 * </pre>
 */
public class EventRouter {

    private final EventSource                        defaultSource;
    private final Map<String, EventSource>           topicSources = new ConcurrentHashMap<>();
    private final List<EventHandlerRegistration>     registrations = new ArrayList<>();
    private final ScheduledExecutorService           scheduler =
        Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "squad-event-scheduler");
            t.setDaemon(true); return t;
        });

    public record EventHandlerRegistration(
        String   topic,
        String   filter,
        String   cron,
        Method   method,
        Object   target,
        int      maxRetries
    ) {}

    public EventRouter(EventSource defaultSource) {
        this.defaultSource = defaultSource;
    }

    /**
     * Register an additional EventSource for a specific topic.
     * Example: register Kafka for "payments.*" but use in-process for "test.*"
     */
    public void registerSource(String topic, EventSource source) {
        topicSources.put(topic, source);
    }

    /**
     * Scan an agent instance for @OnEvent methods and register them.
     */
    public void register(Object agentInstance) {
        Class<?> cls = agentInstance.getClass();
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(OnEvent.class)) continue;
            method.setAccessible(true);
            OnEvent ann = method.getAnnotation(OnEvent.class);
            registrations.add(new EventHandlerRegistration(
                ann.topic(), ann.filter(), ann.cron(),
                method, agentInstance, ann.maxRetries()
            ));
            System.out.printf("[EventRouter] Registered: %s.%s() on topic \"%s\"%s%n",
                cls.getSimpleName(), method.getName(), ann.topic(),
                ann.filter().isEmpty() ? "" : " [filter: " + ann.filter() + "]");
        }
    }

    /**
     * Start all event subscriptions.
     * Call after all agents have been registered.
     */
    public void start() {
        for (EventHandlerRegistration reg : registrations) {
            if (!reg.cron().isEmpty()) {
                startScheduled(reg);
            } else {
                startEventDriven(reg);
            }
        }
        System.out.printf("[EventRouter] Started — %d handler(s) active%n",
            registrations.size());
    }

    /** Publish an event to a topic. */
    public void publish(String topic, String payload) {
        sourceFor(topic).publish(topic, payload);
    }

    /** Publish a pre-built SquadEvent. */
    public void publish(SquadEvent event) {
        EventSource src = sourceFor(event.getTopic());
        if (src instanceof InProcessEventBus bus) {
            bus.publish(event);
        } else {
            src.publish(event.getTopic(), event.getPayload());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        defaultSource.shutdown();
        topicSources.values().forEach(EventSource::shutdown);
    }

    public int registrationCount() { return registrations.size(); }

    // ── Helpers ──────────────────────────────────────────────────

    private void startEventDriven(EventHandlerRegistration reg) {
        Consumer<SquadEvent> handler = event -> {
            // Apply filter if present
            if (!reg.filter().isEmpty() && !matchesFilter(reg.filter(), event)) {
                return; // filtered out
            }
            invokeWithRetry(reg, event);
        };
        sourceFor(reg.topic()).subscribe(reg.topic(), handler);
    }

    private void startScheduled(EventHandlerRegistration reg) {
        // Simple cron: parse "every N seconds" for testing
        // Full cron support would use a library like quartz
        long delayMs = parseCronToDelayMs(reg.cron());
        scheduler.scheduleAtFixedRate(() -> {
            SquadEvent event = new SquadEvent(reg.topic(), "{}", "timer");
            invokeWithRetry(reg, event);
        }, delayMs, delayMs, TimeUnit.MILLISECONDS);
        System.out.printf("[EventRouter] Scheduled: %s.%s() every %dms%n",
            reg.target().getClass().getSimpleName(),
            reg.method().getName(), delayMs);
    }

    private void invokeWithRetry(EventHandlerRegistration reg, SquadEvent event) {
        int attempts = 0;
        while (attempts <= reg.maxRetries()) {
            try {
                // Invoke with or without SquadEvent parameter
                if (reg.method().getParameterCount() == 0) {
                    reg.method().invoke(reg.target());
                } else {
                    reg.method().invoke(reg.target(), event);
                }
                return; // success
            } catch (java.lang.reflect.InvocationTargetException e) {
                attempts++;
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (attempts > reg.maxRetries()) {
                    System.err.printf("[EventRouter] Handler failed after %d attempts: %s%n",
                        attempts, cause.getMessage());
                    return;
                }
                event.incrementRetry();
                System.err.printf("[EventRouter] Retry %d/%d for %s: %s%n",
                    attempts, reg.maxRetries(),
                    reg.method().getName(), cause.getMessage());
                try { Thread.sleep(100L * attempts); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); return;
                }
            } catch (Exception e) {
                System.err.printf("[EventRouter] Error: %s%n", e.getMessage());
                return;
            }
        }
    }

    private boolean matchesFilter(String filter, SquadEvent event) {
        // Simple key=value filter on headers or payload fields
        // Full filter reuses ApprovalEngine condition evaluator
        if (filter.isEmpty()) return true;
        // Parse payload as simple key:value map
        Map<String, String> fields = parsePayloadFields(event.getPayload());
        fields.putAll(event.getHeaders());

        // Split on AND
        for (String part : filter.split(" AND ")) {
            part = part.trim();
            if (!evaluatePart(part, fields)) return false;
        }
        return true;
    }

    private boolean evaluatePart(String expr, Map<String, String> fields) {
        String[] ops = {"<=", ">=", "!=", "<", ">", "=="};
        for (String op : ops) {
            if (!expr.contains(op)) continue;
            String[] parts = expr.split(op, 2);
            if (parts.length != 2) continue;
            String field = parts[0].trim();
            String expected = parts[1].trim();
            String actual = fields.get(field);
            if (actual == null) return false;
            try {
                double a = Double.parseDouble(actual);
                double e = Double.parseDouble(expected);
                return switch (op) {
                    case "<"  -> a < e;
                    case ">"  -> a > e;
                    case "<=" -> a <= e;
                    case ">=" -> a >= e;
                    case "==" -> a == e;
                    case "!=" -> a != e;
                    default   -> false;
                };
            } catch (NumberFormatException e) {
                return switch (op) {
                    case "==" -> actual.equalsIgnoreCase(expected);
                    case "!=" -> !actual.equalsIgnoreCase(expected);
                    default   -> false;
                };
            }
        }
        return false;
    }

    private Map<String, String> parsePayloadFields(String payload) {
        Map<String, String> result = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) return result;
        String inner = payload.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}"))   inner = inner.substring(0, inner.length()-1);
        for (String pair : inner.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"", "");
                String v = kv[1].trim().replace("\"", "");
                result.put(k, v);
            }
        }
        return result;
    }

    private EventSource sourceFor(String topic) {
        return topicSources.getOrDefault(topic, defaultSource);
    }

    private long parseCronToDelayMs(String cron) {
        // Support simple format: "every Ns" -> N*1000ms
        if (cron.startsWith("every ") && cron.endsWith("s")) {
            try {
                int seconds = Integer.parseInt(cron.replace("every ","").replace("s","").trim());
                return seconds * 1000L;
            } catch (NumberFormatException ignored) {}
        }
        return 60_000L; // default 1 minute
    }
}