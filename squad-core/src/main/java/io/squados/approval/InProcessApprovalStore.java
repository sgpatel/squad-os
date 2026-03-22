package io.squados.approval;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory ApprovalStore for single-node use and testing.
 * Approval decisions are made by calling approve() / reject() from
 * another thread or a REST endpoint.
 *
 * For multi-node use, replace with RedisApprovalStore.
 */
public class InProcessApprovalStore implements ApprovalStore {

    private final Map<String, ApprovalRequest> store = new ConcurrentHashMap<>();

    @Override
    public void save(ApprovalRequest request) {
        store.put(request.getId(), request);
        System.out.printf("[ApprovalStore] Request saved: %s — escalated to: %s%n",
            request.getId(), request.getEscalateTo());
    }

    @Override
    public Optional<ApprovalRequest> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ApprovalRequest> findPending() {
        return store.values().stream()
            .filter(ApprovalRequest::isPending)
            .sorted(Comparator.comparing(ApprovalRequest::getCreatedAt))
            .collect(Collectors.toList());
    }

    @Override
    public List<ApprovalRequest> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public ApprovalRequest.Status awaitDecision(String requestId, long pollMs) {
        ApprovalRequest req = store.get(requestId);
        if (req == null) return ApprovalRequest.Status.REJECTED;

        System.out.printf("[ApprovalStore] Waiting for decision on: %s%n", requestId);
        System.out.printf("[ApprovalStore] Escalated to: %s | Priority: %s | Expires: %s%n",
            req.getEscalateTo(), req.getPriority(), req.getExpiresAt());

        while (req.isPending()) {
            if (req.isExpired()) {
                req.timeout();
                System.out.printf("[ApprovalStore] Request %s TIMED OUT — policy: %s%n",
                    requestId, req.getOnTimeout());
                return switch (req.getOnTimeout()) {
                    case APPROVE -> {
                        req.autoApprove("Auto-approved by timeout policy");
                        yield ApprovalRequest.Status.APPROVED;
                    }
                    case ESCALATE -> {
                        System.out.printf("[ApprovalStore] Escalating %s...%n", requestId);
                        yield ApprovalRequest.Status.TIMED_OUT;
                    }
                    default -> ApprovalRequest.Status.TIMED_OUT;
                };
            }
            try { Thread.sleep(pollMs); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ApprovalRequest.Status.REJECTED;
            }
        }
        System.out.printf("[ApprovalStore] Decision received: %s -> %s%n",
            requestId, req.getStatus());
        return req.getStatus();
    }

    @Override
    public void approve(String requestId, String note) {
        ApprovalRequest req = store.get(requestId);
        if (req == null) throw new IllegalArgumentException("Request not found: " + requestId);
        if (req.isTerminal()) throw new IllegalStateException("Request already decided: " + req.getStatus());
        req.approve(note);
        System.out.printf("[ApprovalStore] APPROVED: %s — note: %s%n", requestId, note);
    }

    @Override
    public void reject(String requestId, String reason) {
        ApprovalRequest req = store.get(requestId);
        if (req == null) throw new IllegalArgumentException("Request not found: " + requestId);
        if (req.isTerminal()) throw new IllegalStateException("Request already decided: " + req.getStatus());
        req.reject(reason);
        System.out.printf("[ApprovalStore] REJECTED: %s — reason: %s%n", requestId, reason);
    }
}