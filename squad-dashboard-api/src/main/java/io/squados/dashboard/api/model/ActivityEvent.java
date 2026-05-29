package io.squados.dashboard.api.model;

import java.time.Instant;

/**
 * An immutable activity event emitted by the dashboard backend.
 *
 * Events are pushed to SSE subscribers in real-time and retained in
 * an in-memory ring-buffer (max 200 entries) for late-joining clients.
 */
public record ActivityEvent(
        String  id,         // UUID
        Instant timestamp,
        String  type,       // AGENT_CALL, APPROVAL, VOTE, SECURITY, SYSTEM, ERROR
        String  agentName,
        String  role,
        String  message,
        String  status,     // success, error, warning, info
        Long    latencyMs,  // nullable
        Integer tokens      // nullable
) {}
