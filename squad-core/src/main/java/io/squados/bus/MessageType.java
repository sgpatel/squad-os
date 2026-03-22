package io.squados.bus;

/**
 * All message types that can flow through the AgentMessageBus.
 * Agents publish with a type; @OnMessage listeners subscribe to a type.
 */
public enum MessageType {
    // ── Strategist → All ──────────────────────────────────────────
    DIRECTIVE,          // Oracle issues a call — all agents listen

    // ── Any Agent → Bus ───────────────────────────────────────────
    STATUS_UPDATE,      // Agent reports current state to Oracle
    HP_CRITICAL,        // Tank/DPS HP below threshold → Support reacts
    FLANK_COMPLETE,     // DPS flank succeeded → Support tops up, Oracle adapts
    CONFIDENCE_LOW,     // Agent flags uncertainty → Oracle re-plans

    // ── System ────────────────────────────────────────────────────
    SESSION_END,        // SquadContext fires at mission close → memory flush
    CIRCUIT_OPEN,       // HealthMonitor fires when agent is degraded

    // ── Generic ───────────────────────────────────────────────────
    CUSTOM,
    DEFEND,
    HEAL,
    ATTACK,
    TASK_COMPLETE,
    PING              // Developer-defined events via AgentMessage.customType
}
