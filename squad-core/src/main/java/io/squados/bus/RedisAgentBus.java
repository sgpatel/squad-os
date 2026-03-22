package io.squados.bus;

import io.squados.annotation.AgentRole;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Multi-node AgentMessageBus backed by Redis Pub/Sub.
 * Drop-in replacement for AgentMessageBus — messages fan out across all JVMs.
 *
 * Channel: squad:{squadId}:{fromRole}.{messageType}
 */
public class RedisAgentBus {

    public interface RedisCommands {
        long publish(String channel, String message);
        void subscribe(String channel, Consumer<String> callback);
        void unsubscribe(String channel);
    }

    private final RedisCommands                     redis;
    private final String                            squadId;
    private final Map<String,List<MessageHandler>>  handlers  = new ConcurrentHashMap<>();
    private final Map<String,Consumer<String>>       listeners = new ConcurrentHashMap<>();

    public RedisAgentBus(RedisCommands redis, String squadId) {
        this.redis   = redis;
        this.squadId = squadId;
    }

    public void subscribe(AgentRole from, MessageType type, MessageHandler handler) {
        String channel  = channel(from, type);
        String topicKey = topicKey(from, type);
        handlers.computeIfAbsent(topicKey, k -> new CopyOnWriteArrayList<>()).add(handler);
        if (!listeners.containsKey(channel)) {
            Consumer<String> listener = json -> dispatch(topicKey, AgentMessage.fromJson(json));
            listeners.put(channel, listener);
            redis.subscribe(channel, listener);
        }
    }

    public void subscribeAll(MessageType type, MessageHandler handler) {
        subscribe(AgentRole.WILDCARD, type, handler);
    }

    public void publish(AgentMessage message) {
        redis.publish(channel(message.getFrom(), message.getType()), message.toJson());
        if (message.getFrom() != AgentRole.WILDCARD) {
            redis.publish(channel(AgentRole.WILDCARD, message.getType()), message.toJson());
        }
    }

    public void unsubscribe(AgentRole from, MessageType type) {
        String channel  = channel(from, type);
        String topicKey = topicKey(from, type);
        handlers.remove(topicKey);
        listeners.remove(channel);
        redis.unsubscribe(channel);
    }

    public int subscriptionCount() {
        return handlers.values().stream().mapToInt(List::size).sum();
    }

    private void dispatch(String topicKey, AgentMessage message) {
        List<MessageHandler> hs = handlers.get(topicKey);
        if (hs == null) return;
        for (MessageHandler h : hs) {
            try { h.handle(message); }
            catch (Exception e) {
                System.err.printf("[RedisAgentBus] Handler error on %s: %s%n",
                    topicKey, e.getMessage());
            }
        }
    }

    private String channel(AgentRole from, MessageType type) {
        return "squad:" + squadId + ":" + from.name() + "." + type.name();
    }
    private String topicKey(AgentRole from, MessageType type) {
        return from.name() + "." + type.name();
    }
}
