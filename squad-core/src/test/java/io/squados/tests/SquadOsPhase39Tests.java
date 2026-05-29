package io.squados.tests;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 39 — GraalVM Native Image metadata tests.
 *
 * GV01  reflect-config.json exists on the classpath
 * GV02  reflect-config.json is a valid JSON array (starts with '[', ends with ']')
 * GV03  reflect-config.json contains all required annotation class names
 * GV04  reflect-config.json contains all required record class names
 * GV05  reflect-config.json contains all required key execution class names
 * GV06  reflect-config.json has no duplicate "name" entries
 * GV07  resource-config.json exists on the classpath
 * GV08  resource-config.json contains the squad.properties pattern
 * GV09  native-image.properties exists on the classpath
 * GV10  native-image.properties contains initialize-at-build-time directive
 */
public class SquadOsPhase39Tests {

    private static final String BASE = "/META-INF/native-image/io.github.sgpatel/squad-core/";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 39 — GraalVM Native Image Metadata            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "GV01_reflectConfigExistsOnClasspath",
            "GV02_reflectConfigIsValidJsonArray",
            "GV03_reflectConfigContainsAnnotationClasses",
            "GV04_reflectConfigContainsRecordTypes",
            "GV05_reflectConfigContainsKeyExecutionClasses",
            "GV06_reflectConfigHasNoDuplicateEntries",
            "GV07_resourceConfigExistsOnClasspath",
            "GV08_resourceConfigContainsPropertiesPattern",
            "GV09_nativeImagePropertiesExistsOnClasspath",
            "GV10_nativeImagePropertiesContainsBuildTimeDirective",
        };
        var t = new SquadOsPhase39Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (java.lang.reflect.InvocationTargetException ite) {
                System.out.printf("  [FAIL] %s — %s%n", test, ite.getCause().getMessage());
                failed++;
            } catch (Exception e) {
                System.out.printf("  [FAIL] %s — %s%n", test, e.getMessage());
                failed++;
            }
        }

        System.out.println();
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println();
        if (failed > 0) {
            throw new RuntimeException("Phase 39 tests failed: " + failed + " failure(s)");
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private String readResource(String filename) throws Exception {
        try (InputStream in = SquadOsPhase39Tests.class.getResourceAsStream(BASE + filename)) {
            if (in == null) return null;
            return new String(in.readAllBytes());
        }
    }

    /**
     * Extracts all "name" values from the reflect-config JSON array.
     * Simple string parsing — no external JSON library required.
     */
    private List<String> extractNames(String json) {
        List<String> names = new ArrayList<>();
        int idx = 0;
        while ((idx = json.indexOf("\"name\"", idx)) >= 0) {
            int colon = json.indexOf(':', idx + 6);
            if (colon < 0) break;
            int open = json.indexOf('"', colon + 1);
            if (open < 0) break;
            int close = json.indexOf('"', open + 1);
            if (close < 0) break;
            names.add(json.substring(open + 1, close));
            idx = close + 1;
        }
        return names;
    }

    // -----------------------------------------------------------------------
    // tests
    // -----------------------------------------------------------------------

    void GV01_reflectConfigExistsOnClasspath() throws Exception {
        String content = readResource("reflect-config.json");
        if (content == null) {
            throw new AssertionError("reflect-config.json not found at " + BASE);
        }
        if (content.isBlank()) {
            throw new AssertionError("reflect-config.json is empty");
        }
    }

    void GV02_reflectConfigIsValidJsonArray() throws Exception {
        String content = readResource("reflect-config.json");
        if (content == null) throw new AssertionError("reflect-config.json not found");
        String trimmed = content.strip();
        if (!trimmed.startsWith("[")) {
            throw new AssertionError("reflect-config.json does not start with '['; got: " + trimmed.substring(0, Math.min(20, trimmed.length())));
        }
        if (!trimmed.endsWith("]")) {
            throw new AssertionError("reflect-config.json does not end with ']'");
        }
    }

    void GV03_reflectConfigContainsAnnotationClasses() throws Exception {
        String content = readResource("reflect-config.json");
        if (content == null) throw new AssertionError("reflect-config.json not found");
        List<String> names = extractNames(content);
        String[] required = {
            "io.squados.annotation.Agent",
            "io.squados.annotation.Reflexion",
            "io.squados.annotation.Debate",
            "io.squados.annotation.StructuredOutput",
            "io.squados.annotation.Benchmark",
            "io.squados.annotation.Guardrails",
            "io.squados.annotation.SquadApplication",
        };
        for (String fqn : required) {
            if (!names.contains(fqn)) {
                throw new AssertionError("reflect-config.json is missing annotation class: " + fqn);
            }
        }
    }

    void GV04_reflectConfigContainsRecordTypes() throws Exception {
        String content = readResource("reflect-config.json");
        if (content == null) throw new AssertionError("reflect-config.json not found");
        List<String> names = extractNames(content);
        String[] required = {
            "io.squados.otel.OtelTraceContext",
            "io.squados.optimize.PromptVersion",
            "io.squados.trace.AgentSpan",
            "io.squados.llm.LlmOptions",
            "io.squados.llm.LlmResponse",
            "io.squados.guardrail.GuardrailResult",
            "io.squados.guardrail.GuardrailViolation",
            "io.squados.durable.WorkflowStep",
            "io.squados.plan.PlanIteration",
            "io.squados.reflexion.ReflexionIteration",
        };
        for (String fqn : required) {
            if (!names.contains(fqn)) {
                throw new AssertionError("reflect-config.json is missing record class: " + fqn);
            }
        }
    }

    void GV05_reflectConfigContainsKeyExecutionClasses() throws Exception {
        String content = readResource("reflect-config.json");
        if (content == null) throw new AssertionError("reflect-config.json not found");
        List<String> names = extractNames(content);
        String[] required = {
            "io.squados.context.AgentWrapper",
            "io.squados.context.SquadContext",
            "io.squados.agent.AgentResponse",
            "io.squados.structured.StructuredOutputParser",
            "io.squados.structured.JsonSchemaGenerator",
            "io.squados.eval.EvalScore",
            "io.squados.guardrail.GuardrailEngine",
        };
        for (String fqn : required) {
            if (!names.contains(fqn)) {
                throw new AssertionError("reflect-config.json is missing key execution class: " + fqn);
            }
        }
    }

    void GV06_reflectConfigHasNoDuplicateEntries() throws Exception {
        String content = readResource("reflect-config.json");
        if (content == null) throw new AssertionError("reflect-config.json not found");
        List<String> names = extractNames(content);
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String name : names) {
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new AssertionError("reflect-config.json has duplicate entries: " + duplicates);
        }
    }

    void GV07_resourceConfigExistsOnClasspath() throws Exception {
        String content = readResource("resource-config.json");
        if (content == null) {
            throw new AssertionError("resource-config.json not found at " + BASE);
        }
        if (content.isBlank()) {
            throw new AssertionError("resource-config.json is empty");
        }
    }

    void GV08_resourceConfigContainsPropertiesPattern() throws Exception {
        String content = readResource("resource-config.json");
        if (content == null) throw new AssertionError("resource-config.json not found");
        if (!content.contains("squad") || !content.contains("properties")) {
            throw new AssertionError("resource-config.json does not contain a squad.properties or squad-default.properties pattern");
        }
        // Verify it is a valid JSON object
        String trimmed = content.strip();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new AssertionError("resource-config.json is not a valid JSON object");
        }
    }

    void GV09_nativeImagePropertiesExistsOnClasspath() throws Exception {
        String content = readResource("native-image.properties");
        if (content == null) {
            throw new AssertionError("native-image.properties not found at " + BASE);
        }
        if (content.isBlank()) {
            throw new AssertionError("native-image.properties is empty");
        }
    }

    void GV10_nativeImagePropertiesContainsBuildTimeDirective() throws Exception {
        String content = readResource("native-image.properties");
        if (content == null) throw new AssertionError("native-image.properties not found");
        if (!content.contains("initialize-at-build-time")) {
            throw new AssertionError("native-image.properties does not contain '--initialize-at-build-time' directive");
        }
    }
}
