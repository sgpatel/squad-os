package io.squados.tests;

import io.squados.annotation.*;
import io.squados.eval.*;
import io.squados.exception.EvalFailedException;
import io.squados.llm.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Phase 15 — v2.6 @Eval self-evaluation quality gate
 *
 * Q01 — EvalScore.overall() averages all criteria scores
 * Q02 — EvalScore.passes() true when overall &gt;= minScore
 * Q03 — EvalScore.passes() false when overall &lt; minScore
 * Q04 — EvalScore.get(criterion) returns individual score
 * Q05 — EvalJudge.extractScore parses score from judge output
 * Q06 — EvalJudge.extractScore returns 0.5 when criterion not found
 * Q07 — EvalJudge.extractScore clamps score to [0.0, 1.0]
 * Q08 — EvalRunner passes immediately when score &gt;= minScore
 * Q09 — EvalRunner retries when score &lt; minScore
 * Q10 — EvalRunner returns best attempt after max retries (RETURN_BEST)
 * Q11 — EvalRunner throws EvalFailedException (THROW policy)
 * Q12 — EvalRunner does not retry when retryOnFail=false
 * Q13 — EvalRunner passes after retry when second attempt passes
 * Q14 — EvalScore tracks attempt number
 * Q15 — EvalScore feedback preserved
 * Q16 — EvalRunner no-op when method has no @Eval annotation
 * Q17 — Multiple criteria scored independently
 */
public class SquadOsPhase15Tests {

