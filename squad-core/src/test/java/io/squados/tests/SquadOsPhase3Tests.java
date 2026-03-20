package io.squados.tests;

import io.squados.annotation.AgentRole;
import io.squados.annotation.OnMessage;
import io.squados.bus.AgentMessage;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.MessageHandler;
import io.squados.bus.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 3 test suite — AgentMessageBus and @OnMessage routing.
 *
 * B01 — AgentMessage stores from, to, type, payload correctly
 * B02 — AgentMessage.topic() returns correct key
 * B03 — AgentMessage.getPayload(Class) casts correctly
 * B04 — Bus delivers message to exact subscriber
 * B05 — Bus delivers broadcast to wildcard subscriber
 * B06 — Bus fires exact AND wildcard when both registered
 * B07 — Bus does not deliver to unrelated subscribers
 * B08 — Bus logs all published messages
 * B09 — Bus.getLog(type) filters by message type
 * B10 — Bus.getLogFrom(role) filters by sender
 * B11 — registerListeners scans @OnMessage annotations
 * B12 — registerListeners fires method when matching message published
 * B13 — Multiple @OnMessage on same agent all register
 * B14 — NurseBot pattern: TANK HP_CRITICAL triggers heal handler
 * B15 — Oracle WILDCARD pattern: STATUS_UPDATE from any agent fires
 * B16 — publishTo directed message fires handlers
 * B17 — Bus handles handler exception without crashing other handlers
 */
public class SquadOsPhase3Tests {

    // ── Fixture agent classes ─────────────────────────────────────────

    static class NurseBotAgent {
        final AtomicInteger healCount   = new AtomicInteger(0);
        final AtomicInteger flankCount  = new AtomicInteger(0);
        AtomicReference<Object> lastPayload = new AtomicReference<>();

        @OnMessage(from = AgentRole.TANK, type = MessageType.HP_CRITICAL)
        public void emergencyHeal(AgentMessage msg) {
            healCount.incrementAndGet();
            lastPayload.set(msg.getPayload());
        }

        @OnMessage(from = AgentRole.DPS, type = MessageType.FLANK_COMPLETE)
        public void topUpBlitz(AgentMessage msg) {
            flankCount.incrementAndGet();
        }
    }

    static class OracleAgent {
        final List<AgentMessage> updates = new ArrayList<>();

        @OnMessage(from = AgentRole.WILDCARD, type = MessageType.STATUS_UPDATE)
        public void updateBattlePicture(AgentMessage msg) {
            updates.add(msg);
        }
    }

