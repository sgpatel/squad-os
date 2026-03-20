package io.squados.bus;

import io.squados.annotation.AgentRole;
import io.squados.annotation.OnMessage;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The nervous system of SquadOS — routes messages between agents.
 *
 * Analogous to Spring's ApplicationEventPublisher, but typed by
 * AgentRole + MessageType rather than event class.
 *
 * Architecture:
 *   - Agents publish AgentMessage objects to the bus
 *   - The bus resolves topic = "FROM_ROLE.MESSAGE_TYPE"
 *   - All handlers registered for that topic fire synchronously
 *   - WILDCARD subscriptions ("*.MESSAGE_TYPE") fire for every sender
 *
 * Thread safety: ConcurrentHashMap + CopyOnWriteArrayList
 * means publish() is safe to call from multiple agent threads.
 *
 * Phase 3 (in-process bus — no network).
 * Phase 4 upgrade path: swap to Redis Pub/Sub for multi-node squads.
 */
public class AgentMessageBus {

    /** topic -> list of handlers. topic = "ROLE.TYPE" or "*.TYPE" */
    private final Map<String, CopyOnWriteArrayList<MessageHandler>> handlers
        = new ConcurrentHashMap<>();

    /** Full message log for observability / replay */
    private final List<AgentMessage> log = new CopyOnWriteArrayList<>();

    // ── Subscribe ─────────────────────────────────────────────────────

    /**
     * Register a handler for a specific sender + message type.
     * Use AgentRole.WILDCARD as from to match any sender.
     */
    public void subscribe(AgentRole from, MessageType type, MessageHandler handler) {
        String topic = topicKey(from, type.name());
        handlers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    /**
     * Scan an agent instance for @OnMessage annotations and auto-register
     * all annotated methods as subscribers. Called by SquadContext during boot.
     */
    public void registerListeners(Object agentInstance) {
        for (Method m : agentInstance.getClass().getDeclaredMethods()) {
            OnMessage ann = m.getAnnotation(OnMessage.class);
            if (ann == null) continue;
            m.setAccessible(true);
            subscribe(ann.from(), ann.type(), msg -> {
                try {
                    m.invoke(agentInstance, msg);
                } catch (Exception e) {
                    System.err.printf("[SquadOS] @OnMessage error in %s.%s: %s%n",
                        agentInstance.getClass().getSimpleName(),
                        m.getName(), e.getCause() != null
                            ? e.getCause().getMessage() : e.getMessage());
                }
            });
        }
    }

    // ── Publish ───────────────────────────────────────────────────────

    /**
     * Publish a message to all matching subscribers.
     *
     * Routing:
     *   1. Exact match: handlers registered for message.from + message.type
     *   2. Wildcard match: handlers registered for WILDCARD + message.type
     *   3. Both fire if present — exact fires first
     */
    public void publish(AgentMessage message) {
        log.add(message);

        // 1. Exact topic match
        String exact    = message.topic();
        String wildcard = topicKey(AgentRole.WILDCARD, message.getType().name());

        fire(exact,    message);
        if (!exact.equals(wildcard)) fire(wildcard, message);
    }

    /** Convenience — publish with auto-built message */
    public void publish(AgentRole from, MessageType type, Object payload) {
        publish(new AgentMessage(from, type, payload));
    }

    /** Directed publish — only fires handlers registered for the 'to' agent */
    public void publishTo(AgentRole from, AgentRole to,
                          MessageType type, Object payload) {
        AgentMessage msg = new AgentMessage(from, to, type, payload);
        log.add(msg);
        // For directed messages: fire exact + wildcard, handlers filter by role if needed
        fire(msg.topic(), msg);
        fire(topicKey(AgentRole.WILDCARD, type.name()), msg);
    }

    // ── Observability ─────────────────────────────────────────────────

    /** All messages published since bus creation */
    public List<AgentMessage> getLog() {
        return Collections.unmodifiableList(log);
    }

    /** Messages of a specific type */
    public List<AgentMessage> getLog(MessageType type) {
        return log.stream()
            .filter(m -> m.getType() == type)
            .toList();
    }

    /** Messages from a specific agent */
    public List<AgentMessage> getLogFrom(AgentRole role) {
        return log.stream()
            .filter(m -> m.getFrom() == role)
            .toList();
    }

    public int publishedCount()              { return log.size(); }
    public int subscriberCount()             { return handlers.values().stream().mapToInt(List::size).sum(); }
    public void clearLog()                   { log.clear(); }

    // ── Internals ─────────────────────────────────────────────────────

    private void fire(String topic, AgentMessage msg) {
        List<MessageHandler> subs = handlers.get(topic);
        if (subs == null || subs.isEmpty()) return;
        for (MessageHandler h : subs) {
            try { h.handle(msg); }
            catch (Exception e) {
                System.err.printf("[SquadOS] Bus handler error on topic %s: %s%n",
                    topic, e.getMessage());
            }
        }
    }

    private static String topicKey(AgentRole from, String typeName) {
        return from.name() + "." + typeName;
    }
}
