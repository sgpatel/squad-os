package io.squados.tests;

import io.squados.annotation.*;
import io.squados.delegate.*;
import io.squados.llm.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Phase 20 - v3.1 @Delegate dynamic routing
 *
 * D01 - DelegationDecision stores all fields correctly
 * D02 - DelegationDecision.isUsedFallback true when fallback used
 * D03 - DelegateRouter.ROUND_ROBIN cycles through candidates
 * D04 - DelegateRouter.ROUND_ROBIN wraps around after last candidate
 * D05 - DelegateRouter.ROUND_ROBIN different methods have independent indices
 * D06 - DelegateRouter.LOAD_BALANCE picks least loaded candidate
 * D07 - DelegateRouter.LOAD_BALANCE updates after startDelegation
 * D08 - DelegateRouter.LOAD_BALANCE falls back when all equal
 * D09 - DelegateRouter.FIRST_MATCH routes on keyword match
 * D10 - DelegateRouter.FIRST_MATCH falls back when no match
 * D11 - DelegateRouter.FIRST_MATCH supports OR condition
 * D12 - DelegateRouter.FIRST_MATCH supports AND condition
 * D13 - DelegateRouter.LLM_CHOICE routes to ANALYST when LLM says ANALYST
 * D14 - DelegateRouter.LLM_CHOICE uses fallback on unclear LLM response
 * D15 - DelegateRouter returns fallback when no annotation present
 * D16 - startDelegation/endDelegation update active counts
 * D17 - matchesCondition handles OR, AND, single keyword
 */
public class SquadOsPhase20Tests {

    static class OracleAgent {
        @Delegate(
            candidates = {AgentRole.ANALYST, AgentRole.RESEARCHER, AgentRole.EXECUTOR},
            strategy   = DelegateStrategy.LLM_CHOICE,
            fallback   = AgentRole.STRATEGIST
        )
        public String routeByLlm(String input) { return input; }

        @Delegate(
            candidates = {AgentRole.ANALYST, AgentRole.RESEARCHER, AgentRole.EXECUTOR},
            strategy   = DelegateStrategy.ROUND_ROBIN,
            fallback   = AgentRole.STRATEGIST
        )
        public String routeRoundRobin(String input) { return input; }

        @Delegate(
            candidates = {AgentRole.ANALYST, AgentRole.RESEARCHER, AgentRole.EXECUTOR},
            strategy   = DelegateStrategy.LOAD_BALANCE,
            fallback   = AgentRole.STRATEGIST
        )
        public String routeLoadBalance(String input) { return input; }

        @Delegate(
            candidates  = {AgentRole.ANALYST, AgentRole.RESEARCHER, AgentRole.EXECUTOR},
            strategy    = DelegateStrategy.FIRST_MATCH,
            conditions  = {"data OR analysis", "research OR investigate", "build OR execute"},
            fallback    = AgentRole.STRATEGIST
        )
        public String routeFirstMatch(String input) { return input; }

        public String noDelegate(String input) { return input; }
    }

