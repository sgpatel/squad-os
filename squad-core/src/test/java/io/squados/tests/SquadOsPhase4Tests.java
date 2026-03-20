package io.squados.tests;

import io.squados.annotation.AgentRole;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.MessageType;
import io.squados.health.AgentCircuitBreaker;
import io.squados.health.AgentHealth;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 4 test suite — CircuitBreaker, health monitoring, MissionProfile.
 *
 * C01 — AgentHealth records success: resets failureCount, updates latency
 * C02 — AgentHealth records failure: increments failureCount, stores error
 * C03 — AgentHealth avgLatencyMs computed correctly
 * C04 — CircuitBreaker allows calls when HEALTHY
 * C05 — CircuitBreaker transitions to DEGRADED at half threshold
 * C06 — CircuitBreaker opens circuit at failure threshold
 * C07 — CircuitBreaker publishes CIRCUIT_OPEN event to bus
 * C08 — CircuitBreaker blocks calls when circuit is OPEN
 * C09 — CircuitBreaker resets to HEALTHY after success
 * C10 — CircuitBreaker allows unregistered agent calls (safe default)
 * C11 — Multiple agents tracked independently
 * C12 — onSuccess after CIRCUIT_OPEN closes the circuit
 * C13 — CircuitBreaker.allHealth returns all registered agents
 * C14 — AgentHealth.Status enum has three values
 * C15 — CIRCUIT_OPEN event carries meaningful payload
 * C16 — MissionProfile annotation readable at runtime
 * C17 — Full scenario: 3 failures open circuit, 1 success begins recovery
 */
public class SquadOsPhase4Tests {

