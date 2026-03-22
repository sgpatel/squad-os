package io.squados.tests;

import io.squados.annotation.AgentRole;
import io.squados.bus.AgentMessage;
import io.squados.bus.MessageType;
import io.squados.bus.RedisAgentBus;
import io.squados.context.SquadRegistry;
import io.squados.health.RedisCircuitBreakerStore;
import io.squados.memory.store.RedisWorkingStore;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Phase 9 — v2.0 Multi-node Squads
 *
 * N01 — RedisAgentBus.publish delivers to subscriber on same node
 * N02 — RedisAgentBus subscriber receives correct AgentMessage fields
 * N03 — RedisAgentBus multiple subscribers all receive the message
 * N04 — RedisAgentBus handler crash does not affect other handlers
 * N05 — RedisAgentBus subscribeAll receives messages from any role
 * N06 — RedisAgentBus unsubscribe stops delivery
 * N07 — RedisAgentBus subscriptionCount reflects active subscriptions
 * N08 — AgentMessage toJson/fromJson roundtrip preserves all fields
 * N09 — SquadRegistry.register writes to Redis
 * N10 — SquadRegistry.find returns live registrations
 * N11 — SquadRegistry.find excludes expired registrations
 * N12 — SquadRegistry.findAll returns agents from all nodes
 * N13 — SquadRegistry.deregisterAll removes all local agents
 * N14 — RedisCircuitBreakerStore.getState defaults to CLOSED
 * N15 — RedisCircuitBreakerStore.setState visible to all nodes
 * N16 — RedisCircuitBreakerStore.recordFailure increments count
 * N17 — RedisCircuitBreakerStore.allowCall respects OPEN state
 */
public class SquadOsPhase9Tests {

