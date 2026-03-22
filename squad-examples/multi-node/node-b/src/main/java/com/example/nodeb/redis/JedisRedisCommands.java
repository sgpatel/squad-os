package com.example.nodeb.redis;

import io.squados.bus.RedisAgentBus;
import io.squados.memory.store.RedisWorkingStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class JedisRedisCommands
    implements RedisAgentBus.RedisCommands,
               RedisWorkingStore.RedisCommands {

    private final JedisPool pool;
    private final ExecutorService subPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "redis-sub"); t.setDaemon(true); return t;
    });

    public JedisRedisCommands(String host, int port) {
        this.pool = new JedisPool(host, port);
        System.out.printf("[Redis] Connected to %s:%d%n", host, port);
    }

    @Override public long publish(String ch, String msg) {
        try (Jedis j = pool.getResource()) { return j.publish(ch, msg); }
    }
    @Override public void subscribe(String channel, Consumer<String> callback) {
        subPool.submit(() -> {
            try (Jedis j = pool.getResource()) {
                j.subscribe(new JedisPubSub() {
                    @Override public void onMessage(String ch, String message) {
                        callback.accept(message);
                    }
                }, channel);
            } catch (Exception e) {
                System.err.printf("[Redis] Sub error %s: %s%n", channel, e.getMessage());
            }
        });
    }
    @Override public void unsubscribe(String channel) {}
    @Override public void hset(String k, String f, String v) {
        try (Jedis j = pool.getResource()) { j.hset(k, f, v); }
    }
    @Override public String hget(String k, String f) {
        try (Jedis j = pool.getResource()) { return j.hget(k, f); }
    }
    @Override public Map<String,String> hgetAll(String k) {
        try (Jedis j = pool.getResource()) { return j.hgetAll(k); }
    }
    @Override public void hdel(String k, String... f) {
        try (Jedis j = pool.getResource()) { j.hdel(k, f); }
    }
    @Override public void expire(String k, int s) {
        try (Jedis j = pool.getResource()) { j.expire(k, s); }
    }
    @Override public Set<String> keys(String p) {
        try (Jedis j = pool.getResource()) { return j.keys(p); }
    }
    @Override public void del(String k) {
        try (Jedis j = pool.getResource()) { j.del(k); }
    }
}