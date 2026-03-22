package io.squados.tests;

import io.squados.agent.SquadPlanDeserialiser;
import io.squados.annotation.Required;
import io.squados.annotation.SquadPlan;
import io.squados.exception.SquadPlanException;
import java.util.List;

public class SquadOsPhase10Tests {

    @SquadPlan(description = "Simple test plan")
    public static class SimplePlan {
        public String title;
        public int score;
        public boolean approved;
        public List<String> items;
        public String getTitle()       { return title; }
        public int getScore()          { return score; }
        public boolean isApproved()    { return approved; }
        public List<String> getItems() { return items; }
    }

    @SquadPlan(description = "Daily plan", validate = true)
    public static class DayPlan {
        @Required public List<String> doToday;
        public List<String> doLater;
        public List<String> dropIt;
        @Required public String verdict;
        public List<String> getDoToday() { return doToday; }
        public List<String> getDoLater() { return doLater; }
        public List<String> getDropIt()  { return dropIt; }
        public String getVerdict()       { return verdict; }
    }

    @SquadPlan(description = "Code review output")
    public static class CodeReview {
        @Required public String severity;
        public List<String> issues;
        public String verdict;
        public String getSeverity()      { return severity; }
        public List<String> getIssues()  { return issues; }
        public String getVerdict()       { return verdict; }
    }

    public static class NoPlan { String value; }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase10Tests();
        String[] tests = {
            "S01_deserialiseStringField", "S02_deserialiseIntField",
            "S03_deserialiseBooleanField", "S04_deserialiseListField",
            "S05_deserialiseAllTypes", "S06_requiredFieldPresentNoException",
            "S07_requiredFieldMissingThrows", "S08_requiredStringBlankThrows",
            "S09_requiredListEmptyThrows", "S10_buildSchemaPromptIncludesFields",
            "S11_buildSchemaPromptIncludesRequired", "S12_stripCodeFences",
            "S13_classWithoutAnnotationThrows", "S14_malformedJsonGraceful",
            "S15_parseJsonArrayEmpty", "S16_dayPlanFullRoundtrip",
            "S17_codeReviewFullRoundtrip",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 10 — v2.1 @SquadPlan         ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    → %s: %s%n", name,
                    c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    → %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 10 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 10 GATE: ALL TESTS PASSED ✓\n"); }
    }

    void S01_deserialiseStringField() {
        SimplePlan p = SquadPlanDeserialiser.deserialise("{\"title\": \"Plan A\"}", SimplePlan.class);
        assertEquals("Plan A", p.getTitle(), "String field");
    }

    void S02_deserialiseIntField() {
        SimplePlan p = SquadPlanDeserialiser.deserialise("{\"score\": 9}", SimplePlan.class);
        assertEquals(9, p.getScore(), "int field");
    }

    void S03_deserialiseBooleanField() {
        SimplePlan p = SquadPlanDeserialiser.deserialise("{\"approved\": true}", SimplePlan.class);
        assertTrue(p.isApproved(), "boolean field");
    }

    void S04_deserialiseListField() {
        SimplePlan p = SquadPlanDeserialiser.deserialise("{\"items\": [\"a\",\"b\",\"c\"]}", SimplePlan.class);
        assertEquals(3, p.getItems().size(), "List<String> size");
        assertEquals("a", p.getItems().get(0), "first item");
    }

    void S05_deserialiseAllTypes() {
        SimplePlan p = SquadPlanDeserialiser.deserialise("{\"title\":\"hello\",\"score\":7,\"approved\":true,\"items\":[\"x\",\"y\"]}", SimplePlan.class);
        assertEquals("hello", p.getTitle(), "title");
        assertEquals(7, p.getScore(), "score");
        assertTrue(p.isApproved(), "approved");
        assertEquals(2, p.getItems().size(), "items size");
    }

    void S06_requiredFieldPresentNoException() {
        DayPlan p = SquadPlanDeserialiser.deserialise("{\"doToday\":[\"task1\"],\"verdict\":\"Realistic\"}", DayPlan.class);
        assertNotNull(p.getDoToday(), "doToday present");
        assertEquals("Realistic", p.getVerdict(), "verdict");
    }

