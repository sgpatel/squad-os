package com.example.nodea.redis;

import io.squados.bus.RedisAgentBus;
import io.squados.memory.store.RedisWorkingStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Production Redis adapter using Jedis.
 * Implements both RedisAgentBus.RedisCommands and RedisWorkingStore.RedisCommands.
 *
 * Used by:
 *   - RedisAgentBus (messaging between nodes)
 *   - SquadRegistry (distributed agent discovery)
 *   - RedisCircuitBreakerStore (distributed circuit state)
 */
public class JedisRedisCommands
    implements RedisAgentBus.RedisCommands,
               RedisWorkingStore.RedisCommands {

    private final JedisPool pool;
    private final ExecutorService subPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "redis-sub");
        t.setDaemon(true);
        return t;
    });

    public JedisRedisCommands(String host, int port) {
        this.pool = new JedisPool(host, port);
        System.out.printf("[Redis] Connected to %s:%d%n", host, port);
    }

    // ── RedisAgentBus.RedisCommands ───────────────────────────────

    @Override
    public long publish(String channel, String message) {
        try (Jedis j = pool.getResource()) {
            return j.publish(channel, message);
        }
    }

    @Override
    public void subscribe(String channel, Consumer<String> callback) {
        subPool.submit(() -> {
            try (Jedis j = pool.getResource()) {
                j.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String message) {
                        callback.accept(message);
                    }
                }, channel);
            } catch (Exception e) {
                System.err.printf("[Redis] Subscribe error on %s: %s%n",
                    channel, e.getMessage());
            }
        });
    }

    @Override
    public void unsubscribe(String channel) {
        // Jedis handles unsubscribe via JedisPubSub.unsubscribe()
        // For simplicity in this demo, subscriptions are long-lived
    }

    // ── RedisWorkingStore.RedisCommands ───────────────────────────

    @Override
    public void hset(String key, String field, String value) {
        try (Jedis j = pool.getResource()) { j.hset(key, field, value); }
    }

    @Override
    public String hget(String key, String field) {
        try (Jedis j = pool.getResource()) { return j.hget(key, field); }
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        try (Jedis j = pool.getResource()) { return j.hgetAll(key); }
    }

    @Override
    public void hdel(String key, String... fields) {
        try (Jedis j = pool.getResource()) { j.hdel(key, fields); }
    }

    @Override
    public void expire(String key, int seconds) {
        try (Jedis j = pool.getResource()) { j.expire(key, seconds); }
    }

    @Override
    public Set<String> keys(String pattern) {
        try (Jedis j = pool.getResource()) { return j.keys(pattern); }
    }

    @Override
    public void del(String key) {
        try (Jedis j = pool.getResource()) { j.del(key); }
    }

    public void close() { pool.close(); }
}
