package io.squados.context;

import io.squados.annotation.AgentRole;
import io.squados.memory.store.RedisWorkingStore;

import java.util.*;
import java.util.concurrent.*;

/**
 * Distributed agent registry backed by Redis.
 *
 * Each node registers its agents on startup with a TTL heartbeat.
 * Any node can discover agents running on any other node.
 *
 * Redis key format:
 *   squad:registry:{squadId}:{nodeId}:{role} -> AgentRegistration JSON
 *   TTL: 30 seconds, refreshed every 10 seconds by NodeHeartbeat
 *
 * Usage:
 * <pre>
 * SquadRegistry registry = new SquadRegistry(redis, "my-squad", "node-a");
 * registry.register(AgentRole.STRATEGIST, "Oracle", "node-a");
 * List<AgentRegistration> analysts = registry.find(AgentRole.ANALYST);
 * </pre>
 */
public class SquadRegistry {

    /** Immutable snapshot of a registered agent. */
    public record AgentRegistration(
        String    nodeId,
        AgentRole role,
        String    name,
        long      registeredAt,
        long      lastHeartbeat
    ) {
        public boolean isAlive(long nowMs, long ttlMs) {
            return (nowMs - lastHeartbeat) < ttlMs;
        }

        public String toJson() {
            return "{\"nodeId\":\"" + nodeId + "\"," +
                   "\"role\":\"" + role.name() + "\"," +
                   "\"name\":\"" + name + "\"," +
                   "\"registeredAt\":" + registeredAt + "," +
                   "\"lastHeartbeat\":" + lastHeartbeat + "}";
        }

        public static AgentRegistration fromJson(String json) {
            return new AgentRegistration(
                extract(json, "nodeId"),
                AgentRole.valueOf(extract(json, "role")),
                extract(json, "name"),
                Long.parseLong(extractNum(json, "registeredAt")),
                Long.parseLong(extractNum(json, "lastHeartbeat"))
            );
        }
    }

    private static final int TTL_SECONDS  = 30;
    private static final int BEAT_SECONDS = 10;

    private final RedisWorkingStore.RedisCommands redis;
    private final String                          squadId;
    private final String                          nodeId;
    private final ScheduledExecutorService        heartbeatPool;
    private final Map<AgentRole, AgentRegistration> local = new ConcurrentHashMap<>();

    public SquadRegistry(RedisWorkingStore.RedisCommands redis,
                         String squadId, String nodeId) {
        this.redis        = redis;
        this.squadId      = squadId;
        this.nodeId       = nodeId;
        this.heartbeatPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "squad-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Register a local agent and start heartbeat.
     * Call once per agent on node startup.
     */
    public void register(AgentRole role, String name) {
        AgentRegistration reg = new AgentRegistration(
            nodeId, role, name,
            System.currentTimeMillis(), System.currentTimeMillis()
        );
        local.put(role, reg);
        writeToRedis(reg);
        System.out.printf("[SquadRegistry] Registered %s [%s] on node %s%n",
            name, role, nodeId);
    }

    /**
     * Find all live registrations for a given role across all nodes.
     */
    public List<AgentRegistration> find(AgentRole role) {
        String pattern = registryKey("*", role);
        Set<String> keys = redis.keys(pattern);
        List<AgentRegistration> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String key : keys) {
            String json = redis.hget(key, "data");
            if (json == null) continue;
            try {
                AgentRegistration reg = AgentRegistration.fromJson(json);
                if (reg.isAlive(now, TTL_SECONDS * 1000L)) {
                    results.add(reg);
                }
            } catch (Exception e) {
                System.err.printf("[SquadRegistry] Bad registration at %s: %s%n",
                    key, e.getMessage());
            }
        }
        return results;
    }

    /** Find all live agents across all roles. */
    public List<AgentRegistration> findAll() {
        String pattern = "squad:registry:" + squadId + ":*";
        Set<String> keys = redis.keys(pattern);
        List<AgentRegistration> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String key : keys) {
            String json = redis.hget(key, "data");
            if (json == null) continue;
            try {
                AgentRegistration reg = AgentRegistration.fromJson(json);
                if (reg.isAlive(now, TTL_SECONDS * 1000L)) results.add(reg);
            } catch (Exception ignored) {}
        }
        return results;
    }

    /** Start periodic heartbeat — keeps registrations alive in Redis. */
    public void startHeartbeat() {
        heartbeatPool.scheduleAtFixedRate(() -> {
            local.forEach((role, reg) -> {
                AgentRegistration refreshed = new AgentRegistration(
                    reg.nodeId(), reg.role(), reg.name(),
                    reg.registeredAt(), System.currentTimeMillis()
                );
                local.put(role, refreshed);
                writeToRedis(refreshed);
            });
        }, BEAT_SECONDS, BEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** Deregister all local agents (call on shutdown). */
    public void deregisterAll() {
        heartbeatPool.shutdown();
        local.forEach((role, reg) -> {
            redis.del(registryKey(nodeId, role));
        });
        local.clear();
        System.out.printf("[SquadRegistry] Node %s deregistered from squad %s%n",
            nodeId, squadId);
    }

    public String getNodeId() { return nodeId; }

    // ── Helpers ───────────────────────────────────────────────────

    private void writeToRedis(AgentRegistration reg) {
        String key = registryKey(reg.nodeId(), reg.role());
        redis.hset(key, "data", reg.toJson());
        redis.expire(key, TTL_SECONDS);
    }

    private String registryKey(String nId, AgentRole role) {
        return "squad:registry:" + squadId + ":" + nId + ":" + role.name();
    }

    private static String extract(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private static String extractNum(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return "0";
        start += search.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        return end < 0 ? "0" : json.substring(start, end).trim();
    }
}
