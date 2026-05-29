package io.squados.dashboard.api.model;

import java.time.Instant;

/**
 * An approval request visible on the dashboard — GET /api/v1/approvals.
 */
public record ApprovalItem(
        String  id,
        Instant requestedAt,
        String  agentName,
        String  role,
        String  action,
        String  payload,        // truncated to 500 chars
        String  status,         // PENDING, APPROVED, REJECTED, EXPIRED
        String  approvedBy,     // null if pending
        Instant resolvedAt,     // null if pending
        int     timeoutSeconds
) {}
