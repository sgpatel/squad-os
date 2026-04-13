package io.squados.tests;

import io.squados.agent.AgentResponse;
import io.squados.annotation.*;
import io.squados.config.SquadConfig;
import io.squados.context.SquadContext;
import io.squados.llm.MockLlmPort;
import io.squados.structured.*;

import java.util.List;

/**
 * Phase 33 — @StructuredOutput tests.
 *
 * SO01  JsonSchemaGenerator builds correct schema from plain POJO
 * SO02  JsonSchemaGenerator includes @OutputField descriptions and examples
 * SO03  JsonSchemaGenerator marks optional fields
 * SO04  StructuredOutputParser extracts JSON from prose response
 * SO05  StructuredOutputParser populates all field types (String, double, boolean)
 * SO06  StructuredOutputParser handles missing optional fields gracefully
 * SO07  StructuredOutputParser retries when JSON is malformed
 * SO08  StructuredOutputParser throws StructuredOutputException after max retries
 * SO09  @StructuredOutput injects schema into agent system prompt
 * SO10  AgentResponse.structuredOutput() returns typed POJO
 */
public class SquadOsPhase33Tests {

    // ── Schema POJOs ──────────────────────────────────────────────────

    static class SentimentReport {
        @OutputField(description = "sentiment label", example = "POSITIVE", required = true)
        public String sentiment;

        @OutputField(description = "confidence 0.0-1.0", example = "0.92", required = true)
        public double confidence;

        @OutputField(description = "brief rationale", required = false)
        public String rationale;
    }

    static class SimpleOutput {
        public String value;
        public int    count;
        public boolean active;
    }

    // ── Agent stubs ───────────────────────────────────────────────────

    @Agent(role = AgentRole.ANALYST, name = "SentimentAgent",
           description = "Analyses sentiment of text.")
    @StructuredOutput(schema = SentimentReport.class, retryOnMalformed = true, maxRetries = 2)
    static class SentimentAgent {}