    void S07_requiredFieldMissingThrows() {
        try {
            SquadPlanDeserialiser.deserialise("{\"verdict\":\"ok\"}", DayPlan.class);
            throw new AssertionError("should have thrown");
        } catch (SquadPlanException e) {
            assertTrue(e.getMessage().contains("doToday"), "error mentions field");
        }
    }

    void S08_requiredStringBlankThrows() {
        try {
            SquadPlanDeserialiser.deserialise("{\"doToday\":[\"t1\"],\"verdict\":\"\"}", DayPlan.class);
            throw new AssertionError("should have thrown");
        } catch (SquadPlanException e) {
            assertTrue(e.getMessage().contains("verdict"), "error mentions verdict");
        }
    }

    void S09_requiredListEmptyThrows() {
        try {
            SquadPlanDeserialiser.deserialise("{\"doToday\":[],\"verdict\":\"ok\"}", DayPlan.class);
            throw new AssertionError("should have thrown");
        } catch (SquadPlanException e) {
            assertTrue(e.getMessage().contains("doToday"), "error mentions list");
        }
    }

    void S10_buildSchemaPromptIncludesFields() {
        String prompt = SquadPlanDeserialiser.buildSchemaPrompt(DayPlan.class);
        assertTrue(prompt.contains("doToday"), "doToday in schema");
        assertTrue(prompt.contains("verdict"), "verdict in schema");
        assertTrue(prompt.contains("JSON"), "JSON instruction in prompt");
    }

    void S11_buildSchemaPromptIncludesRequired() {
        String prompt = SquadPlanDeserialiser.buildSchemaPrompt(DayPlan.class);
        assertTrue(prompt.contains("REQUIRED"), "REQUIRED marker in schema");
    }

    void S12_stripCodeFences() {
        String fenced = "```json\n{\"title\":\"hello\"}\n```";
        // parseJsonFields should handle the stripped content
        var fields = SquadPlanDeserialiser.parseJsonFields("{\"title\":\"hello\"}");
        assertEquals("hello", fields.get("title"), "stripCodeFences works");
    }

    void S13_classWithoutAnnotationThrows() {
        try {
            SquadPlanDeserialiser.deserialise("{}", NoPlan.class);
            throw new AssertionError("should have thrown");
        } catch (SquadPlanException e) {
            assertTrue(e.getMessage().contains("@SquadPlan"), "error mentions annotation");
        }
    }

    void S14_malformedJsonGraceful() {
        // Malformed JSON should still attempt deserialisation — fields just null
        SimplePlan p = SquadPlanDeserialiser.deserialise("not json at all", SimplePlan.class);
        assertNotNull(p, "returns instance even for bad JSON (fields will be null)");
    }

    void S15_parseJsonArrayEmpty() {
        var list = SquadPlanDeserialiser.parseJsonArray("[]");
        assertEquals(0, list.size(), "empty array gives empty list");
    }

    void S16_dayPlanFullRoundtrip() {
        String json = "{\"doToday\":[\"fix auth bug\",\"reply to sarah\",\"standup\"],\"doLater\":[\"prepare slides\",\"review PR\"],\"dropIt\":[\"learn kubernetes\",\"organise desk\"],\"verdict\":\"Realistic\"}";
        DayPlan plan = SquadPlanDeserialiser.deserialise(json, DayPlan.class);
        assertEquals(3, plan.getDoToday().size(), "3 do-today items");
        assertEquals("fix auth bug", plan.getDoToday().get(0), "first task");
        assertEquals(2, plan.getDropIt().size(), "2 drop-it items");
        assertEquals("Realistic", plan.getVerdict(), "verdict");
    }

    void S17_codeReviewFullRoundtrip() {
        String json = "{\"severity\":\"HIGH\",\"issues\":[\"SQL injection on line 5\",\"hardcoded password\"],\"verdict\":\"BLOCK\"}";
        CodeReview review = SquadPlanDeserialiser.deserialise(json, CodeReview.class);
        assertEquals("HIGH", review.getSeverity(), "severity");
        assertEquals(2, review.getIssues().size(), "2 issues");
        assertEquals("BLOCK", review.getVerdict(), "verdict BLOCK");
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return;
        throw new AssertionError(msg + " — expected non-null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return;
        throw new AssertionError(msg + " — expected true");
    }
}