    @io.squados.annotation.MissionProfile("gaming")
    static class GamingAgent {}

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase4Tests();
        String[] tests = {
            "C01_healthRecordsSuccess",
            "C02_healthRecordsFailure",
            "C03_healthAvgLatency",
            "C04_breakerAllowsHealthyCalls",
            "C05_breakerTransitionsToDegraded",
            "C06_breakerOpensAtThreshold",
            "C07_breakerPublishesCircuitOpenEvent",
            "C08_breakerBlocksOpenCircuit",
            "C09_breakerResetsAfterSuccess",
            "C10_breakerAllowsUnregisteredAgent",
            "C11_multipleAgentsTrackedIndependently",
            "C12_successAfterOpenBeginsRecovery",
            "C13_allHealthReturnsAllAgents",
            "C14_healthStatusHasThreeValues",
            "C15_circuitOpenEventHasPayload",
            "C16_missionProfileAnnotationReadable",
            "C17_fullCircuitScenario",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 4 — Circuit Breaker Tests    ║");
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
            System.out.println("\n  PHASE 4 GATE: FAILED\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 4 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v0.4.0 tag can be cut. Phase 5 (hardening) can begin.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void C01_healthRecordsSuccess() {
        AgentHealth h = new AgentHealth(AgentRole.TANK, "IronVeil");
        h.recordSuccess(120);
        assertEquals(0, h.getFailureCount(), "failure count reset to 0");
        assertEquals(1, h.getCallCount(),    "call count incremented");
        assertEquals(AgentHealth.Status.HEALTHY, h.getStatus(), "status HEALTHY");
        assertNotNull(h.getLastCallAt(), "lastCallAt set");
    }

    void C02_healthRecordsFailure() {
        AgentHealth h = new AgentHealth(AgentRole.DPS, "Blitz");
        h.recordFailure("LLM timeout");
        assertEquals(1, h.getFailureCount(), "failure count = 1");
        assertEquals(1, h.getCallCount(),    "call count = 1");
        assertEquals("LLM timeout", h.getLastError(), "error stored");
    }

    void C03_healthAvgLatency() {
        AgentHealth h = new AgentHealth(AgentRole.SUPPORT, "NurseBot");
        h.recordSuccess(100);
        h.recordSuccess(200);
        h.recordSuccess(300);
        assertEquals(200L, h.avgLatencyMs(), "avg latency = (100+200+300)/3 = 200ms");
        // Zero calls edge case
        AgentHealth empty = new AgentHealth(AgentRole.SCOUT, "Wraith");
        assertEquals(0L, empty.avgLatencyMs(), "avg latency = 0 when no calls");
    }

    void C04_breakerAllowsHealthyCalls() {
        AgentCircuitBreaker cb = breaker();
        cb.register(AgentRole.TANK, "IronVeil");
        assertTrue(cb.allowCall(AgentRole.TANK), "HEALTHY agent — calls allowed");
    }

    void C05_breakerTransitionsToDegraded() {
        AgentCircuitBreaker cb = breaker(); // threshold=3
        cb.register(AgentRole.DPS, "Blitz");
        cb.onFailure(AgentRole.DPS, "timeout"); // 1 failure = threshold/2 = 1
        assertEquals(AgentHealth.Status.DEGRADED,
            cb.getHealth(AgentRole.DPS).getStatus(), "DEGRADED at 1 failure (half of 3)");
        assertTrue(cb.allowCall(AgentRole.DPS), "DEGRADED still allows calls");
    }

    void C06_breakerOpensAtThreshold() {
        AgentCircuitBreaker cb = breaker(); // threshold=3
        cb.register(AgentRole.DPS, "Blitz");
        cb.onFailure(AgentRole.DPS, "err1");
        cb.onFailure(AgentRole.DPS, "err2");
        cb.onFailure(AgentRole.DPS, "err3"); // 3rd failure opens circuit
        assertEquals(AgentHealth.Status.CIRCUIT_OPEN,
            cb.getHealth(AgentRole.DPS).getStatus(), "CIRCUIT_OPEN at 3 failures");
    }

    void C07_breakerPublishesCircuitOpenEvent() {
        AgentMessageBus bus = new AgentMessageBus();
        AgentCircuitBreaker cb = new AgentCircuitBreaker(3, 2, bus);
        cb.register(AgentRole.DPS, "Blitz");

        cb.onFailure(AgentRole.DPS, "e1");
        cb.onFailure(AgentRole.DPS, "e2");
        cb.onFailure(AgentRole.DPS, "e3");

        assertEquals(1, bus.getLog(MessageType.CIRCUIT_OPEN).size(),
            "1 CIRCUIT_OPEN event published");
        assertEquals(AgentRole.DPS,
            bus.getLog(MessageType.CIRCUIT_OPEN).get(0).getFrom(),
            "event from DPS");
    }

    void C08_breakerBlocksOpenCircuit() {
        AgentCircuitBreaker cb = breaker();
        cb.register(AgentRole.DPS, "Blitz");
        cb.onFailure(AgentRole.DPS, "e1");
        cb.onFailure(AgentRole.DPS, "e2");
        cb.onFailure(AgentRole.DPS, "e3");
        assertFalse(cb.allowCall(AgentRole.DPS), "CIRCUIT_OPEN blocks calls");
    }

    void C09_breakerResetsAfterSuccess() {
        AgentCircuitBreaker cb = breaker();
        cb.register(AgentRole.DPS, "Blitz");
        cb.onFailure(AgentRole.DPS, "e1");
        cb.onFailure(AgentRole.DPS, "e2");
        cb.onFailure(AgentRole.DPS, "e3"); // circuit opens
        // Simulate recovery: onSuccess resets failureCount → status → HEALTHY
        cb.onSuccess(AgentRole.DPS, 50); // resets failures to 0
        assertEquals(AgentHealth.Status.HEALTHY,
            cb.getHealth(AgentRole.DPS).getStatus(),
            "HEALTHY after success resets failureCount");
        assertTrue(cb.allowCall(AgentRole.DPS), "calls allowed after recovery");
    }

    void C10_breakerAllowsUnregisteredAgent() {
        AgentCircuitBreaker cb = breaker();
        // SCOUT not registered — safe default is allow
        assertTrue(cb.allowCall(AgentRole.SCOUT),
            "unregistered agent defaults to allowed");
    }

    void C11_multipleAgentsTrackedIndependently() {
        AgentCircuitBreaker cb = breaker();
        cb.register(AgentRole.TANK, "IronVeil");
        cb.register(AgentRole.DPS,  "Blitz");

        cb.onFailure(AgentRole.DPS, "e1");
        cb.onFailure(AgentRole.DPS, "e2");
        cb.onFailure(AgentRole.DPS, "e3"); // DPS opens

        assertEquals(AgentHealth.Status.CIRCUIT_OPEN,
            cb.getHealth(AgentRole.DPS).getStatus(),  "DPS circuit open");
        assertEquals(AgentHealth.Status.HEALTHY,
            cb.getHealth(AgentRole.TANK).getStatus(), "TANK unaffected");
        assertTrue(cb.allowCall(AgentRole.TANK), "TANK still callable");
    }

    void C12_successAfterOpenBeginsRecovery() {
        AgentCircuitBreaker cb = breaker();
        cb.register(AgentRole.SUPPORT, "NurseBot");
        cb.onFailure(AgentRole.SUPPORT, "e1");
        cb.onFailure(AgentRole.SUPPORT, "e2");
        cb.onFailure(AgentRole.SUPPORT, "e3");
        assertEquals(AgentHealth.Status.CIRCUIT_OPEN,
            cb.getHealth(AgentRole.SUPPORT).getStatus(), "circuit open");
        cb.onSuccess(AgentRole.SUPPORT, 100);
        assertEquals(AgentHealth.Status.HEALTHY,
            cb.getHealth(AgentRole.SUPPORT).getStatus(), "recovering after success");
    }

    void C13_allHealthReturnsAllAgents() {
        AgentCircuitBreaker cb = breaker();
        cb.register(AgentRole.TANK,      "IronVeil");
        cb.register(AgentRole.DPS,       "Blitz");
        cb.register(AgentRole.SUPPORT,   "NurseBot");
        cb.register(AgentRole.STRATEGIST,"Oracle");
        assertEquals(4, cb.allHealth().size(), "4 agents tracked");
    }

    void C14_healthStatusHasThreeValues() {
        AgentHealth.Status[] values = AgentHealth.Status.values();
        assertEquals(3, values.length, "3 status values");
        assertEquals(AgentHealth.Status.HEALTHY,      values[0], "HEALTHY");
        assertEquals(AgentHealth.Status.DEGRADED,     values[1], "DEGRADED");
        assertEquals(AgentHealth.Status.CIRCUIT_OPEN, values[2], "CIRCUIT_OPEN");
    }

    void C15_circuitOpenEventHasPayload() {
        AgentMessageBus     bus = new AgentMessageBus();
        AgentCircuitBreaker cb  = new AgentCircuitBreaker(2, 2, bus);
        cb.register(AgentRole.TANK, "IronVeil");
        cb.onFailure(AgentRole.TANK, "timeout");
        cb.onFailure(AgentRole.TANK, "timeout"); // opens at threshold=2
        var events = bus.getLog(MessageType.CIRCUIT_OPEN);
        assertEquals(1, events.size(), "one CIRCUIT_OPEN event");
        assertNotNull(events.get(0).getPayload(), "payload non-null");
        assertTrue(events.get(0).getPayload().toString().contains("failure"),
            "payload mentions failures");
    }

    void C16_missionProfileAnnotationReadable() {
        var ann = GamingAgent.class
            .getAnnotation(io.squados.annotation.MissionProfile.class);
        assertNotNull(ann, "@MissionProfile found");
        assertEquals("gaming", ann.value(), "@MissionProfile.value");
    }

    void C17_fullCircuitScenario() {
        // Simulate the exact Ghost Protocol failure scenario from the game design
        AgentMessageBus     bus     = new AgentMessageBus();
        AgentCircuitBreaker cb      = new AgentCircuitBreaker(3, 2, bus);
        AtomicInteger       fallbacks = new AtomicInteger(0);

        cb.register(AgentRole.STRATEGIST, "Oracle");

        // Subscribe to CIRCUIT_OPEN to activate fallback
        bus.subscribe(AgentRole.STRATEGIST, MessageType.CIRCUIT_OPEN,
            msg -> fallbacks.incrementAndGet());

        // Oracle gets fed bad data 3 times
        cb.onFailure(AgentRole.STRATEGIST, "enemy decoy signal");
        assertTrue(cb.allowCall(AgentRole.STRATEGIST), "still callable after 1 failure");

        cb.onFailure(AgentRole.STRATEGIST, "false flank reading");
        assertTrue(cb.allowCall(AgentRole.STRATEGIST), "still callable after 2 failures");

        cb.onFailure(AgentRole.STRATEGIST, "phantom position confirmed");
        assertFalse(cb.allowCall(AgentRole.STRATEGIST), "BLOCKED after 3 failures");
        assertEquals(1, fallbacks.get(), "fallback activated via CIRCUIT_OPEN event");

        // Wraith provides verified intel — Oracle recovers
        cb.onSuccess(AgentRole.STRATEGIST, 80);
        assertTrue(cb.allowCall(AgentRole.STRATEGIST), "Oracle recovered");
        assertEquals(AgentHealth.Status.HEALTHY,
            cb.getHealth(AgentRole.STRATEGIST).getStatus(), "status HEALTHY");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private AgentCircuitBreaker breaker() {
        return new AgentCircuitBreaker(3, 2, new AgentMessageBus());
    }

    private static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertEquals(long e, long a, String msg) {
        if (e == a) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    private static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
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