    // ── Runner ────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase3Tests();
        String[] tests = {
            "B01_agentMessageStoresFields",
            "B02_agentMessageTopicKey",
            "B03_agentMessageTypedPayload",
            "B04_busDeliverstToExactSubscriber",
            "B05_busDeliverstoBroadcastWildcard",
            "B06_busFiresExactAndWildcard",
            "B07_busDoesNotDeliverToUnrelated",
            "B08_busLogsAllMessages",
            "B09_busLogFilterByType",
            "B10_busLogFilterBySender",
            "B11_registerListenersScansAnnotations",
            "B12_registerListenersFiresOnMatch",
            "B13_multipleOnMessageAllRegister",
            "B14_nurseBotHpCriticalPattern",
            "B15_oracleWildcardPattern",
            "B16_publishToDirectedMessage",
            "B17_handlerExceptionDoesNotCrashBus",
        };

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║     SquadOS Phase 3 — MessageBus Tests       ║");
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
            System.out.println("\n  PHASE 3 GATE: FAILED\n");
            System.exit(1);
        } else {
            System.out.println("\n  PHASE 3 GATE: ALL TESTS PASSED ✓");
            System.out.println("  v0.3.0 tag can be cut. Phase 4 (DX + CLI) can begin.\n");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void B01_agentMessageStoresFields() {
        AgentMessage m = new AgentMessage(
            AgentRole.STRATEGIST, AgentRole.TANK, MessageType.DIRECTIVE, "Hold the gate");
        assertEquals(AgentRole.STRATEGIST, m.getFrom(),    "from");
        assertEquals(AgentRole.TANK,       m.getTo(),      "to");
        assertEquals(MessageType.DIRECTIVE, m.getType(),   "type");
        assertEquals("Hold the gate",      m.getPayload(), "payload");
        assertFalse(m.isBroadcast(), "directed message is not broadcast");
        assertNotNull(m.getMessageId(), "messageId generated");
        assertNotNull(m.getSentAt(),    "sentAt set");
    }

    void B02_agentMessageTopicKey() {
        AgentMessage m = new AgentMessage(
            AgentRole.TANK, MessageType.HP_CRITICAL, 35);
        assertEquals("TANK.HP_CRITICAL", m.topic(), "topic key format");
    }

    void B03_agentMessageTypedPayload() {
        AgentMessage m = new AgentMessage(AgentRole.TANK, MessageType.HP_CRITICAL, 42);
        Integer hp = m.getPayload(Integer.class);
        assertEquals(42, hp, "typed payload cast");

        assertThrows(ClassCastException.class,
            () -> m.getPayload(String.class),
            "wrong type throws ClassCastException");
    }

    void B04_busDeliverstToExactSubscriber() {
        AgentMessageBus bus = new AgentMessageBus();
        AtomicInteger count = new AtomicInteger(0);
        bus.subscribe(AgentRole.TANK, MessageType.HP_CRITICAL, msg -> count.incrementAndGet());
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 35);
        assertEquals(1, count.get(), "exact subscriber fired once");
    }

    void B05_busDeliverstoBroadcastWildcard() {
        AgentMessageBus bus = new AgentMessageBus();
        AtomicInteger count = new AtomicInteger(0);
        bus.subscribe(AgentRole.WILDCARD, MessageType.STATUS_UPDATE,
            msg -> count.incrementAndGet());
        bus.publish(AgentRole.TANK,       MessageType.STATUS_UPDATE, "hp=75");
        bus.publish(AgentRole.DPS,        MessageType.STATUS_UPDATE, "flanked");
        bus.publish(AgentRole.STRATEGIST, MessageType.STATUS_UPDATE, "re-planning");
        assertEquals(3, count.get(), "wildcard fires for all 3 senders");
    }

    void B06_busFiresExactAndWildcard() {
        AgentMessageBus bus = new AgentMessageBus();
        AtomicInteger exact    = new AtomicInteger(0);
        AtomicInteger wildcard = new AtomicInteger(0);
        bus.subscribe(AgentRole.TANK,     MessageType.HP_CRITICAL, msg -> exact.incrementAndGet());
        bus.subscribe(AgentRole.WILDCARD, MessageType.HP_CRITICAL, msg -> wildcard.incrementAndGet());
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 20);
        assertEquals(1, exact.get(),    "exact fired once");
        assertEquals(1, wildcard.get(), "wildcard fired once");
    }

    void B07_busDoesNotDeliverToUnrelated() {
        AgentMessageBus bus = new AgentMessageBus();
        AtomicInteger count = new AtomicInteger(0);
        bus.subscribe(AgentRole.SUPPORT, MessageType.HP_CRITICAL, msg -> count.incrementAndGet());
        // Publish from DPS (not SUPPORT) — subscriber only listens for SUPPORT
        bus.publish(AgentRole.DPS, MessageType.HP_CRITICAL, 10);
        assertEquals(0, count.get(), "SUPPORT subscriber not fired for DPS message");
    }

    void B08_busLogsAllMessages() {
        AgentMessageBus bus = new AgentMessageBus();
        bus.publish(AgentRole.TANK, MessageType.STATUS_UPDATE, "hp=90");
        bus.publish(AgentRole.DPS,  MessageType.FLANK_COMPLETE, "sniper down");
        assertEquals(2, bus.publishedCount(), "2 messages logged");
        assertEquals(2, bus.getLog().size(),  "getLog returns all");
    }

    void B09_busLogFilterByType() {
        AgentMessageBus bus = new AgentMessageBus();
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL,   35);
        bus.publish(AgentRole.TANK, MessageType.STATUS_UPDATE, "holding");
        bus.publish(AgentRole.DPS,  MessageType.HP_CRITICAL,   50);
        assertEquals(2, bus.getLog(MessageType.HP_CRITICAL).size(),   "2 HP_CRITICAL");
        assertEquals(1, bus.getLog(MessageType.STATUS_UPDATE).size(), "1 STATUS_UPDATE");
    }

    void B10_busLogFilterBySender() {
        AgentMessageBus bus = new AgentMessageBus();
        bus.publish(AgentRole.TANK,      MessageType.STATUS_UPDATE, "a");
        bus.publish(AgentRole.TANK,      MessageType.HP_CRITICAL,   "b");
        bus.publish(AgentRole.STRATEGIST,MessageType.DIRECTIVE,     "c");
        assertEquals(2, bus.getLogFrom(AgentRole.TANK).size(),       "2 from TANK");
        assertEquals(1, bus.getLogFrom(AgentRole.STRATEGIST).size(), "1 from STRATEGIST");
    }

    void B11_registerListenersScansAnnotations() {
        AgentMessageBus bus   = new AgentMessageBus();
        NurseBotAgent   nurse = new NurseBotAgent();
        bus.registerListeners(nurse);
        assertEquals(2, bus.subscriberCount(), "2 @OnMessage methods registered");
    }

    void B12_registerListenersFiresOnMatch() {
        AgentMessageBus bus   = new AgentMessageBus();
        NurseBotAgent   nurse = new NurseBotAgent();
        bus.registerListeners(nurse);

        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 35);

        assertEquals(1, nurse.healCount.get(),  "heal handler fired once");
        assertEquals(35, nurse.lastPayload.get(), "payload passed correctly");
        assertEquals(0, nurse.flankCount.get(), "flank handler NOT fired");
    }

    void B13_multipleOnMessageAllRegister() {
        AgentMessageBus bus   = new AgentMessageBus();
        NurseBotAgent   nurse = new NurseBotAgent();
        bus.registerListeners(nurse);

        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL,   30);
        bus.publish(AgentRole.DPS,  MessageType.FLANK_COMPLETE, "done");

        assertEquals(1, nurse.healCount.get(),  "HP_CRITICAL handler fired");
        assertEquals(1, nurse.flankCount.get(), "FLANK_COMPLETE handler fired");
    }

    void B14_nurseBotHpCriticalPattern() {
        AgentMessageBus bus   = new AgentMessageBus();
        NurseBotAgent   nurse = new NurseBotAgent();
        bus.registerListeners(nurse);

        // Fire 3 HP_CRITICAL events — NurseBot should react to each
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 38);
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 22);
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 15);

        assertEquals(3, nurse.healCount.get(), "NurseBot reacted to all 3 HP events");
    }

    void B15_oracleWildcardPattern() {
        AgentMessageBus bus    = new AgentMessageBus();
        OracleAgent     oracle = new OracleAgent();
        bus.registerListeners(oracle);

        // Oracle listens for STATUS_UPDATE from ANY agent (WILDCARD)
        bus.publish(AgentRole.TANK,    MessageType.STATUS_UPDATE, "hp=80");
        bus.publish(AgentRole.DPS,     MessageType.STATUS_UPDATE, "flanking");
        bus.publish(AgentRole.SUPPORT, MessageType.STATUS_UPDATE, "healing");

        assertEquals(3, oracle.updates.size(), "Oracle received all 3 status updates");
        assertEquals(AgentRole.TANK,    oracle.updates.get(0).getFrom(), "first from TANK");
        assertEquals(AgentRole.DPS,     oracle.updates.get(1).getFrom(), "second from DPS");
        assertEquals(AgentRole.SUPPORT, oracle.updates.get(2).getFrom(), "third from SUPPORT");
    }

    void B16_publishToDirectedMessage() {
        AgentMessageBus bus    = new AgentMessageBus();
        AtomicInteger   count  = new AtomicInteger(0);
        bus.subscribe(AgentRole.STRATEGIST, MessageType.DIRECTIVE,
            msg -> count.incrementAndGet());

        bus.publishTo(AgentRole.STRATEGIST, AgentRole.TANK,
                      MessageType.DIRECTIVE, "Hold gate");

        assertEquals(1, bus.publishedCount(), "message logged");
        AgentMessage logged = bus.getLog().get(0);
        assertEquals(AgentRole.TANK, logged.getTo(), "directed to TANK");
        assertFalse(logged.isBroadcast(), "directed is not broadcast");
    }

    void B17_handlerExceptionDoesNotCrashBus() {
        AgentMessageBus bus        = new AgentMessageBus();
        AtomicInteger   goodCount  = new AtomicInteger(0);

        // Bad handler — throws on every message
        bus.subscribe(AgentRole.TANK, MessageType.HP_CRITICAL,
            msg -> { throw new RuntimeException("simulated handler crash"); });

        // Good handler — should still fire
        bus.subscribe(AgentRole.TANK, MessageType.HP_CRITICAL,
            msg -> goodCount.incrementAndGet());

        // Should not throw
        bus.publish(AgentRole.TANK, MessageType.HP_CRITICAL, 10);

        assertEquals(1, goodCount.get(),
            "good handler still fired despite bad handler crash");
    }

    // ── Assertion helpers ─────────────────────────────────────────────

    private static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
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
    private static <T extends Throwable> void assertThrows(
            Class<T> type, Runnable action, String msg) {
        try { action.run();
            throw new AssertionError(msg + " — expected " + type.getSimpleName() + " not thrown");
        } catch (Throwable t) {
            if (!type.isInstance(t))
                throw new AssertionError(msg + " — expected " + type.getSimpleName()
                    + " but got " + t.getClass().getSimpleName());
        }
    }
}
