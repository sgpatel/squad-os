package io.squados.approval;

import java.util.List;
import java.util.Optional;

/**
 * Storage and coordination layer for approval requests.
 *
 * Implementations:
 *   InProcessApprovalStore  — in-memory, for single-node and testing
 *   RedisApprovalStore      — Redis-backed, for multi-node (coming in v2.3)
 *
 * The @AwaitApproval interceptor calls save() then awaitDecision().
 * The human approver (via REST endpoint / UI / webhook) calls approve() or reject().
 */
public interface ApprovalStore {

    /** Persist a new approval request. */
    void save(ApprovalRequest request);

    /** Retrieve by ID. */
    Optional<ApprovalRequest> findById(String id);

    /** All pending requests — for approver dashboard. */
    List<ApprovalRequest> findPending();

    /** All requests — for audit trail. */
    List<ApprovalRequest> findAll();

    /**
     * Block the calling thread until the request is approved, rejected,
     * or times out. Called by the @AwaitApproval interceptor.
     *
     * @param requestId  The approval request ID
     * @param pollMs     How often to check for a decision (milliseconds)
     * @return           Final status (APPROVED, REJECTED, TIMED_OUT)
     */
    ApprovalRequest.Status awaitDecision(String requestId, long pollMs);

    /** Human approves the request. */
    void approve(String requestId, String note);

    /** Human rejects the request. */
    void reject(String requestId, String reason);

    /** Count of pending requests. */
    default int pendingCount() { return findPending().size(); }
}