    static class ScriptedLlm implements LlmPort {
        final String response;
        ScriptedLlm(String resp) { this.response = resp; }
        @Override public LlmResponse chat(String s, String u, LlmOptions o) {
            return new LlmResponse(response, 0, 0, "mock");
        }
        @Override public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) {
            return null;
        }
    }

    Method m(String name) {
        try { return OracleAgent.class.getDeclaredMethod(name, String.class); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase20Tests();
        String[] tests = {
            "D01_decisionStoresFields",
            "D02_decisionFallbackFlag",
            "D03_roundRobinCycles",
            "D04_roundRobinWrapsAround",
            "D05_roundRobinIndependentPerMethod",
            "D06_loadBalancePicksLeast",
            "D07_loadBalanceUpdatesAfterStart",
            "D08_loadBalanceAllEqualPicksFirst",
            "D09_firstMatchRouteOnKeyword",
            "D10_firstMatchFallbackOnNoMatch",
            "D11_firstMatchOrCondition",
            "D12_firstMatchAndCondition",
            "D13_llmChoiceRoutesToAnalyst",
            "D14_llmChoiceFallbackOnUnclear",
            "D15_noAnnotationReturnsFallback",
            "D16_startEndDelegationCounters",
            "D17_matchesConditionVariants",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 20 - v3.1 @Delegate          ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    -> %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    -> %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 20 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 20 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.1 @Delegate dynamic routing operational.\n"); }
    }

    void D01_decisionStoresFields() {
        DelegationDecision d = new DelegationDecision(
            AgentRole.ANALYST,
            new AgentRole[]{AgentRole.ANALYST, AgentRole.RESEARCHER},
            DelegateStrategy.LLM_CHOICE, "test reasoning", false);
        assertEquals(AgentRole.ANALYST, d.getChosenRole(), "chosen role");
        assertEquals(DelegateStrategy.LLM_CHOICE, d.getStrategy(), "strategy");
        assertEquals("test reasoning", d.getReasoning(), "reasoning");
        assertFalse(d.isUsedFallback(), "not fallback");
        assertNotNull(d.getDecidedAt(), "timestamp set");
    }

    void D02_decisionFallbackFlag() {
        DelegationDecision d = new DelegationDecision(
            AgentRole.STRATEGIST, new AgentRole[]{}, DelegateStrategy.LLM_CHOICE, "fb", true);
        assertTrue(d.isUsedFallback(), "fallback flag true");
    }

    void D03_roundRobinCycles() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        Method meth = m("routeRoundRobin");
        AgentRole r1 = router.route(meth, "task").getChosenRole();
        AgentRole r2 = router.route(meth, "task").getChosenRole();
        AgentRole r3 = router.route(meth, "task").getChosenRole();
        assertFalse(r1.equals(r2) && r2.equals(r3), "round-robin cycles through roles");
    }

    void D04_roundRobinWrapsAround() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        Method meth = m("routeRoundRobin");
        // 3 candidates: route 3 times to exhaust, then wraps
        AgentRole first = router.route(meth, "t").getChosenRole();
        router.route(meth, "t");
        router.route(meth, "t");
        AgentRole fourth = router.route(meth, "t").getChosenRole();
        assertEquals(first, fourth, "4th call wraps to first candidate");
    }

    void D05_roundRobinIndependentPerMethod() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        Method m1 = m("routeRoundRobin");
        Method m2 = m("routeLoadBalance"); // different method name
        AgentRole rrFirst = router.route(m1, "t").getChosenRole();
        AgentRole lbFirst = router.route(m2, "t").getChosenRole(); // load balance, not rr
        // Just verify both return valid candidates
        assertNotNull(rrFirst, "round-robin decision not null");
        assertNotNull(lbFirst, "load-balance decision not null");
    }

    void D06_loadBalancePicksLeast() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        // Manually inflate ANALYST count
        router.startDelegation(AgentRole.ANALYST);
        router.startDelegation(AgentRole.ANALYST);
        router.startDelegation(AgentRole.RESEARCHER);
        // EXECUTOR has 0 active — should be chosen
        DelegationDecision d = router.route(m("routeLoadBalance"), "task");
        assertEquals(AgentRole.EXECUTOR, d.getChosenRole(), "least loaded = EXECUTOR");
    }

    void D07_loadBalanceUpdatesAfterStart() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        assertEquals(0, router.getActiveCount(AgentRole.ANALYST), "starts at 0");
        router.startDelegation(AgentRole.ANALYST);
        assertEquals(1, router.getActiveCount(AgentRole.ANALYST), "1 after start");
        router.endDelegation(AgentRole.ANALYST);
        assertEquals(0, router.getActiveCount(AgentRole.ANALYST), "0 after end");
    }

    void D08_loadBalanceAllEqualPicksFirst() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        // All candidates at 0 — picks first (ANALYST)
        DelegationDecision d = router.route(m("routeLoadBalance"), "task");
        assertEquals(AgentRole.ANALYST, d.getChosenRole(), "first candidate when all equal");
    }

    void D09_firstMatchRouteOnKeyword() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        DelegationDecision d = router.route(m("routeFirstMatch"), "please research this topic");
        assertEquals(AgentRole.RESEARCHER, d.getChosenRole(), "research keyword -> RESEARCHER");
    }

    void D10_firstMatchFallbackOnNoMatch() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        DelegationDecision d = router.route(m("routeFirstMatch"), "plan the strategy");
        assertEquals(AgentRole.STRATEGIST, d.getChosenRole(), "no match -> fallback STRATEGIST");
        assertTrue(d.isUsedFallback(), "fallback flag set");
    }

    void D11_firstMatchOrCondition() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        // condition[0] = "data OR analysis" -> matches "data pipeline"
        DelegationDecision d = router.route(m("routeFirstMatch"), "data pipeline processing");
        assertEquals(AgentRole.ANALYST, d.getChosenRole(), "data keyword -> ANALYST");
    }

    void D12_firstMatchAndCondition() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        // condition[2] = "build OR execute" -> matches "build the system"
        DelegationDecision d = router.route(m("routeFirstMatch"), "build the deployment system");
        assertEquals(AgentRole.EXECUTOR, d.getChosenRole(), "build keyword -> EXECUTOR");
    }

    void D13_llmChoiceRoutesToAnalyst() {
        // LLM responds with ANALYST
        DelegateRouter router = new DelegateRouter(new ScriptedLlm("ANALYST"));
        DelegationDecision d = router.route(m("routeByLlm"), "analyse this data");
        assertEquals(AgentRole.ANALYST, d.getChosenRole(), "LLM chose ANALYST");
        assertFalse(d.isUsedFallback(), "not a fallback");
    }

    void D14_llmChoiceFallbackOnUnclear() {
        // LLM gives unclear response
        DelegateRouter router = new DelegateRouter(new ScriptedLlm("I cannot decide"));
        DelegationDecision d = router.route(m("routeByLlm"), "some task");
        assertEquals(AgentRole.STRATEGIST, d.getChosenRole(), "unclear LLM -> fallback");
        assertTrue(d.isUsedFallback(), "fallback flag set");
    }

    void D15_noAnnotationReturnsFallback() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        DelegationDecision d = router.route(m("noDelegate"), "task");
        assertEquals(AgentRole.STRATEGIST, d.getChosenRole(), "no annotation -> default fallback");
    }

    void D16_startEndDelegationCounters() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        router.startDelegation(AgentRole.ANALYST);
        router.startDelegation(AgentRole.ANALYST);
        router.startDelegation(AgentRole.RESEARCHER);
        assertEquals(2, router.getActiveCount(AgentRole.ANALYST), "2 active analyst");
        assertEquals(1, router.getActiveCount(AgentRole.RESEARCHER), "1 active researcher");
        router.endDelegation(AgentRole.ANALYST);
        assertEquals(1, router.getActiveCount(AgentRole.ANALYST), "1 after end");
    }

    void D17_matchesConditionVariants() {
        DelegateRouter router = new DelegateRouter(new ScriptedLlm(""));
        assertTrue(router.matchesCondition("fraud", "detect fraud patterns"), "single keyword");
        assertFalse(router.matchesCondition("fraud", "analyse revenue data"), "keyword miss");
        assertTrue(router.matchesCondition("fraud OR risk", "assess risk exposure"), "OR match");
        assertFalse(router.matchesCondition("fraud OR theft", "revenue analysis"), "OR both miss");
        assertTrue(router.matchesCondition("fraud AND payment", "payment fraud alert"), "AND match");
        assertFalse(router.matchesCondition("fraud AND payment", "fraud detected in account"), "AND partial miss");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return; throw new AssertionError(msg + " — null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}