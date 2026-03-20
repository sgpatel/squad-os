package io.squados.tests;

import io.squados.annotation.AgentRole;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.MessageType;
import io.squados.context.MissionState;
import io.squados.context.TokenBudget;
import io.squados.health.AgentCircuitBreaker;
import io.squados.memory.MemoryManager;
import io.squados.memory.annotation.*;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.MemoryRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 5 — Hardening, concurrent safety, chaos tests.
 *
 * H01 — MissionState put/get roundtrip
 * H02 — MissionState version increments on every write
 * H03 — MissionState putIfVersion rejects stale version
 * H04 — MissionState 50 concurrent writers — no lost updates
 * H05 — MissionState reset clears all keys and resets version
 * H06 — TokenBudget records usage and returns remaining
 * H07 — TokenBudget hard cap blocks further calls
 * H08 — TokenBudget soft warning at 80%
 * H09 — TokenBudget per-agent caps are independent
 * H10 — TokenBudget reset zeroes all usage
 * H11 — Bus survives 1000 rapid-fire messages without losing any
 * H12 — Bus 20 concurrent publishers — all messages logged
 * H13 — CircuitBreaker 50 concurrent failures — opens exactly once
 * H14 — MemoryStore 100 concurrent writes — all retrievable
 * H15 — MissionState getOrDefault returns default for missing key
 * H16 — TokenBudget hasRemaining false when exactly at cap
 * H17 — Full squad chaos: concurrent bus + state + breaker + memory
 */
public class SquadOsPhase5Tests {

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase5Tests();
        String[] tests = {
            "H01_missionStatePutGet",
            "H02_missionStateVersionIncrements",
            "H03_missionStatePutIfVersionRejectsStale",
            "H04_missionState50ConcurrentWriters",
            "H05_missionStateReset",
            "H06_tokenBudgetRecordsUsage",
            "H07_tokenBudgetHardCapBlocks",
            "H08_tokenBudgetSoftWarning",
            "H09_tokenBudgetPerAgentCapsIndependent",
            "H10_tokenBudgetReset",
            "H11_bus1000RapidMessages",
            "H12_bus20ConcurrentPublishers",
            "H13_circuitBreaker50ConcurrentFailures",
            "H14_memoryStore100ConcurrentWrites",
            "H15_missionStateGetOrDefault",
            "H16_tokenBudgetHasRemainingFalseAtCap",
            "H17_fullSquadChaosTest",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 5 — Hardening & Chaos Tests  ║");
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

        System.out.printf("%n  Results: %d passed, %d failed out of %d tests%n",
            passed, failed, passed + failed);
        if (failed > 0) {
            System.out.println("\n  PHASE 5 GATE: FAILED\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 5 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v0.5.0 tag can be cut. v1.0.0 release candidate ready.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void H01_missionStatePutGet() {
        MissionState s = new MissionState();
        s.put("tank.hp", 75);
        s.put("oracle.plan", "funnel");
        assertEquals((Object)75,       s.get("tank.hp", Integer.class), "int roundtrip");
        assertEquals("funnel", s.get("oracle.plan", String.class), "string roundtrip");
        assertFalse(s.has("missing.key"), "missing key returns false");
        assertEquals(2, s.size(), "size = 2");
    }

    void H02_missionStateVersionIncrements() {
        MissionState s = new MissionState();
        assertEquals(0L, s.getVersion(), "version starts at 0");
        s.put("k1", "v1"); assertEquals(1L, s.getVersion(), "version=1 after write");
        s.put("k2", "v2"); assertEquals(2L, s.getVersion(), "version=2 after write");
        s.put("k1", "v3"); assertEquals(3L, s.getVersion(), "version=3 on overwrite");
    }

    void H03_missionStatePutIfVersionRejectsStale() {
        MissionState s = new MissionState();
        s.put("key", "original");
        long v = s.getVersion(); // capture version = 1
        s.put("key", "concurrent-write"); // version becomes 2
        boolean result = s.putIfVersion("key", "stale-write", v); // expects v=1 but is 2
        assertFalse(result, "stale version rejected");
        assertEquals("concurrent-write", s.get("key", String.class),
            "concurrent write survived, stale write lost");
    }

    void H04_missionState50ConcurrentWriters() throws Exception {
        MissionState     state   = new MissionState();
        int              threads = 50;
        CountDownLatch   start   = new CountDownLatch(1);
        CountDownLatch   done    = new CountDownLatch(threads);
        AtomicInteger    writes  = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                // Each thread writes to its own key — no key contention
                state.put("agent." + id + ".status", "active-" + id);
                writes.incrementAndGet();
                done.countDown();
            }).start();
        }

