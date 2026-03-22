package io.squados.tests;

import io.squados.annotation.*;
import io.squados.exception.VoteRejectedException;
import io.squados.vote.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Phase 13 — v2.4 @SquadVote
 *
 * V01 — Vote.approve() sets APPROVE decision
 * V02 — Vote.reject() sets REJECT decision
 * V03 — Vote.abstain() sets ABSTAIN decision
 * V04 — Vote.withVoter() preserves all fields
 * V05 — MAJORITY rule: 2 approve 1 reject -> APPROVED
 * V06 — MAJORITY rule: 1 approve 2 reject -> REJECTED
 * V07 — MAJORITY rule: 1 approve 1 reject 1 abstain -> TIE
 * V08 — UNANIMOUS rule: all approve -> APPROVED
 * V09 — UNANIMOUS rule: one reject -> REJECTED
 * V10 — ANY rule: one approve -> APPROVED
 * V11 — ANY rule: all reject -> REJECTED
 * V12 — SUPERMAJORITY rule: 4/5 approve -> APPROVED
 * V13 — SUPERMAJORITY rule: 2/5 approve -> REJECTED
 * V14 — WEIGHTED rule: higher weighted side wins
 * V15 — TieBreaker.APPROVE -> approves on tie
 * V16 — TieBreaker.REJECT -> rejects on tie
 * V17 — VoteResult summary contains voter names and reasons
 */
public class SquadOsPhase13Tests {

    VoteCollector collector(VoteRule rule, TieBreaker tie, int voters) {
        return new VoteCollector("Test topic", rule, tie, voters, 5);
    }

    VoteResult tally(VoteCollector c, Vote... votes) {
        return c.tally(List.of(votes));
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase13Tests();
        String[] tests = {
            "V01_voteApproveDecision",
            "V02_voteRejectDecision",
            "V03_voteAbstainDecision",
            "V04_voteWithVoter",
            "V05_majorityTwoApproveOneReject",
            "V06_majorityOneApproveTwoReject",
            "V07_majorityTie",
            "V08_unanimousAllApprove",
            "V09_unanimousOneReject",
            "V10_anyOneApprove",
            "V11_anyAllReject",
            "V12_supermajorityFourFifths",
            "V13_supermajorityTwoFifths",
            "V14_weightedHigherWins",
            "V15_tieBreakerApprove",
            "V16_tieBreakerReject",
            "V17_voteResultSummaryContainsVoters",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 13 — v2.4 @SquadVote         ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
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
        if (failed > 0) { System.out.println("\n  PHASE 13 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 13 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.4 @SquadVote operational.\n"); }
    }

    void V01_voteApproveDecision() {
        Vote v = Vote.approve("Looks good");
        assertTrue(v.isApprove(), "APPROVE decision");
        assertEquals("Looks good", v.getReason(), "reason preserved");
    }

    void V02_voteRejectDecision() {
        Vote v = Vote.reject("Too risky");
        assertTrue(v.isReject(), "REJECT decision");
    }

    void V03_voteAbstainDecision() {
        Vote v = Vote.abstain("Insufficient data");
        assertTrue(v.isAbstain(), "ABSTAIN decision");
        assertEquals(0.0, v.getWeight(), "abstain has 0 weight");
    }

    void V04_voteWithVoter() {
        Vote v = Vote.approve("ok").withVoter("RiskAgent");
        assertEquals("RiskAgent", v.getVoterName(), "voter name set");
        assertTrue(v.isApprove(), "decision preserved");
    }

    void V05_majorityTwoApproveOneReject() {
        var c = collector(VoteRule.MAJORITY, TieBreaker.REJECT, 3);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.approve("ok").withVoter("B"),
            Vote.reject("no").withVoter("C"));
        assertTrue(r.isApproved(), "2/3 majority -> APPROVED");
        assertEquals(2, r.getApproveCount(), "2 approvals");
    }

