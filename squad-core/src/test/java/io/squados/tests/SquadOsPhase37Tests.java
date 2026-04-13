package io.squados.tests;

import io.squados.annotation.AgentRole;
import io.squados.otel.*;
import io.squados.trace.AgentSpan;

import java.time.Instant;
import java.util.Map;

/**
 * Phase 37 — OpenTelemetry Export tests.
 *
 * OT01  OtelExporterConfig.builder() creates valid config
 * OT02  OtelExporterConfig.fromEnv() reads OTEL env vars with defaults
 * OT03  OtelExporterConfig.traceEndpoint() appends /v1/traces
 * OT04  OtelTraceContext.root() generates valid W3C traceparent
 * OT05  OtelTraceContext.child() preserves traceId
 * OT06  OtelTraceContext.parse() decodes traceparent header
 * OT07  OtelSpanExporter.toOtlpJson() produces valid OTLP JSON
 * OT08  OtelSpanExporter.toOtlpJson() includes all required span fields
 * OT09  OtelSpanExporter.name() returns meaningful exporter name
 * OT10  OtelSpanExporter.export() is non-blocking (submits to executor)
 */
public class SquadOsPhase37Tests {

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 37 — OpenTelemetry Export                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "OT01_configBuilderCreatesValidConfig",
            "OT02_configFromEnvUsesDefaults",
            "OT03_configTraceEndpointAppendsPath",
            "OT04_traceContextRootGeneratesValidParent",
            "OT05_traceContextChildPreservesTraceId",
            "OT06_traceContextParseDecodesHeader",
            "OT07_exporterToOtlpJsonProducesJson",
            "OT08_exporterJsonIncludesAllSpanFields",
            "OT09_exporterNameIsDescriptive",
            "OT10_exporterExportIsNonBlocking",
        };
        var t = new SquadOsPhase37Tests();
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
        System.out.printf("Phase 37 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    void OT01_configBuilderCreatesValidConfig() {
        OtelExporterConfig cfg = OtelExporterConfig.builder()
            .endpoint("http://jaeger:4318")
            .serviceName("my-squad")
            .timeoutMs(3000)
            .header("Authorization", "Bearer token123")
            .build();

        assert "http://jaeger:4318".equals(cfg.endpoint()) : "Endpoint should match";
        assert "my-squad".equals(cfg.serviceName())        : "Service name should match";
        assert cfg.timeoutMs() == 3000                     : "Timeout should match";
        assert "Bearer token123".equals(cfg.headers().get("Authorization"))
            : "Header should be set";
        assert cfg.enabled() : "Should be enabled by default";
    }

    void OT02_configFromEnvUsesDefaults() {
        OtelExporterConfig cfg = OtelExporterConfig.fromEnv();
        assert cfg.endpoint() != null    : "Endpoint should not be null";
        assert cfg.serviceName() != null : "Service name should not be null";
        // Default values when env vars not set
        assert cfg.endpoint().startsWith("http") : "Endpoint should be HTTP";
    }

    void OT03_configTraceEndpointAppendsPath() {
        OtelExporterConfig cfg = OtelExporterConfig.builder()
            .endpoint("http://collector:4318")
            .build();
        assert cfg.traceEndpoint().equals("http://collector:4318/v1/traces")
            : "Trace endpoint should append /v1/traces: " + cfg.traceEndpoint();

        // Test with trailing slash
        OtelExporterConfig cfg2 = OtelExporterConfig.builder()
            .endpoint("http://collector:4318/")
            .build();
        assert cfg2.traceEndpoint().equals("http://collector:4318/v1/traces")
            : "Should strip trailing slash before appending";
    }

    void OT04_traceContextRootGeneratesValidParent() {
        OtelTraceContext ctx = OtelTraceContext.root();
        assert ctx.traceId() != null && ctx.traceId().length() == 32
            : "TraceId should be 32 hex chars: " + ctx.traceId();
        assert ctx.spanId() != null && ctx.spanId().length() >= 8
            : "SpanId should be at least 8 hex chars";
        assert "01".equals(ctx.flags()) : "Flags should be 01 (sampled)";
        assert ctx.traceparent().startsWith("00-")
            : "Traceparent should start with '00-'";
    }

    void OT05_traceContextChildPreservesTraceId() {
        OtelTraceContext root  = OtelTraceContext.root();
        OtelTraceContext child = root.child();
        assert root.traceId().equals(child.traceId())
            : "Child should have same traceId as parent";
        assert !root.spanId().equals(child.spanId())
            : "Child should have different spanId";
    }

    void OT06_traceContextParseDecodesHeader() {
        String header = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        OtelTraceContext ctx = OtelTraceContext.parse(header);
        assert ctx != null : "Parse should succeed";
        assert "4bf92f3577b34da6a3ce929d0e0e4736".equals(ctx.traceId())
            : "TraceId should match";
        assert "00f067aa0ba902b7".equals(ctx.spanId())
            : "SpanId should match";
        assert "01".equals(ctx.flags()) : "Flags should be 01";
    }

    void OT07_exporterToOtlpJsonProducesJson() {
        OtelExporterConfig cfg = OtelExporterConfig.builder()
            .endpoint("http://localhost:4318")
            .serviceName("test-service")
            .build();
        OtelSpanExporter exporter = new OtelSpanExporter(cfg);

        AgentSpan span = buildTestSpan();
        String json = exporter.toOtlpJson(span);

        assert json != null && !json.isBlank()    : "JSON should not be blank";
        assert json.startsWith("{")               : "JSON should start with {";
        assert json.endsWith("}")                 : "JSON should end with }";
        assert json.contains("resourceSpans")     : "Should contain resourceSpans";
        assert json.contains("scopeSpans")        : "Should contain scopeSpans";
        assert json.contains("spans")             : "Should contain spans";

        exporter.shutdown();
    }

    void OT08_exporterJsonIncludesAllSpanFields() {
        OtelExporterConfig cfg = OtelExporterConfig.builder()
            .endpoint("http://localhost:4318")
            .serviceName("my-squad")
            .build();
        OtelSpanExporter exporter = new OtelSpanExporter(cfg);

        AgentSpan span = buildTestSpan();
        String json = exporter.toOtlpJson(span);

        assert json.contains("agent.name")        : "Should include agent.name attribute";
        assert json.contains("agent.role")        : "Should include agent.role attribute";
        assert json.contains("agent.prompt_tokens"): "Should include token counts";
        assert json.contains("my-squad")           : "Should include service name";
        assert json.contains("startTimeUnixNano")  : "Should include start time";
        assert json.contains("endTimeUnixNano")    : "Should include end time";
        assert json.contains("STATUS_CODE_OK")     : "Should include OK status for success";

        exporter.shutdown();
    }

    void OT09_exporterNameIsDescriptive() {
        OtelExporterConfig cfg = OtelExporterConfig.builder()
            .endpoint("http://jaeger:4318")
            .build();
        OtelSpanExporter exporter = new OtelSpanExporter(cfg);
        assert exporter.name().contains("jaeger")  : "Name should contain endpoint host";
        assert exporter.name().contains("/v1/traces") : "Name should contain OTLP path";
        exporter.shutdown();
    }

    void OT10_exporterExportIsNonBlocking() {
        // Disabled exporter — export() should be instant (no HTTP call attempted)
        OtelExporterConfig cfg = OtelExporterConfig.builder()
            .endpoint("http://localhost:4318")
            .enabled(false)
            .build();
        OtelSpanExporter exporter = new OtelSpanExporter(cfg);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            exporter.export(buildTestSpan());
        }
        long elapsed = System.currentTimeMillis() - start;

        assert elapsed < 500 : "100 disabled exports should complete in < 500ms: " + elapsed + "ms";
        assert exporter.exportedCount() == 0 : "Disabled exporter should not count exports";
        exporter.shutdown();
    }

    // ── Helper ────────────────────────────────────────────────────────

    private AgentSpan buildTestSpan() {
        return new AgentSpan.Builder()
            .traceId("abc123traceId00000000000000000000")
            .spanId("spanId0000000001")
            .spanName("TestAgent/execute")
            .agentRole(AgentRole.ANALYST)
            .agentName("TestAgent")
            .startTime(Instant.now().minusMillis(50))
            .endTime(Instant.now())
            .durationMs(50)
            .status(AgentSpan.Status.OK)
            .promptTokens(120)
            .completionTokens(80)
            .inputLength(200)
            .outputLength(150)
            .build();
    }
}