    // ── Runner ────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 33 — @StructuredOutput                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "SO01_schemaGeneratorBuildsJsonTemplate",
            "SO02_schemaGeneratorIncludesOutputFieldAnnotations",
            "SO03_schemaGeneratorMarksOptionalFields",
            "SO04_parserExtractsJsonFromProse",
            "SO05_parserPopulatesAllFieldTypes",
            "SO06_parserHandlesMissingOptionalField",
            "SO07_parserRetriesOnMalformed",
            "SO08_parserThrowsAfterMaxRetries",
            "SO09_structuredOutputInjectsSchemaIntoPrompt",
            "SO10_agentResponseStructuredOutputReturnsTyped",
        };
        var t = new SquadOsPhase33Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, c.getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.printf("Phase 33 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    void SO01_schemaGeneratorBuildsJsonTemplate() {
        String template = JsonSchemaGenerator.buildJsonTemplate(SimpleOutput.class);
        assert template.contains("\"value\"")  : "Should contain field 'value'";
        assert template.contains("\"count\"")  : "Should contain field 'count'";
        assert template.contains("\"active\"") : "Should contain field 'active'";
        assert template.startsWith("{")        : "Should start with {";
        assert template.endsWith("}")          : "Should end with }";
    }

    void SO02_schemaGeneratorIncludesOutputFieldAnnotations() {
        String template = JsonSchemaGenerator.buildJsonTemplate(SentimentReport.class);
        assert template.contains("sentiment label") : "Should include @OutputField description";
        assert template.contains("POSITIVE")        : "Should include @OutputField example";
        assert template.contains("confidence")      : "Should include confidence field";
    }

    void SO03_schemaGeneratorMarksOptionalFields() {
        String template = JsonSchemaGenerator.buildJsonTemplate(SentimentReport.class);
        assert template.contains("optional") : "Should mark rationale as optional";
    }

    void SO04_parserExtractsJsonFromProse() {
        String prose = "Sure! Here is the analysis: {\"value\": \"hello\", \"count\": 3, \"active\": true} That's all.";
        String json = StructuredOutputParser.extractJson(prose);
        assert json.startsWith("{") && json.endsWith("}")
            : "Should extract JSON object from prose";
    }

    void SO05_parserPopulatesAllFieldTypes() throws Exception {
        String json = "{\"value\": \"test\", \"count\": 42, \"active\": true}";
        SimpleOutput obj = StructuredOutputParser.populate(SimpleOutput.class, json);
        assert "test".equals(obj.value) : "String field should be 'test'";
        assert obj.count == 42          : "int field should be 42";
        assert obj.active               : "boolean field should be true";
    }

    void SO06_parserHandlesMissingOptionalField() throws Exception {
        // rationale is optional — should succeed even if absent
        String json = "{\"sentiment\": \"POSITIVE\", \"confidence\": 0.95}";
        SentimentReport report = StructuredOutputParser.populate(SentimentReport.class, json);
        assert "POSITIVE".equals(report.sentiment) : "Sentiment should be POSITIVE";
        assert report.confidence == 0.95            : "Confidence should be 0.95";
        assert report.rationale == null             : "Optional rationale should be null";
    }

    void SO07_parserRetriesOnMalformed() {
        // First call returns garbled JSON, second call returns valid JSON
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("{\"value\": \"retry-success\", \"count\": 1, \"active\": false}");

        StructuredOutputResult<SimpleOutput> result = StructuredOutputParser.parse(
            "not json at all",    // bad first response
            SimpleOutput.class,
            true, 2,
            "system prompt", llm,
            new io.squados.llm.LlmOptions(0.3f, 512, null));

        assert result.isSuccess()               : "Should succeed after retry";
        assert result.parseAttempts() >= 2      : "Should have retried";
        assert "retry-success".equals(result.value().value) : "Value should be 'retry-success'";
    }

    void SO08_parserThrowsAfterMaxRetries() {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("still not json");

        boolean threw = false;
        try {
            StructuredOutputParser.parse("garbage", SimpleOutput.class,
                true, 1, "sys", llm,
                new io.squados.llm.LlmOptions(0.3f, 512, null));
        } catch (io.squados.exception.StructuredOutputException e) {
            threw = true;
            assert e.getAttempts() > 0 : "Should record attempts";
        }
        assert threw : "Should throw StructuredOutputException after max retries";
    }

    void SO09_structuredOutputInjectsSchemaIntoPrompt() {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Checking prompt injection");

        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentAgent.class));
        SquadContext ctx = new SquadContext(cfg, llm);
        ctx.boot();

        // Submit anything — the LLM call record will show the system prompt with schema
        ctx.submit("Analyse: 'Great product!'");

        var lastCall = llm.getLastCall();
        assert lastCall != null : "LLM should have been called";
        assert lastCall.systemPrompt().contains("STRUCTURED OUTPUT")
            : "System prompt should contain schema injection: " + lastCall.systemPrompt().substring(0, Math.min(200, lastCall.systemPrompt().length()));
    }

    void SO10_agentResponseStructuredOutputReturnsTyped() {
        MockLlmPort llm = new MockLlmPort();
        // Return valid JSON matching SentimentReport
        llm.setDefaultResponse(
            "{\"sentiment\": \"POSITIVE\", \"confidence\": 0.97, \"rationale\": \"Clearly positive tone.\"}");

        SquadConfig cfg = SquadConfig.forTesting(List.of(SentimentAgent.class));
        SquadContext ctx = new SquadContext(cfg, llm);
        ctx.boot();

        AgentResponse r = ctx.submit("Analyse: 'Absolutely fantastic!'");
        assert r.isSuccess() : "Response should succeed: " + r.errorMessage();

        SentimentReport report = r.structuredOutput(SentimentReport.class);
        assert report != null              : "Structured output should be non-null";
        assert "POSITIVE".equals(report.sentiment) : "Sentiment should be POSITIVE";
        assert report.confidence > 0.9     : "Confidence should be > 0.9";

        System.out.printf("         → sentiment=%s confidence=%.2f%n",
            report.sentiment, report.confidence);
    }
}
