package io.squados.tests;

import io.squados.pipeline.ConditionEvaluator;

/**
 * Phase 26 — v3.6 @Condition expression evaluator
 *
 * C01 — Empty condition expression always returns true
 * C02 — Null condition expression always returns true
 * C03 — contains("x") matches case-insensitively
 * C04 — contains("x") returns false when not found
 * C05 — notContains("x") returns true when not found
 * C06 — notContains("x") returns false when found
 * C07 — startsWith("x") matches prefix
 * C08 — startsWith("x") fails on non-prefix
 * C09 — endsWith("x") matches suffix
 * C10 — endsWith("x") fails on non-suffix
 * C11 — matches("regex") matches Java pattern
 * C12 — matches("regex") fails on mismatch
 * C13 — isEmpty returns true for blank text
 * C14 — isEmpty returns false for non-blank
 * C15 — isNotEmpty returns false for blank text
 * C16 — isNotEmpty returns true for non-blank
 * C17 — success predicate returns stepSuccess value
 * C18 — failure predicate returns !stepSuccess value
 * C19 — expr1 || expr2 short-circuits on first true
 * C20 — expr1 || expr2 evaluates both when first is false
 * C21 — expr1 && expr2 short-circuits on first false
 * C22 — expr1 && expr2 requires both true
 * C23 — Complex: contains("P1") || contains("P2")
 * C24 — success && contains("NEGATIVE") compound
 * C25 — Unknown predicate defaults to true (safe)
 */
public class SquadOsPhase26Tests {

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase26Tests();
        String[] tests = {
            "C01_emptyConditionTrue",
            "C02_nullConditionTrue",
            "C03_containsMatchesCaseInsensitive",
            "C04_containsReturnsFalseWhenMissing",
            "C05_notContainsTrueWhenMissing",
            "C06_notContainsFalseWhenFound",
            "C07_startsWithMatchesPrefix",
            "C08_startsWithFailsOnNonPrefix",
            "C09_endsWithMatchesSuffix",
            "C10_endsWithFailsOnNonSuffix",
            "C11_matchesJavaPattern",
            "C12_matchesFails",
            "C13_isEmptyTrueForBlank",
            "C14_isEmptyFalseForNonBlank",
            "C15_isNotEmptyFalseForBlank",
            "C16_isNotEmptyTrueForNonBlank",
            "C17_successPredicate",
            "C18_failurePredicate",
            "C19_orShortCircuitsOnFirstTrue",
            "C20_orEvalsBothWhenFirstFalse",
            "C21_andShortCircuitsOnFirstFalse",
            "C22_andRequiresBothTrue",
            "C23_complexOrCondition",
            "C24_complexAndWithSuccess",
            "C25_unknownPredicateDefaultsTrue",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 26 - v3.6 @Condition         ║");
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
        if (failed > 0) { System.out.println("\n  PHASE 26 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 26 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.6 @Condition expression evaluation operational.\n"); }
    }

    void C01_emptyConditionTrue() {
        assertTrue(ConditionEvaluator.evaluate("", "any text", true), "empty -> true");
    }

    void C02_nullConditionTrue() {
        assertTrue(ConditionEvaluator.evaluate(null, "any text", true), "null -> true");
    }

    void C03_containsMatchesCaseInsensitive() {
        assertTrue(ConditionEvaluator.evaluate("contains(\"NEGATIVE\")", "very negative result", true),
            "case-insensitive contains");
    }

    void C04_containsReturnsFalseWhenMissing() {
        assertFalse(ConditionEvaluator.evaluate("contains(\"URGENT\")", "routine update", true),
            "contains false when missing");
    }

    void C05_notContainsTrueWhenMissing() {
        assertTrue(ConditionEvaluator.evaluate("notContains(\"error\")", "all clear", true),
            "notContains true when missing");
    }

    void C06_notContainsFalseWhenFound() {
        assertFalse(ConditionEvaluator.evaluate("notContains(\"error\")", "error occurred", true),
            "notContains false when found");
    }

    void C07_startsWithMatchesPrefix() {
        assertTrue(ConditionEvaluator.evaluate("startsWith(\"ESCALATE\")", "ESCALATE: P1 issue", true),
            "startsWith match");
    }

    void C08_startsWithFailsOnNonPrefix() {
        assertFalse(ConditionEvaluator.evaluate("startsWith(\"ESCALATE\")", "No escalation needed", true),
            "startsWith fail");
    }

    void C09_endsWithMatchesSuffix() {
        assertTrue(ConditionEvaluator.evaluate("endsWith(\"confirmed\")", "Action confirmed", true),
            "endsWith match");
    }

