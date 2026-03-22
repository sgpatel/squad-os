package io.squados.tests;

import io.squados.annotation.*;
import io.squados.improve.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Phase 18 — v2.9 @Improve few-shot learning from feedback
 *
 * F01 — FeedbackExample stores input, output, label, note
 * F02 — FeedbackExample.isGood() true for GOOD label
 * F03 — FeedbackExample.isBad() true for BAD label
 * F04 — FeedbackExample.toPromptExample() includes input and output
 * F05 — FeedbackExample.toPromptExample(true) includes AVOID header
 * F06 — InProcessFeedbackStore.save stores example
 * F07 — InProcessFeedbackStore.findAll returns all for label
 * F08 — InProcessFeedbackStore.count returns correct count
 * F09 — InProcessFeedbackStore.findSimilarGood returns GOOD only
 * F10 — InProcessFeedbackStore.findSimilarBad returns BAD only
 * F11 — InProcessFeedbackStore similarity ranks by keyword overlap
 * F12 — ImproveEngine returns empty when below minExamples
 * F13 — ImproveEngine injects examples when above minExamples
 * F14 — ImproveEngine prompt contains FEW-SHOT header
 * F15 — ImproveEngine respects topK limit
 * F16 — ImproveEngine includes negative examples when configured
 * F17 — ImproveEngine.saveFeedback saves with correct label
 */
public class SquadOsPhase18Tests {

    static class LoanAgent {
        @Improve(label = "loan-check", topK = 3, minExamples = 2)
        public String underwriteLoan(String input) { return "decision"; }

        @Improve(label = "fraud-check", topK = 2, minExamples = 1,
                 includeNegativeExamples = true)
        public String checkFraud(String input) { return "result"; }

        public String noImprove(String input) { return "plain"; }
    }

    Method m(String name) {
        try { return LoanAgent.class.getDeclaredMethod(name, String.class); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase18Tests();
        String[] tests = {
            "F01_feedbackExampleStoresFields",
            "F02_isGoodTrue",
            "F03_isBadTrue",
            "F04_toPromptExampleIncludesInputOutput",
            "F05_toPromptExampleNegativeHeader",
            "F06_storesSaveExample",
            "F07_findAllReturnsAll",
            "F08_countCorrect",
            "F09_findSimilarGoodReturnsGoodOnly",
            "F10_findSimilarBadReturnsBadOnly",
            "F11_similarityRanksByKeywordOverlap",
            "F12_engineEmptyBelowMinExamples",
            "F13_engineInjectsAboveMinExamples",
            "F14_enginePromptHasFewShotHeader",
            "F15_engineRespectsTopK",
            "F16_engineIncludesNegativeExamples",
            "F17_saveFeedbackCorrectLabel",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 18 — v2.9 @Improve           ║");
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
        if (failed > 0) { System.out.println("\n  PHASE 18 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 18 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v2.9 @Improve few-shot learning operational.\n"); }
    }

    void F01_feedbackExampleStoresFields() {
        FeedbackExample ex = new FeedbackExample(
            "loan-check", "loan app input", "approved output",
            FeedbackExample.Label.GOOD, "well reasoned");
        assertEquals("loan-check",    ex.getMethodLabel(), "label");
        assertEquals("loan app input",ex.getInput(),       "input");
        assertEquals("approved output",ex.getOutput(),     "output");
        assertEquals("well reasoned", ex.getNote(),        "note");
        assertNotNull(ex.getId(),                          "id generated");
        assertNotNull(ex.getCreatedAt(),                   "timestamp set");
    }

    void F02_isGoodTrue() {
        FeedbackExample ex = new FeedbackExample("l","i","o",FeedbackExample.Label.GOOD,"n");
        assertTrue(ex.isGood(), "GOOD -> isGood");
        assertFalse(ex.isBad(), "GOOD -> not isBad");
    }

    void F03_isBadTrue() {
        FeedbackExample ex = new FeedbackExample("l","i","o",FeedbackExample.Label.BAD,"n");
        assertTrue(ex.isBad(), "BAD -> isBad");
        assertFalse(ex.isGood(), "BAD -> not isGood");
    }

    void F04_toPromptExampleIncludesInputOutput() {
        FeedbackExample ex = new FeedbackExample(
            "l","my input","my output",FeedbackExample.Label.GOOD,"great");
        String prompt = ex.toPromptExample(false);
        assertTrue(prompt.contains("my input"),  "input in prompt");
        assertTrue(prompt.contains("my output"), "output in prompt");
        assertTrue(prompt.contains("great"),     "note in prompt");
    }

    void F05_toPromptExampleNegativeHeader() {
        FeedbackExample ex = new FeedbackExample(
            "l","i","o",FeedbackExample.Label.BAD,"avoid this");
        String prompt = ex.toPromptExample(true);
        assertTrue(prompt.contains("AVOID"), "AVOID header for negative");
    }

    void F06_storesSaveExample() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("loan","i","o",FeedbackExample.Label.GOOD,"n"));
        assertEquals(1, store.count("loan"), "1 example stored");
    }