    void V06_majorityOneApproveTwoReject() {
        var c = collector(VoteRule.MAJORITY, TieBreaker.REJECT, 3);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.reject("no").withVoter("B"),
            Vote.reject("no").withVoter("C"));
        assertTrue(r.isRejected(), "1/3 majority -> REJECTED");
    }

    void V07_majorityTie() {
        var c = collector(VoteRule.MAJORITY, TieBreaker.ABSTAIN, 3);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.reject("no").withVoter("B"),
            Vote.abstain("meh").withVoter("C"));
        assertTrue(r.isTie(), "1 approve 1 reject 1 abstain -> TIE");
    }

    void V08_unanimousAllApprove() {
        var c = collector(VoteRule.UNANIMOUS, TieBreaker.REJECT, 3);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.approve("ok").withVoter("B"),
            Vote.approve("ok").withVoter("C"));
        assertTrue(r.isApproved(), "all approve -> UNANIMOUS APPROVED");
    }

    void V09_unanimousOneReject() {
        var c = collector(VoteRule.UNANIMOUS, TieBreaker.REJECT, 3);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.approve("ok").withVoter("B"),
            Vote.reject("NO").withVoter("C"));
        assertTrue(r.isRejected(), "one reject -> UNANIMOUS REJECTED");
    }

    void V10_anyOneApprove() {
        var c = collector(VoteRule.ANY, TieBreaker.REJECT, 3);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.reject("no").withVoter("B"),
            Vote.reject("no").withVoter("C"));
        assertTrue(r.isApproved(), "ANY: one approve -> APPROVED");
    }

    void V11_anyAllReject() {
        var c = collector(VoteRule.ANY, TieBreaker.REJECT, 2);
        var r = tally(c,
            Vote.reject("no").withVoter("A"),
            Vote.reject("no").withVoter("B"));
        assertTrue(r.isRejected(), "ANY: all reject -> REJECTED");
    }

    void V12_supermajorityFourFifths() {
        var c = collector(VoteRule.SUPERMAJORITY, TieBreaker.REJECT, 5);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.approve("ok").withVoter("B"),
            Vote.approve("ok").withVoter("C"),
            Vote.approve("ok").withVoter("D"),
            Vote.reject("no").withVoter("E"));
        assertTrue(r.isApproved(), "4/5 >= 2/3 -> SUPERMAJORITY APPROVED");
    }

    void V13_supermajorityTwoFifths() {
        var c = collector(VoteRule.SUPERMAJORITY, TieBreaker.REJECT, 5);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.approve("ok").withVoter("B"),
            Vote.reject("no").withVoter("C"),
            Vote.reject("no").withVoter("D"),
            Vote.reject("no").withVoter("E"));
        assertTrue(r.isRejected(), "2/5 < 2/3 -> SUPERMAJORITY REJECTED");
    }

    void V14_weightedHigherWins() {
        var c = collector(VoteRule.WEIGHTED, TieBreaker.REJECT, 3);
        var r = tally(c,
            Vote.approve("ok", 3.0).withVoter("SeniorRisk"),
            Vote.reject("no", 1.0).withVoter("JuniorA"),
            Vote.reject("no", 1.0).withVoter("JuniorB"));
        assertTrue(r.isApproved(), "weight 3 > 1+1 -> WEIGHTED APPROVED");
        assertEquals(3.0, r.getApproveWeight(), "approve weight = 3.0");
    }

    void V15_tieBreakerApprove() {
        var c = collector(VoteRule.MAJORITY, TieBreaker.APPROVE, 2);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.reject("no").withVoter("B"));
        assertTrue(r.isApproved(), "TIE + APPROVE breaker -> APPROVED");
    }

    void V16_tieBreakerReject() {
        var c = collector(VoteRule.MAJORITY, TieBreaker.REJECT, 2);
        var r = tally(c,
            Vote.approve("ok").withVoter("A"),
            Vote.reject("no").withVoter("B"));
        assertTrue(r.isRejected(), "TIE + REJECT breaker -> REJECTED");
    }

    void V17_voteResultSummaryContainsVoters() {
        var c = collector(VoteRule.MAJORITY, TieBreaker.REJECT, 2);
        var r = tally(c,
            Vote.approve("Strong signal").withVoter("RiskAgent"),
            Vote.reject("Pattern anomaly").withVoter("FraudAgent"));
        assertTrue(r.getSummary().contains("RiskAgent"), "summary has RiskAgent");
        assertTrue(r.getSummary().contains("FraudAgent"), "summary has FraudAgent");
        assertTrue(r.getSummary().contains("Strong signal"), "summary has reason");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
}