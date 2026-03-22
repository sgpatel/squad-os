package io.squados.tests;

import io.squados.annotation.*;
import io.squados.approval.*;
import io.squados.exception.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.*;

/**
 * Phase 12 — v2.3 @AwaitApproval + @AutoApproval
 *
 * A01 — @AutoApproval condition true -> AUTO_APPROVED, no human needed
 * A02 — @AutoApproval condition false + no @AwaitApproval -> throws AutoApprovalFailedException
 * A03 — @AutoApproval condition false + @AwaitApproval -> escalates to human
 * A04 — @AutoApproval AND condition: both true -> approved
 * A05 — @AutoApproval AND condition: one false -> escalates
 * A06 — @AutoApproval OR condition: one true -> approved
 * A07 — @AutoApproval rejectOnMatch=true: condition true -> rejects
 * A08 — @AwaitApproval human approves -> result returned
 * A09 — @AwaitApproval human rejects -> throws ApprovalRejectedException
 * A10 — @AwaitApproval saves request to store
 * A11 — ApprovalStore.findPending returns pending requests
 * A12 — ApprovalStore.approve transitions status to APPROVED
 * A13 — ApprovalStore.reject transitions status to REJECTED
 * A14 — ApprovalRequest.isExpired works correctly
 * A15 — Timeout with APPROVE policy -> auto-approves
 * A16 — Timeout with REJECT policy -> throws exception
 * A17 — No annotations -> result passes through unchanged
 */
public class SquadOsPhase12Tests {

    // ── Test result objects ───────────────────────────────────────
    public static class LoanDecision {
        public double amount;
        public double riskScore;
        public String status;
        public LoanDecision(double amount, double riskScore, String status) {
            this.amount    = amount;
            this.riskScore = riskScore;
            this.status    = status;
        }
        @Override public String toString() {
            return "LoanDecision{amount=" + amount + ", risk=" + riskScore + ", status=" + status + "}";
        }
    }

    // ── Test agent ────────────────────────────────────────────────
    static class UnderwriterAgent {
        @AutoApproval(condition = "amount < 10000 AND riskScore < 0.3",
                      reason = "Within auto-approval limits")
        public LoanDecision autoOnly(LoanDecision d) { return d; }

        @AutoApproval(condition = "amount < 10000",
                      reason = "Auto-approved")
        @AwaitApproval(reason = "Exceeds auto-approval limit",
                       timeoutHours = 1)
        public LoanDecision autoWithFallback(LoanDecision d) { return d; }

        @AwaitApproval(reason = "Manual review required",
                       timeoutHours = 1)
        public LoanDecision manualOnly(LoanDecision d) { return d; }

        @AwaitApproval(reason = "Timeout test",
                       timeoutHours = 0,
                       onTimeout = TimeoutPolicy.APPROVE)
        public LoanDecision timeoutApprove(LoanDecision d) { return d; }

        @AwaitApproval(reason = "Timeout test",
                       timeoutHours = 0,
                       onTimeout = TimeoutPolicy.REJECT)
        public LoanDecision timeoutReject(LoanDecision d) { return d; }

        @AutoApproval(condition = "riskScore > 0.8",
                      rejectOnMatch = true,
                      rejectReason = "High risk blocked")
        public LoanDecision blockHighRisk(LoanDecision d) { return d; }

        public LoanDecision noAnnotation(LoanDecision d) { return d; }
    }

