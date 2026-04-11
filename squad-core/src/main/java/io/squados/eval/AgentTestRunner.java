package io.squados.eval;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.AgentTest;
import io.squados.context.AgentWrapper;
import io.squados.exception.EvalFailedException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Executes golden-set tests for agents annotated with @AgentTest.
 *
 * Test case JSON format (classpath resource):
 * <pre>
 *   [
 *     { "input": "What is 2+2?",       "expectedContains": "4" },
 *     { "input": "Summarise this doc", "expectedMinWords": 20  }
 *   ]
 * </pre>
 *
 * Zero external dependency — uses simple character-level JSON parsing.
 * For complex assertions use judgeModel (future LLM-as-judge integration).
 *
 * Usage:
 * <pre>
 *   AgentTestRunner runner = new AgentTestRunner();
 *   RunReport report = runner.run(wrapper);   // throws EvalFailedException if below threshold
 * </pre>
 */
public class AgentTestRunner {

    // ── Value types ───────────────────────────────────────────────────

    public record TestCase(String input, String expectedContains, int expectedMinWords) {}

    public record TestResult(String input, boolean passed, String output, String reason) {}

    public record RunReport(List<TestResult> results, float passRate, boolean passed) {
        @Override public String toString() {
            long ok = results.stream().filter(TestResult::passed).count();
            return String.format("AgentTestRunner: %d/%d passed (%.0f%%)",
                ok, results.size(), passRate * 100);
        }
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Run all test cases for the given wrapper using its @AgentTest annotation.
     *
     * @throws IllegalArgumentException if the agent is not annotated with @AgentTest
     * @throws EvalFailedException      if passRate &lt; @AgentTest.passRateMin()
     */
    public RunReport run(AgentWrapper wrapper) {
        AgentTest ann = wrapper.getAgentClass().getAnnotation(AgentTest.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                "Agent '" + wrapper.getName() + "' has no @AgentTest annotation");
        }

        List<TestCase>   cases   = loadTestCases(ann.testCasesPath());
        List<TestResult> results = new ArrayList<>(cases.size());

        for (TestCase tc : cases) {
            TaskContext ctx = new TaskContext(tc.input(), UUID.randomUUID().toString(), null);
            AgentResponse response;
            try {
                response = wrapper.execute(ctx);
            } catch (Exception e) {
                results.add(new TestResult(tc.input(), false, "",
                    "Exception during execution: " + e.getMessage()));
                continue;
            }

            String  output = response.content() != null ? response.content() : "";
            boolean passed = evaluate(output, tc);
            results.add(new TestResult(tc.input(), passed, output,
                passed ? "OK" : buildFailReason(output, tc)));
        }

        long  passCount   = results.stream().filter(TestResult::passed).count();
        float passRate    = cases.isEmpty() ? 1f : (float) passCount / cases.size();
        boolean allPassed = passRate >= ann.passRateMin();

        RunReport report = new RunReport(List.copyOf(results), passRate, allPassed);
        System.out.println("[AgentTest] " + wrapper.getName() + " — " + report);

        if (!allPassed) {
            throw new EvalFailedException(wrapper.getName(), passRate, ann.passRateMin());
        }
        return report;
    }

    // ── Evaluation ────────────────────────────────────────────────────

    private boolean evaluate(String output, TestCase tc) {
        if (tc.expectedContains() != null && !tc.expectedContains().isBlank()) {
            if (!output.toLowerCase().contains(tc.expectedContains().toLowerCase())) {
                return false;
            }
        }
        if (tc.expectedMinWords() > 0) {
            int wordCount = output.isBlank() ? 0 : output.trim().split("\\s+").length;
            if (wordCount < tc.expectedMinWords()) return false;
        }
        return true;
    }

    private String buildFailReason(String output, TestCase tc) {
        if (tc.expectedContains() != null
                && !output.toLowerCase().contains(tc.expectedContains().toLowerCase())) {
            return "Expected output to contain: '" + tc.expectedContains() + "'";
        }
        if (tc.expectedMinWords() > 0) {
            int wc = output.isBlank() ? 0 : output.trim().split("\\s+").length;
            return "Expected >= " + tc.expectedMinWords() + " words, got " + wc;
        }
        return "Assertion failed";
    }

    // ── JSON loading ──────────────────────────────────────────────────

    private List<TestCase> loadTestCases(String path) {
        try (InputStream is = AgentTestRunner.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException(
                    "[AgentTest] Test cases file not found on classpath: " + path);
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            return parseTestCases(json);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "[AgentTest] Failed to load test cases from: " + path, e);
        }
    }

    /**
     * Zero-dependency JSON array parser.
     * Handles: [{"input":"...","expectedContains":"...","expectedMinWords":N}]
     */
    private List<TestCase> parseTestCases(String json) {
        List<TestCase> cases = new ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);

        for (String obj : splitObjects(json)) {
            String input            = extractString(obj, "input");
            String expectedContains = extractString(obj, "expectedContains");
            int    expectedMinWords = extractInt(obj,    "expectedMinWords");
            if (input != null && !input.isBlank()) {
                cases.add(new TestCase(input, expectedContains, expectedMinWords));
            }
        }
        return cases;
    }

    /** Split top-level JSON objects from a flat comma-delimited sequence. */
    private List<String> splitObjects(String json) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if      (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) result.add(json.substring(start, i + 1)); }
        }
        return result;
    }

    /** Extract a JSON string value by key. Returns null if key not found. */
    private String extractString(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return null;
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = q1 + 1;
        while (q2 < obj.length()) {
            char c = obj.charAt(q2);
            if (c == '"' && obj.charAt(q2 - 1) != '\\') break;
            q2++;
        }
        return obj.substring(q1 + 1, q2)
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\");
    }

    /** Extract a JSON integer value by key. Returns 0 if key not found. */
    private int extractInt(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0) return 0;
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0) return 0;
        int start = colon + 1;
        while (start < obj.length() && Character.isWhitespace(obj.charAt(start))) start++;
        int end = start;
        while (end < obj.length() && Character.isDigit(obj.charAt(end))) end++;
        if (start == end) return 0;
        try { return Integer.parseInt(obj.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }
}