    void F07_findAllReturnsAll() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("m","i1","o1",FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("m","i2","o2",FeedbackExample.Label.BAD,"n"));
        store.save(new FeedbackExample("m","i3","o3",FeedbackExample.Label.GOOD,"n"));
        assertEquals(3, store.findAll("m").size(), "3 total examples");
    }

    void F08_countCorrect() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("x","i","o",FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("x","i","o",FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("y","i","o",FeedbackExample.Label.GOOD,"n"));
        assertEquals(2, store.count("x"), "2 for x");
        assertEquals(1, store.count("y"), "1 for y");
        assertEquals(0, store.count("z"), "0 for z");
    }

    void F09_findSimilarGoodReturnsGoodOnly() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("m","loan application","approved",FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("m","loan application","rejected",FeedbackExample.Label.BAD,"n"));
        List<FeedbackExample> good = store.findSimilarGood("m","loan application",5);
        assertTrue(good.stream().allMatch(FeedbackExample::isGood), "all GOOD");
        assertEquals(1, good.size(), "1 GOOD example");
    }

    void F10_findSimilarBadReturnsBadOnly() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("m","fraud check","flagged",FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("m","fraud check","missed it",FeedbackExample.Label.BAD,"n"));
        List<FeedbackExample> bad = store.findSimilarBad("m","fraud check",5);
        assertTrue(bad.stream().allMatch(FeedbackExample::isBad), "all BAD");
        assertEquals(1, bad.size(), "1 BAD example");
    }

    void F11_similarityRanksByKeywordOverlap() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        // High overlap with query "loan amount credit score"
        store.save(new FeedbackExample("m","loan amount credit score risk",
            "output A",FeedbackExample.Label.GOOD,"n"));
        // Low overlap
        store.save(new FeedbackExample("m","weather temperature humidity",
            "output B",FeedbackExample.Label.GOOD,"n"));
        List<FeedbackExample> results = store.findSimilarGood("m",
            "loan amount credit score", 2);
        assertEquals("output A", results.get(0).getOutput(),
            "high overlap example ranked first");
    }

    void F12_engineEmptyBelowMinExamples() {
        ImproveEngine engine = new ImproveEngine(new InProcessFeedbackStore());
        // minExamples=2 but store is empty
        String enrichment = engine.buildEnrichment(m("underwriteLoan"), "loan input");
        assertEquals("", enrichment, "empty when below minExamples");
    }

    void F13_engineInjectsAboveMinExamples() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("loan-check","loan input","good output",
            FeedbackExample.Label.GOOD,"excellent"));
        store.save(new FeedbackExample("loan-check","loan application","approved",
            FeedbackExample.Label.GOOD,"correct"));
        ImproveEngine engine = new ImproveEngine(store);
        // minExamples=2, we have 2
        String enrichment = engine.buildEnrichment(m("underwriteLoan"), "loan input");
        assertTrue(!enrichment.isEmpty(), "examples injected");
    }

    void F14_enginePromptHasFewShotHeader() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("loan-check","loan","good",
            FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("loan-check","loan","good2",
            FeedbackExample.Label.GOOD,"n"));
        ImproveEngine engine = new ImproveEngine(store);
        String enrichment = engine.buildEnrichment(m("underwriteLoan"), "loan input");
        assertTrue(enrichment.contains("FEW-SHOT"), "FEW-SHOT header present");
    }

    void F15_engineRespectsTopK() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        for (int i = 0; i < 10; i++) {
            store.save(new FeedbackExample("loan-check","loan example "+i,"output "+i,
                FeedbackExample.Label.GOOD,"n"));
        }
        ImproveEngine engine = new ImproveEngine(store);
        String enrichment = engine.buildEnrichment(m("underwriteLoan"), "loan example");
        // topK=3, count "Example" occurrences in prompt
        long exampleCount = enrichment.lines()
            .filter(l -> l.startsWith("--- Example")).count();
        assertEquals(3L, exampleCount, "topK=3 examples injected");
    }

    void F16_engineIncludesNegativeExamples() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        store.save(new FeedbackExample("fraud-check","fraud input","good output",
            FeedbackExample.Label.GOOD,"n"));
        store.save(new FeedbackExample("fraud-check","fraud input","bad output",
            FeedbackExample.Label.BAD,"missed the signal"));
        ImproveEngine engine = new ImproveEngine(store);
        String enrichment = engine.buildEnrichment(m("checkFraud"), "fraud input");
        assertTrue(enrichment.contains("AVOID"), "negative example included");
    }

    void F17_saveFeedbackCorrectLabel() {
        InProcessFeedbackStore store = new InProcessFeedbackStore();
        ImproveEngine engine = new ImproveEngine(store);
        FeedbackExample ex = engine.saveFeedback(
            m("underwriteLoan"), "input", "output",
            FeedbackExample.Label.GOOD, "well done");
        assertEquals("loan-check", ex.getMethodLabel(), "label from @Improve annotation");
        assertEquals(FeedbackExample.Label.GOOD, ex.getLabel(), "GOOD label");
        assertEquals(1, store.count("loan-check"), "stored in correct bucket");
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