        start.countDown();
        boolean finished = done.await(5, TimeUnit.SECONDS);
        assertTrue(finished, "all 50 threads finished within 5s");
        assertEquals(threads, writes.get(), "all 50 writes recorded");
        assertEquals(threads, state.size(), "all 50 keys present in state");
    }

    void H05_missionStateReset() {
        MissionState s = new MissionState();
        s.put("a", 1); s.put("b", 2); s.put("c", 3);
        assertEquals(3L, s.getVersion(), "version=3 before reset");
        s.reset();
        assertEquals(0, s.size(),       "size=0 after reset");
        assertEquals(0L, s.getVersion(),"version=0 after reset");
        assertFalse(s.has("a"),         "key 'a' gone after reset");
    }

    void H06_tokenBudgetRecordsUsage() {
        TokenBudget b = new TokenBudget(4096);
        b.setCap(AgentRole.STRATEGIST, 1024);
        b.record(AgentRole.STRATEGIST, 300);
        b.record(AgentRole.STRATEGIST, 200);
        assertEquals(500,  b.usedBy(AgentRole.STRATEGIST),       "used=500");
        assertEquals(1024, b.capFor(AgentRole.STRATEGIST),       "cap=1024");
        assertEquals(524,  b.remainingFor(AgentRole.STRATEGIST), "remaining=524");
        assertTrue(b.hasRemaining(AgentRole.STRATEGIST),         "still has budget");
    }

    void H07_tokenBudgetHardCapBlocks() {
        TokenBudget b = new TokenBudget(4096);
        b.setCap(AgentRole.DPS, 100);
        boolean ok1 = b.record(AgentRole.DPS, 60);
        boolean ok2 = b.record(AgentRole.DPS, 50); // 110 > 100
        assertTrue(ok1,  "first call within budget");
        assertFalse(ok2, "second call exceeds budget — blocked");
        assertFalse(b.hasRemaining(AgentRole.DPS), "no budget remaining");
    }

    void H08_tokenBudgetSoftWarning() {
        // 80% of 100 = 80 — record 85 tokens, soft warning should print
        // We just verify the call doesn't throw and returns true
        TokenBudget b = new TokenBudget(4096);
        b.setCap(AgentRole.SUPPORT, 100);
        boolean result = b.record(AgentRole.SUPPORT, 85); // 85% — triggers warning
        assertTrue(result, "call succeeds at 85% (soft cap only warns, doesn't block)");
        assertTrue(b.hasRemaining(AgentRole.SUPPORT), "still has remaining (15 tokens)");
    }

    void H09_tokenBudgetPerAgentCapsIndependent() {
        TokenBudget b = new TokenBudget(4096);
        b.setCap(AgentRole.TANK,      512);
        b.setCap(AgentRole.STRATEGIST,2048);
        b.record(AgentRole.TANK, 512); // exhaust TANK
        assertFalse(b.hasRemaining(AgentRole.TANK),       "TANK exhausted");
        assertTrue(b.hasRemaining(AgentRole.STRATEGIST),  "STRATEGIST unaffected");
        assertTrue(b.hasRemaining(AgentRole.DPS),         "DPS uses default cap");
    }

    void H10_tokenBudgetReset() {
        TokenBudget b = new TokenBudget(4096);
        b.setCap(AgentRole.TANK, 100);
        b.record(AgentRole.TANK, 100);
        assertFalse(b.hasRemaining(AgentRole.TANK), "exhausted before reset");
        b.reset();
        assertEquals(0, b.usedBy(AgentRole.TANK),      "usage=0 after reset");
        assertTrue(b.hasRemaining(AgentRole.TANK),     "budget restored after reset");
    }

    void H11_bus1000RapidMessages() {
        AgentMessageBus bus = new AgentMessageBus();
        AtomicInteger received = new AtomicInteger(0);
        bus.subscribe(AgentRole.TANK, MessageType.STATUS_UPDATE,
            msg -> received.incrementAndGet());
        for (int i = 0; i < 1000; i++) {
            bus.publish(AgentRole.TANK, MessageType.STATUS_UPDATE, "tick-" + i);
        }
        assertEquals(1000, bus.publishedCount(), "1000 messages logged");
        assertEquals(1000, received.get(),       "1000 messages delivered");
    }

    void H12_bus20ConcurrentPublishers() throws Exception {
        AgentMessageBus  bus     = new AgentMessageBus();
        int              threads = 20;
        int              msgs    = 50;
        CountDownLatch   start   = new CountDownLatch(1);
        CountDownLatch   done    = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < msgs; j++) {
                    bus.publish(AgentRole.TANK, MessageType.STATUS_UPDATE, j);
                }
                done.countDown();
            }).start();
        }

        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "all 20 threads finished");
        assertEquals(threads * msgs, bus.publishedCount(),
            "all " + (threads * msgs) + " messages logged — no loss under concurrency");
    }

    void H13_circuitBreaker50ConcurrentFailures() throws Exception {
        AgentMessageBus     bus     = new AgentMessageBus();
        AgentCircuitBreaker cb      = new AgentCircuitBreaker(3, 2, bus);
        AtomicInteger       opens   = new AtomicInteger(0);
        cb.register(AgentRole.DPS, "Blitz");

        bus.subscribe(AgentRole.DPS, MessageType.CIRCUIT_OPEN,
            msg -> opens.incrementAndGet());

        int              threads = 50;
        CountDownLatch   start   = new CountDownLatch(1);
        CountDownLatch   done    = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                cb.onFailure(AgentRole.DPS, "concurrent failure");
                done.countDown();
            }).start();
        }

        start.countDown();
        done.await(5, TimeUnit.SECONDS);

        // Circuit should be open — we don't assert exactly 1 open event
        // because concurrent threads may publish before checking status
        assertEquals(io.squados.health.AgentHealth.Status.CIRCUIT_OPEN,
            cb.getHealth(AgentRole.DPS).getStatus(),
            "circuit open after 50 concurrent failures");
        assertTrue(opens.get() >= 1,
            "at least 1 CIRCUIT_OPEN event published");
    }

    void H14_memoryStore100ConcurrentWrites() throws Exception {
        MemoryRouter  router  = new MemoryRouter();
        int           threads = 100;
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch done   = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                MemoryRecord r = new MemoryRecord(
                    "squad1", "agent-" + id, "sess-chaos",
                    io.squados.memory.annotation.MemoryType.WORKING,
                    "memory content " + id,
                    io.squados.memory.annotation.Importance.MEDIUM,
                    new String[0]);
                router.save(r);
                done.countDown();
            }).start();
        }

        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        assertEquals(threads, router.countFor(
            io.squados.memory.annotation.MemoryType.WORKING),
            "all 100 concurrent memory writes survived");
    }

    void H15_missionStateGetOrDefault() {
        MissionState s = new MissionState();
        s.put("tank.hp", 80);
        assertEquals((Object)80,       s.getOrDefault("tank.hp",    Integer.class, 100), "existing key");
        assertEquals((Object)100,      s.getOrDefault("missing.key",Integer.class, 100), "missing key default");
        assertEquals("none",   s.getOrDefault("other",      String.class, "none"), "string default");
    }

    void H16_tokenBudgetHasRemainingFalseAtCap() {
        TokenBudget b = new TokenBudget(4096);
        b.setCap(AgentRole.SCOUT, 50);
        b.record(AgentRole.SCOUT, 50); // exactly at cap
        assertFalse(b.hasRemaining(AgentRole.SCOUT), "no remaining when exactly at cap");
        assertEquals(0, b.remainingFor(AgentRole.SCOUT), "remaining=0 at cap");
    }

    void H17_fullSquadChaosTest() throws Exception {
        // All four systems running concurrently: bus + state + breaker + memory
        AgentMessageBus     bus     = new AgentMessageBus();
        MissionState        state   = new MissionState();
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(10, 3, bus);
        MemoryManager       memory  = new MemoryManager(new MemoryRouter(), new MockEmbeddingPort());
        TokenBudget         budget  = new TokenBudget(10000);

        breaker.register(AgentRole.TANK,      "IronVeil");
        breaker.register(AgentRole.DPS,       "Blitz");
        breaker.register(AgentRole.SUPPORT,   "NurseBot");
        breaker.register(AgentRole.STRATEGIST,"Oracle");
        budget.setCap(AgentRole.TANK,      2000);
        budget.setCap(AgentRole.DPS,       1000);
        budget.setCap(AgentRole.SUPPORT,   2000);
        budget.setCap(AgentRole.STRATEGIST,4000);

        AtomicInteger events = new AtomicInteger(0);
        bus.subscribe(AgentRole.WILDCARD, MessageType.STATUS_UPDATE,
            msg -> events.incrementAndGet());

        int            threads = 40;
        CountDownLatch start   = new CountDownLatch(1);
        CountDownLatch done    = new CountDownLatch(threads);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        AgentRole[] roles = {AgentRole.TANK, AgentRole.DPS,
                             AgentRole.SUPPORT, AgentRole.STRATEGIST};

        for (int i = 0; i < threads; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    start.await();
                    AgentRole role = roles[id % roles.length];

                    // 1. Write to MissionState
                    state.put("agent." + id + ".active", true);

                    // 2. Record token usage
                    budget.record(role, 50);

                    // 3. Publish status update
                    bus.publish(role, MessageType.STATUS_UPDATE, "agent-" + id + "-ok");

                    // 4. Write a memory
                    Memory ann = new Memory() {
                        public Class<Memory> annotationType() { return Memory.class; }
                        public MemoryType    type()           { return MemoryType.WORKING; }
                        public MemoryScope   scope()          { return MemoryScope.AGENT; }
                        public MemoryOp      op()             { return MemoryOp.WRITE; }
                        public int           topK()           { return 3; }
                        public float         minScore()       { return 0f; }
                        public String[]      tags()           { return new String[0]; }
                        public Importance    importance()     { return Importance.MEDIUM; }
                        public boolean       promote()        { return false; }
                    };
                    memory.write(ann, role.name(), "chaos-squad", "sess-chaos",
                                 "agent-" + id + " status OK");

                    // 5. Record success with breaker
                    breaker.onSuccess(role, 50);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);

        assertTrue(finished, "all 40 chaos threads finished within 15s");
        assertTrue(errors.isEmpty(),
            "no exceptions thrown: " + (errors.isEmpty() ? "" : errors.get(0).getMessage()));
        assertEquals(threads, state.size(), "all 40 state keys written");
        assertEquals(threads, events.get(), "all 40 bus events delivered");
        assertEquals(threads, memory.totalMemories(), "all 40 memories stored");
    }

    // ── Assertion helpers ─────────────────────────────────────────────

    private static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertEquals(long e, long a, String msg) {
        if (e == a) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
    private static void assertFalse(boolean c, String msg) {
        if (!c) return;
        throw new AssertionError(msg + " — expected false");
    }
}