    // ── Mock Redis ────────────────────────────────────────────────────
    static class MockRedis implements RedisWorkingStore.RedisCommands,
                                      RedisAgentBus.RedisCommands {
        final Map<String, Map<String,String>> hashes  = new ConcurrentHashMap<>();
        final Map<String, List<Consumer<String>>> subs = new ConcurrentHashMap<>();
        final List<String> published = new ArrayList<>();

        @Override public void hset(String key, String field, String value) {
            hashes.computeIfAbsent(key, k->new ConcurrentHashMap<>()).put(field,value);
        }
        @Override public String hget(String key, String field) {
            return hashes.getOrDefault(key, Map.of()).get(field);
        }
        @Override public Map<String,String> hgetAll(String key) {
            return hashes.getOrDefault(key, Map.of());
        }
        @Override public void hdel(String key, String... fields) {
            Map<String,String> h = hashes.get(key);
            if (h != null) for (String f : fields) h.remove(f);
        }
        @Override public void expire(String key, int seconds) {}
        @Override public Set<String> keys(String pattern) {
            // Simple wildcard: split on * and check prefix/suffix/contains
            String[] parts = pattern.split("\\*", -1);
            Set<String> result = new HashSet<>();
            for (String k : hashes.keySet()) {
                boolean match = true;
                int pos = 0;
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].isEmpty()) continue;
                    int found = k.indexOf(parts[i], pos);
                    if (found < 0) { match = false; break; }
                    if (i == 0 && found != 0) { match = false; break; }
                    pos = found + parts[i].length();
                }
                if (match) result.add(k);
            }
            return result;
        }
        @Override public void del(String key) { hashes.remove(key); }

        // RedisAgentBus.RedisCommands
        @Override public long publish(String channel, String message) {
            published.add(channel + ":" + message);
            List<Consumer<String>> listeners = subs.get(channel);
            if (listeners != null) listeners.forEach(l -> l.accept(message));
            return listeners == null ? 0 : listeners.size();
        }
        @Override public void subscribe(String channel, Consumer<String> callback) {
            subs.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
        @Override public void unsubscribe(String channel) { subs.remove(channel); }
    }

    // ── Runner ────────────────────────────────────────────────────────
    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase9Tests();
        String[] tests = {
            "N01_redisAgentBusDeliverToSubscriber",
            "N02_redisAgentBusCorrectFields",
            "N03_redisAgentBusMultipleSubscribers",
            "N04_redisAgentBusHandlerCrashIsolated",
            "N05_redisAgentBusSubscribeAll",
            "N06_redisAgentBusUnsubscribeStopsDelivery",
            "N07_redisAgentBusSubscriptionCount",
            "N08_agentMessageJsonRoundtrip",
            "N09_squadRegistryRegisterWritesToRedis",
            "N10_squadRegistryFindReturnsLive",
            "N11_squadRegistryFindExcludesExpired",
            "N12_squadRegistryFindAllReturnsAllNodes",
            "N13_squadRegistryDeregisterAll",
            "N14_circuitBreakerDefaultClosed",
            "N15_circuitBreakerSetStateVisible",
            "N16_circuitBreakerRecordFailure",
            "N17_circuitBreakerAllowCallRespectsOpen",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 9 — v2.0 Multi-node Squads  ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");

        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name);
                passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n", name,
                    c.getClass().getSimpleName(), c.getMessage());
                failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage());
                failed++;
            }
        }

        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 9 GATE: FAILED\n"); System.exit(1); }
        else {
            System.out.println("\n  PHASE 9 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v2.0 Multi-node Squads operational.\n");
        }
    }

    // ── Tests ──────────────────────────────────────────────────────────

    void N01_redisAgentBusDeliverToSubscriber() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        AtomicInteger received = new AtomicInteger(0);

        bus.subscribe(AgentRole.TANK, MessageType.HP_CRITICAL,
            msg -> { try { received.incrementAndGet(); } catch(Exception e){} });
        bus.publish(new AgentMessage(AgentRole.TANK, MessageType.HP_CRITICAL, 45));

        assertEquals(1, received.get(), "subscriber received 1 message");
    }

    void N02_redisAgentBusCorrectFields() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        AtomicReference<AgentMessage> captured = new AtomicReference<>();

        bus.subscribe(AgentRole.ANALYST, MessageType.TASK_COMPLETE, msg -> { captured.set(msg); });
        bus.publish(new AgentMessage(AgentRole.ANALYST, MessageType.TASK_COMPLETE, "done"));

        assertNotNull(captured.get(), "message captured");
        assertEquals(AgentRole.ANALYST,       captured.get().getFrom(), "from correct");
        assertEquals(MessageType.TASK_COMPLETE, captured.get().getType(), "type correct");
    }

    void N03_redisAgentBusMultipleSubscribers() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        AtomicInteger count = new AtomicInteger(0);

        bus.subscribe(AgentRole.DPS, MessageType.ATTACK, msg -> { count.incrementAndGet(); });
        bus.subscribe(AgentRole.DPS, MessageType.ATTACK, msg -> { count.incrementAndGet(); });
        bus.subscribe(AgentRole.DPS, MessageType.ATTACK, msg -> { count.incrementAndGet(); });
        bus.publish(new AgentMessage(AgentRole.DPS, MessageType.ATTACK, "strike"));

        assertEquals(3, count.get(), "all 3 subscribers received the message");
    }

    void N04_redisAgentBusHandlerCrashIsolated() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        AtomicInteger goodCount = new AtomicInteger(0);

        bus.subscribe(AgentRole.SUPPORT, MessageType.HEAL,
            msg -> { throw new RuntimeException("intentional crash"); });
        bus.subscribe(AgentRole.SUPPORT, MessageType.HEAL,
            msg -> goodCount.incrementAndGet());

        bus.publish(new AgentMessage(AgentRole.SUPPORT, MessageType.HEAL, 100));
        assertEquals(1, goodCount.get(), "good handler still ran after crash");
    }

    void N05_redisAgentBusSubscribeAll() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        AtomicInteger count = new AtomicInteger(0);

        bus.subscribeAll(MessageType.PING, msg -> { count.incrementAndGet(); });
        bus.publish(new AgentMessage(AgentRole.WILDCARD, MessageType.PING, "ping"));

        assertEquals(1, count.get(), "wildcard subscriber received message");
    }

    void N06_redisAgentBusUnsubscribeStopsDelivery() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        AtomicInteger count = new AtomicInteger(0);

        bus.subscribe(AgentRole.TANK, MessageType.DEFEND, msg -> { count.incrementAndGet(); });
        bus.publish(new AgentMessage(AgentRole.TANK, MessageType.DEFEND, "block"));
        assertEquals(1, count.get(), "received before unsubscribe");

        bus.unsubscribe(AgentRole.TANK, MessageType.DEFEND);
        bus.publish(new AgentMessage(AgentRole.TANK, MessageType.DEFEND, "block2"));
        assertEquals(1, count.get(), "no message after unsubscribe");
    }

    void N07_redisAgentBusSubscriptionCount() {
        MockRedis redis = new MockRedis();
        RedisAgentBus bus = new RedisAgentBus(redis, "squad1");
        assertEquals(0, bus.subscriptionCount(), "empty initially");

        bus.subscribe(AgentRole.ANALYST, MessageType.TASK_COMPLETE, msg -> {});
        bus.subscribe(AgentRole.ANALYST, MessageType.TASK_COMPLETE, msg -> {});
        assertEquals(2, bus.subscriptionCount(), "2 subscriptions");
    }

    void N08_agentMessageJsonRoundtrip() {
        AgentMessage original = new AgentMessage(
            AgentRole.STRATEGIST, MessageType.TASK_COMPLETE, "plan executed");
        String json      = original.toJson();
        AgentMessage copy = AgentMessage.fromJson(json);

        assertNotNull(json,          "json not null");
        assertTrue(json.contains("STRATEGIST"), "json has role");
        assertEquals(AgentRole.STRATEGIST,         copy.getFrom(), "from preserved");
        assertEquals(MessageType.TASK_COMPLETE,    copy.getType(), "type preserved");
    }

    void N09_squadRegistryRegisterWritesToRedis() {
        MockRedis redis = new MockRedis();
        SquadRegistry reg = new SquadRegistry(redis, "squad1", "node-a");
        reg.register(AgentRole.STRATEGIST, "Oracle");

        Set<String> keys = redis.keys("squad:registry:squad1:*");
        assertTrue(keys.size() > 0, "registration key written to Redis");
    }

    void N10_squadRegistryFindReturnsLive() {
        MockRedis redis = new MockRedis();
        SquadRegistry reg = new SquadRegistry(redis, "squad1", "node-a");
        reg.register(AgentRole.STRATEGIST, "Oracle");

        List<SquadRegistry.AgentRegistration> found = reg.find(AgentRole.STRATEGIST);
        assertEquals(1, found.size(), "1 STRATEGIST found");
        assertEquals("Oracle", found.get(0).name(), "name correct");
        assertEquals("node-a", found.get(0).nodeId(), "nodeId correct");
    }

    void N11_squadRegistryFindExcludesExpired() {
        MockRedis redis = new MockRedis();
        SquadRegistry reg = new SquadRegistry(redis, "squad1", "node-a");
        reg.register(AgentRole.ANALYST, "OldAnalyst");

        // Manually write a stale registration (heartbeat 60s ago)
        long staleTime = System.currentTimeMillis() - 60_000;
        String staleJson = "{\"nodeId\":\"node-dead\"," +
            "\"role\":\"ANALYST\"," +
            "\"name\":\"StaleAnalyst\"," +
            "\"registeredAt\":" + staleTime + "," +
            "\"lastHeartbeat\":" + staleTime + "}";
        redis.hset("squad:registry:squad1:node-dead:ANALYST", "data", staleJson);

        List<SquadRegistry.AgentRegistration> found = reg.find(AgentRole.ANALYST);
        assertTrue(found.stream().noneMatch(r -> r.nodeId().equals("node-dead")),
            "stale node-dead excluded");
        assertEquals(1, found.size(), "only fresh node-a returned");
    }

    void N12_squadRegistryFindAllReturnsAllNodes() {
        MockRedis redis = new MockRedis();
        SquadRegistry regA = new SquadRegistry(redis, "squad1", "node-a");
        SquadRegistry regB = new SquadRegistry(redis, "squad1", "node-b");
        regA.register(AgentRole.STRATEGIST, "Oracle");
        regB.register(AgentRole.ANALYST,    "DataBot");

        List<SquadRegistry.AgentRegistration> all = regA.findAll();
        assertEquals(2, all.size(), "both node-a and node-b agents found");
    }

    void N13_squadRegistryDeregisterAll() {
        MockRedis redis = new MockRedis();
        SquadRegistry reg = new SquadRegistry(redis, "squad1", "node-x");
        reg.register(AgentRole.SUPPORT, "Nurse");
        assertEquals(1, reg.findAll().size(), "1 before deregister");

        reg.deregisterAll();
        assertEquals(0, reg.findAll().size(), "0 after deregister");
    }

    void N14_circuitBreakerDefaultClosed() {
        MockRedis redis = new MockRedis();
        RedisCircuitBreakerStore store =
            new RedisCircuitBreakerStore(redis, "squad1");
        assertEquals(RedisCircuitBreakerStore.CircuitState.CLOSED,
            store.getState(AgentRole.STRATEGIST), "default is CLOSED");
    }

    void N15_circuitBreakerSetStateVisible() {
        MockRedis redis = new MockRedis();
        RedisCircuitBreakerStore store =
            new RedisCircuitBreakerStore(redis, "squad1");
        store.setState(AgentRole.ANALYST, RedisCircuitBreakerStore.CircuitState.OPEN);

        // Second instance on same Redis — simulates Node B reading Node A's state
        RedisCircuitBreakerStore storeB =
            new RedisCircuitBreakerStore(redis, "squad1");
        assertEquals(RedisCircuitBreakerStore.CircuitState.OPEN,
            storeB.getState(AgentRole.ANALYST),
            "OPEN state visible to other nodes via Redis");
    }

    void N16_circuitBreakerRecordFailure() {
        MockRedis redis = new MockRedis();
        RedisCircuitBreakerStore store =
            new RedisCircuitBreakerStore(redis, "squad1");
        assertEquals(1, store.recordFailure(AgentRole.EXECUTOR), "count=1 after 1 failure");
        assertEquals(2, store.recordFailure(AgentRole.EXECUTOR), "count=2 after 2 failures");
        assertEquals(3, store.recordFailure(AgentRole.EXECUTOR), "count=3 after 3 failures");
    }

    void N17_circuitBreakerAllowCallRespectsOpen() {
        MockRedis redis = new MockRedis();
        RedisCircuitBreakerStore store =
            new RedisCircuitBreakerStore(redis, "squad1");

        assertTrue(store.allowCall(AgentRole.TANK), "CLOSED allows call");
        store.setState(AgentRole.TANK, RedisCircuitBreakerStore.CircuitState.OPEN);
        assertFalse(store.allowCall(AgentRole.TANK), "OPEN blocks call");
        store.setState(AgentRole.TANK, RedisCircuitBreakerStore.CircuitState.HALF_OPEN);
        assertTrue(store.allowCall(AgentRole.TANK), "HALF_OPEN allows call");
    }

    // ── Helpers ───────────────────────────────────────────────────────
    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return;
        throw new AssertionError(msg + " — expected false");
    }
}