    // ── Mock LLM ─────────────────────────────────────────────────
    static class ScriptedLlm implements LlmPort {
        final List<String> responses;
        int callCount = 0;
        ScriptedLlm(String... responses) {
            this.responses = List.of(responses);
        }
        @Override public LlmResponse chat(String s, String u, LlmOptions o) {
            String r = callCount < responses.size() ? responses.get(callCount) : "ok";
            callCount++;
            return new LlmResponse(r, 0, 0, "mock");
        }
        @Override public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) {
            return null;
        }
    }

    // ── Test agent with @Eval methods ─────────────────────────────
    static class AnalystAgent {
        @Eval(judge = AgentRole.CRITIC, minScore = 0.8f,
              criteria = {EvalCriteria.FAITHFULNESS, EvalCriteria.COMPLETENESS},
              retryOnFail = true, maxRetries = 2, onFail = EvalFailPolicy.RETURN_BEST)
        public String analyse() { return "analysis result"; }

        @Eval(minScore = 0.8f, retryOnFail = false,
              onFail = EvalFailPolicy.THROW)
        public String strictAnalyse() { return "strict result"; }

        public String noEval() { return "pass through"; }
    }

    // ── Runner ────────────────────────────────────────────────────
    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase15Tests();
        String[] tests = {
            "Q01_overallAveragesCriteria",
            "Q02_passesWhenAboveThreshold",
            "Q03_failsWhenBelowThreshold",
            "Q04_getIndividualScore",
            "Q05_extractScoreParsesCorrectly",
            "Q06_extractScoreDefaultWhenMissing",
            "Q07_extractScoreClampsToRange",
            "Q08_runnerPassesImmediately",
            "Q09_runnerRetriesOnFail",
            "Q10_runnerReturnsBestOnExhaustion",
            "Q11_runnerThrowsOnFail",
            "Q12_runnerNoRetryWhenDisabled",
            "Q13_runnerPassesAfterRetry",
            "Q14_evalScoreTracksAttemptNumber",
            "Q15_evalScoreFeedbackPreserved",
            "Q16_runnerNoOpWithoutAnnotation",
            "Q17_multipleCriteriaScoredIndependently",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 15 — v2.6 @Eval              ║");
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
        if (failed > 0) { System.out.println("\n  PHASE 15 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 15 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.6 @Eval quality gate operational.\n"); }
    }

    // ── Helper: build EvalScore directly ─────────────────────────
    EvalScore score(float faith, float complete) {
        Map<EvalCriteria, Float> m = new LinkedHashMap<>();
        m.put(EvalCriteria.FAITHFULNESS, faith);
        m.put(EvalCriteria.COMPLETENESS, complete);
        return EvalJudge.mockScore(m, "test feedback", 1, "output");
    }

    // ── Tests ─────────────────────────────────────────────────────
    void Q01_overallAveragesCriteria() {
        EvalScore s = score(0.8f, 0.6f);
        assertEqualsF(0.7f, s.overall(), "average of 0.8 and 0.6 = 0.7");
    }

    void Q02_passesWhenAboveThreshold() {
        EvalScore s = score(0.9f, 0.9f);
        assertTrue(s.passes(0.8f), "0.9 overall passes 0.8 threshold");
    }

    void Q03_failsWhenBelowThreshold() {
        EvalScore s = score(0.5f, 0.5f);
        assertFalse(s.passes(0.8f), "0.5 overall fails 0.8 threshold");
    }

    void Q04_getIndividualScore() {
        EvalScore s = score(0.9f, 0.6f);
        assertEqualsF(0.9f, s.get(EvalCriteria.FAITHFULNESS), "faithfulness score");
        assertEqualsF(0.6f, s.get(EvalCriteria.COMPLETENESS), "completeness score");
    }

    void Q05_extractScoreParsesCorrectly() {
        EvalJudge judge = new EvalJudge(new ScriptedLlm());
        float score = judge.extractScore("FAITHFULNESS: 0.85 - very accurate", "FAITHFULNESS");
        assertEqualsF(0.85f, score, "parsed 0.85 correctly");
    }

    void Q06_extractScoreDefaultWhenMissing() {
        EvalJudge judge = new EvalJudge(new ScriptedLlm());
        float score = judge.extractScore("some other text", "FAITHFULNESS");
        assertEqualsF(0.5f, score, "default 0.5 when not found");
    }

    void Q07_extractScoreClampsToRange() {
        EvalJudge judge = new EvalJudge(new ScriptedLlm());
        float high = judge.extractScore("FAITHFULNESS: 1.5 - great", "FAITHFULNESS");
        float low  = judge.extractScore("FAITHFULNESS: -0.3 - bad", "FAITHFULNESS");
        assertEqualsF(1.0f, high, "clamped to max 1.0");
        assertEqualsF(0.0f, low,  "clamped to min 0.0");
    }

    void Q08_runnerPassesImmediately() throws Exception {
        // Judge always returns high score
        ScriptedLlm judgeLlm = new ScriptedLlm(
            "FAITHFULNESS: 0.9 - accurate\nCOMPLETENESS: 0.9 - complete");
        ScriptedLlm agentLlm = new ScriptedLlm("retry output");
        EvalJudge judge = new EvalJudge(judgeLlm);
        EvalRunner runner = new EvalRunner(judge, agentLlm);
        Method m = AnalystAgent.class.getDeclaredMethod("analyse");
        String result = runner.runEvalLoop(m, "initial output", "task", "sys");
        assertEquals("initial output", result, "passed immediately — no retry");
        assertEquals(1, judgeLlm.callCount, "judge called once");
        assertEquals(0, agentLlm.callCount, "agent not retried");
    }

    void Q09_runnerRetriesOnFail() throws Exception {
        // Judge fails first, passes second
        ScriptedLlm judgeLlm = new ScriptedLlm(
            "FAITHFULNESS: 0.3 - poor\nCOMPLETENESS: 0.3 - incomplete",
            "FAITHFULNESS: 0.9 - great\nCOMPLETENESS: 0.9 - complete");
        ScriptedLlm agentLlm = new ScriptedLlm("improved output");
        EvalJudge judge = new EvalJudge(judgeLlm);
        EvalRunner runner = new EvalRunner(judge, agentLlm);
        Method m = AnalystAgent.class.getDeclaredMethod("analyse");
        String result = runner.runEvalLoop(m, "initial output", "task", "sys");
        assertEquals("improved output", result, "retry output returned after pass");
        assertEquals(2, judgeLlm.callCount, "judge called twice");
        assertEquals(1, agentLlm.callCount, "agent retried once");
    }

    void Q10_runnerReturnsBestOnExhaustion() throws Exception {
        // Judge always fails — 3 attempts total (1 initial + 2 retries)
        ScriptedLlm judgeLlm = new ScriptedLlm(
            "FAITHFULNESS: 0.3\nCOMPLETENESS: 0.5",
            "FAITHFULNESS: 0.6\nCOMPLETENESS: 0.6",
            "FAITHFULNESS: 0.4\nCOMPLETENESS: 0.4");
        ScriptedLlm agentLlm = new ScriptedLlm("retry1", "retry2");
        EvalJudge judge = new EvalJudge(judgeLlm);
        EvalRunner runner = new EvalRunner(judge, agentLlm);
        Method m = AnalystAgent.class.getDeclaredMethod("analyse");
        String result = runner.runEvalLoop(m, "initial output", "task", "sys");
        assertEquals("retry1", result, "best attempt (retry1 scored 0.6) returned");
    }

    void Q11_runnerThrowsOnFail() throws Exception {
        ScriptedLlm judgeLlm = new ScriptedLlm(
            "FAITHFULNESS: 0.3\nCOMPLETENESS: 0.3");
        EvalJudge judge = new EvalJudge(judgeLlm);
        EvalRunner runner = new EvalRunner(judge, new ScriptedLlm("x"));
        Method m = AnalystAgent.class.getDeclaredMethod("strictAnalyse");
        try {
            runner.runEvalLoop(m, "output", "task", "sys");
            throw new AssertionError("should have thrown");
        } catch (EvalFailedException e) {
            assertTrue(e.getMinScore() == 0.8f, "minScore in exception");
        }
    }

    void Q12_runnerNoRetryWhenDisabled() throws Exception {
        ScriptedLlm judgeLlm = new ScriptedLlm(
            "FAITHFULNESS: 0.3\nCOMPLETENESS: 0.3\nRELEVANCE: 0.3");
        ScriptedLlm agentLlm = new ScriptedLlm("retry");
        EvalJudge judge = new EvalJudge(judgeLlm);
        EvalRunner runner = new EvalRunner(judge, agentLlm);
        Method m = AnalystAgent.class.getDeclaredMethod("strictAnalyse");
        try {
            runner.runEvalLoop(m, "output", "task", "sys");
        } catch (EvalFailedException ignored) {}
        assertEquals(0, agentLlm.callCount, "agent not called for retry when retryOnFail=false");
    }

    void Q13_runnerPassesAfterRetry() throws Exception {
        ScriptedLlm judgeLlm = new ScriptedLlm(
            "FAITHFULNESS: 0.2\nCOMPLETENESS: 0.2",
            "FAITHFULNESS: 0.95\nCOMPLETENESS: 0.95");
        ScriptedLlm agentLlm = new ScriptedLlm("better output");
        EvalJudge judge = new EvalJudge(judgeLlm);
        EvalRunner runner = new EvalRunner(judge, agentLlm);
        Method m = AnalystAgent.class.getDeclaredMethod("analyse");
        String result = runner.runEvalLoop(m, "initial", "task", "sys");
        assertEquals("better output", result, "passes on second attempt");
    }

    void Q14_evalScoreTracksAttemptNumber() {
        Map<EvalCriteria, Float> m = new LinkedHashMap<>();
        m.put(EvalCriteria.FAITHFULNESS, 0.8f);
        EvalScore s = EvalJudge.mockScore(m, "fb", 3, "out");
        assertEquals(3, s.getAttempt(), "attempt number 3");
    }

    void Q15_evalScoreFeedbackPreserved() {
        Map<EvalCriteria, Float> m = new LinkedHashMap<>();
        m.put(EvalCriteria.RELEVANCE, 0.7f);
        EvalScore s = EvalJudge.mockScore(m, "needs more detail", 1, "out");
        assertEquals("needs more detail", s.getFeedback(), "feedback preserved");
    }

    void Q16_runnerNoOpWithoutAnnotation() throws Exception {
        EvalJudge judge = new EvalJudge(new ScriptedLlm());
        EvalRunner runner = new EvalRunner(judge, new ScriptedLlm());
        Method m = AnalystAgent.class.getDeclaredMethod("noEval");
        String result = runner.runEvalLoop(m, "pass through", "task", "sys");
        assertEquals("pass through", result, "no @Eval — pass through unchanged");
    }

    void Q17_multipleCriteriaScoredIndependently() {
        Map<EvalCriteria, Float> m = new LinkedHashMap<>();
        m.put(EvalCriteria.FAITHFULNESS,  0.9f);
        m.put(EvalCriteria.COMPLETENESS,  0.5f);
        m.put(EvalCriteria.RELEVANCE,     0.7f);
        m.put(EvalCriteria.CLARITY,       0.8f);
        EvalScore s = EvalJudge.mockScore(m, "fb", 1, "out");
        assertEqualsF(0.9f, s.get(EvalCriteria.FAITHFULNESS), "faithfulness");
        assertEqualsF(0.5f, s.get(EvalCriteria.COMPLETENESS), "completeness");
        float expected = (0.9f + 0.5f + 0.7f + 0.8f) / 4f;
        assertEqualsF(expected, s.overall(), "overall average of 4 criteria");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
    static void assertEqualsF(float e, float a, String msg) {
        if (Math.abs(e - a) < 0.001f) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
}