    // ── Runner ────────────────────────────────────────────────────
    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase12Tests();
        String[] tests = {
            "A01_autoApprovalConditionTrue",
            "A02_autoApprovalNoFallbackThrows",
            "A03_autoApprovalFalseEscalates",
            "A04_autoApprovalAndBothTrue",
            "A05_autoApprovalAndOneFalse",
            "A06_autoApprovalOrOneTrue",
            "A07_autoApprovalRejectOnMatch",
            "A08_awaitApprovalHumanApproves",
            "A09_awaitApprovalHumanRejects",
            "A10_awaitApprovalSavesToStore",
            "A11_storeFindPendingReturnsPending",
            "A12_storeApproveTransitionsStatus",
            "A13_storeRejectTransitionsStatus",
            "A14_approvalRequestIsExpired",
            "A15_timeoutApprovePolicy",
            "A16_timeoutRejectPolicy",
            "A17_noAnnotationsPassThrough",
        };
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 12 — v2.3 @AwaitApproval + @Auto     ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 12 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 12 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.3 @AwaitApproval + @AutoApproval operational.\n"); }
    }

    // ── Test helpers ──────────────────────────────────────────────
    ApprovalEngine engine() { return new ApprovalEngine(new InProcessApprovalStore(), 50L); }
    Method method(String name) {
        try { return UnderwriterAgent.class.getDeclaredMethod(name, LoanDecision.class); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ── Tests ─────────────────────────────────────────────────────
    void A01_autoApprovalConditionTrue() throws Exception {
        var d = new LoanDecision(5000, 0.2, "PENDING");
        Object result = engine().processApprovals(method("autoOnly"), d, "Underwriter");
        assertEquals(d, result, "result returned unchanged");
    }

    void A02_autoApprovalNoFallbackThrows() throws Exception {
        var d = new LoanDecision(50000, 0.8, "PENDING");
        try {
            engine().processApprovals(method("autoOnly"), d, "Underwriter");
            throw new AssertionError("should have thrown");
        } catch (AutoApprovalFailedException e) {
            assertTrue(e.getMessage().contains("amount"), "exception mentions condition");
        }
    }

    void A03_autoApprovalFalseEscalates() throws Exception {
        // amount >= 10000 -> falls through to @AwaitApproval
        var d = new LoanDecision(50000, 0.2, "PENDING");
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalEngine eng = new ApprovalEngine(store, 50L);
        // Approve in background
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            List<ApprovalRequest> pending = store.findPending();
            if (!pending.isEmpty()) store.approve(pending.get(0).getId(), "Approved by test");
        });
        Object result = eng.processApprovals(method("autoWithFallback"), d, "Underwriter");
        assertEquals(d, result, "escalated + approved returns result");
    }

    void A04_autoApprovalAndBothTrue() throws Exception {
        var d = new LoanDecision(5000, 0.2, "PENDING");
        ApprovalEngine eng = engine();
        Object result = eng.processApprovals(method("autoOnly"), d, "Underwriter");
        assertEquals(d, result, "AND both true -> approved");
    }

    void A05_autoApprovalAndOneFalse() throws Exception {
        // amount OK but riskScore too high
        var d = new LoanDecision(5000, 0.9, "PENDING");
        try {
            engine().processApprovals(method("autoOnly"), d, "Underwriter");
            throw new AssertionError("should have thrown");
        } catch (AutoApprovalFailedException e) {
            assertNotNull(e, "AND one false -> fails");
        }
    }

    void A06_autoApprovalOrOneTrue() throws Exception {
        // autoWithFallback uses only "amount < 10000", OR not needed,
        // test engine directly with OR condition
        ApprovalEngine eng = engine();
        var d = new LoanDecision(5000, 0.9, "PENDING");
        boolean result = eng.evaluateCondition("amount < 10000 OR riskScore < 0.5", d);
        assertTrue(result, "OR one true -> condition passes");
    }

    void A07_autoApprovalRejectOnMatch() throws Exception {
        var d = new LoanDecision(5000, 0.9, "PENDING");
        try {
            engine().processApprovals(method("blockHighRisk"), d, "Underwriter");
            throw new AssertionError("should have thrown");
        } catch (AutoApprovalFailedException e) {
            assertTrue(e.getMessage().contains("High risk"), "rejectOnMatch blocks high risk");
        }
    }

    void A08_awaitApprovalHumanApproves() throws Exception {
        var d = new LoanDecision(50000, 0.5, "PENDING");
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalEngine eng = new ApprovalEngine(store, 50L);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(150); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            List<ApprovalRequest> pending = store.findPending();
            if (!pending.isEmpty()) store.approve(pending.get(0).getId(), "Looks good");
        });
        Object result = eng.processApprovals(method("manualOnly"), d, "Underwriter");
        assertEquals(d, result, "approved -> result returned");
    }

    void A09_awaitApprovalHumanRejects() throws Exception {
        var d = new LoanDecision(50000, 0.5, "PENDING");
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalEngine eng = new ApprovalEngine(store, 50L);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(150); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            List<ApprovalRequest> pending = store.findPending();
            if (!pending.isEmpty()) store.reject(pending.get(0).getId(), "Too risky");
        });
        try {
            eng.processApprovals(method("manualOnly"), d, "Underwriter");
            throw new AssertionError("should have thrown");
        } catch (ApprovalRejectedException e) {
            assertTrue(e.getReason().contains("Too risky"), "rejection reason preserved");
        }
    }

    void A10_awaitApprovalSavesToStore() throws Exception {
        var d = new LoanDecision(50000, 0.5, "PENDING");
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalEngine eng = new ApprovalEngine(store, 50L);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            store.findPending().forEach(r -> store.approve(r.getId(), "ok"));
        });
        eng.processApprovals(method("manualOnly"), d, "Underwriter");
        assertEquals(1, store.findAll().size(), "1 request saved to store");
    }

    void A11_storeFindPendingReturnsPending() {
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalRequest req = new ApprovalRequest(
            "Agent", "method", "decision", "reason",
            "approver", ApprovalPriority.NORMAL, TimeoutPolicy.REJECT, 1);
        store.save(req);
        assertEquals(1, store.findPending().size(), "1 pending request found");
    }

    void A12_storeApproveTransitionsStatus() {
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalRequest req = new ApprovalRequest(
            "Agent", "method", "decision", "reason",
            "approver", ApprovalPriority.NORMAL, TimeoutPolicy.REJECT, 1);
        store.save(req);
        store.approve(req.getId(), "ok");
        assertEquals(ApprovalRequest.Status.APPROVED,
            store.findById(req.getId()).get().getStatus(), "status is APPROVED");
    }

    void A13_storeRejectTransitionsStatus() {
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalRequest req = new ApprovalRequest(
            "Agent", "method", "decision", "reason",
            "approver", ApprovalPriority.NORMAL, TimeoutPolicy.REJECT, 1);
        store.save(req);
        store.reject(req.getId(), "no");
        assertEquals(ApprovalRequest.Status.REJECTED,
            store.findById(req.getId()).get().getStatus(), "status is REJECTED");
    }

    void A14_approvalRequestIsExpired() {
        ApprovalRequest req = new ApprovalRequest(
            "Agent", "method", "d", "r", "a",
            ApprovalPriority.NORMAL, TimeoutPolicy.REJECT, 0); // 0 hours = expires immediately
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        assertTrue(req.isExpired(), "request with 0h timeout is expired");
    }

    void A15_timeoutApprovePolicy() throws Exception {
        var d = new LoanDecision(5000, 0.1, "PENDING");
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalEngine eng = new ApprovalEngine(store, 50L);
        // timeoutApprove has 0h timeout + APPROVE policy -> auto-approves immediately
        Object result = eng.processApprovals(method("timeoutApprove"), d, "Underwriter");
        assertEquals(d, result, "APPROVE timeout policy returns result");
    }

    void A16_timeoutRejectPolicy() throws Exception {
        var d = new LoanDecision(5000, 0.1, "PENDING");
        InProcessApprovalStore store = new InProcessApprovalStore();
        ApprovalEngine eng = new ApprovalEngine(store, 50L);
        try {
            eng.processApprovals(method("timeoutReject"), d, "Underwriter");
            throw new AssertionError("should have thrown");
        } catch (ApprovalRejectedException e) {
            assertTrue(e.getMessage().contains("timed out"), "REJECT timeout throws");
        }
    }

    void A17_noAnnotationsPassThrough() throws Exception {
        var d = new LoanDecision(999999, 0.99, "APPROVED");
        Object result = engine().processApprovals(method("noAnnotation"), d, "Underwriter");
        assertEquals(d, result, "no annotations -> pass through");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
}