    void C10_endsWithFailsOnNonSuffix() {
        assertFalse(ConditionEvaluator.evaluate("endsWith(\"confirmed\")", "Action pending", true),
            "endsWith fail");
    }

    void C11_matchesJavaPattern() {
        assertTrue(ConditionEvaluator.evaluate("matches(\".*P[12].*\")", "Priority P1 issue detected", true),
            "matches regex");
    }

    void C12_matchesFails() {
        assertFalse(ConditionEvaluator.evaluate("matches(\".*P[12].*\")", "routine task", true),
            "matches fail");
    }

    void C13_isEmptyTrueForBlank() {
        assertTrue(ConditionEvaluator.evaluate("isEmpty", "  ", true), "isEmpty true for blank");
        assertTrue(ConditionEvaluator.evaluate("isEmpty", "", true), "isEmpty true for empty");
    }

    void C14_isEmptyFalseForNonBlank() {
        assertFalse(ConditionEvaluator.evaluate("isEmpty", "hello", true), "isEmpty false for text");
    }

    void C15_isNotEmptyFalseForBlank() {
        assertFalse(ConditionEvaluator.evaluate("isNotEmpty", "", true), "isNotEmpty false for empty");
    }

    void C16_isNotEmptyTrueForNonBlank() {
        assertTrue(ConditionEvaluator.evaluate("isNotEmpty", "text", true), "isNotEmpty true");
    }

    void C17_successPredicate() {
        assertTrue(ConditionEvaluator.evaluate("success", "any", true), "success=true when step succeeded");
        assertFalse(ConditionEvaluator.evaluate("success", "any", false), "success=false when step failed");
    }

    void C18_failurePredicate() {
        assertFalse(ConditionEvaluator.evaluate("failure", "any", true), "failure=false when step succeeded");
        assertTrue(ConditionEvaluator.evaluate("failure", "any", false), "failure=true when step failed");
    }

    void C19_orShortCircuitsOnFirstTrue() {
        // First part is true — second part doesn't matter
        assertTrue(ConditionEvaluator.evaluate("contains(\"P1\") || contains(\"P2\")",
            "P1 critical alert", true), "OR short-circuits on first true");
    }

    void C20_orEvalsBothWhenFirstFalse() {
        // First part is false, second is true
        assertTrue(ConditionEvaluator.evaluate("contains(\"P1\") || contains(\"P2\")",
            "P2 high priority", true), "OR evaluates second when first false");
        // Both false
        assertFalse(ConditionEvaluator.evaluate("contains(\"P1\") || contains(\"P2\")",
            "routine update", true), "OR false when both false");
    }

    void C21_andShortCircuitsOnFirstFalse() {
        // First part is false — result is false regardless
        assertFalse(ConditionEvaluator.evaluate("success && contains(\"NEGATIVE\")",
            "NEGATIVE result", false), "AND short-circuits on first false");
    }

    void C22_andRequiresBothTrue() {
        assertTrue(ConditionEvaluator.evaluate("success && contains(\"NEGATIVE\")",
            "very NEGATIVE outcome", true), "AND true when both true");
        assertFalse(ConditionEvaluator.evaluate("success && contains(\"NEGATIVE\")",
            "positive outcome", true), "AND false when second false");
    }

    void C23_complexOrCondition() {
        String expr = "contains(\"P1\") || contains(\"P2\")";
        assertTrue(ConditionEvaluator.evaluate(expr, "P1 critical", true), "P1 matches");
        assertTrue(ConditionEvaluator.evaluate(expr, "P2 high", true), "P2 matches");
        assertFalse(ConditionEvaluator.evaluate(expr, "P3 low", true), "P3 no match");
    }

    void C24_complexAndWithSuccess() {
        String expr = "success && contains(\"NEGATIVE\")";
        assertTrue(ConditionEvaluator.evaluate(expr, "The NEGATIVE sentiment was detected", true),
            "AND matches when both true");
        assertFalse(ConditionEvaluator.evaluate(expr, "The NEGATIVE sentiment", false),
            "AND fails when success=false");
        assertFalse(ConditionEvaluator.evaluate(expr, "Positive result", true),
            "AND fails when contains false");
    }

    void C25_unknownPredicateDefaultsTrue() {
        // Unknown predicates should default to true (safe — don't skip)
        assertTrue(ConditionEvaluator.evaluate("unknownPredicate", "text", true),
            "unknown predicate defaults true");
    }

    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}
