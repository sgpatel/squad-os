package io.squados.otel;

import io.squados.trace.AgentSpan;
import io.squados.trace.TraceExporter;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exports SquadOS {@link AgentSpan}s as OTLP/HTTP JSON spans to any
 * OpenTelemetry-compatible collector (Jaeger, Zipkin, Grafana Tempo,
 * Datadog, Honeycomb, etc.).
 *
 * Protocol: OTLP over HTTP (protobuf-JSON) — {@code POST /v1/traces}
 * No OTEL SDK dependency — pure Java HTTP client.
 *
 * Wire to SquadContext:
 * <pre>
 *   OtelExporterConfig cfg = OtelExporterConfig.builder()
 *       .endpoint("http://jaeger:4318")
 *       .serviceName("content-squad")
 *       .build();
 *   OtelSpanExporter exporter = new OtelSpanExporter(cfg);
 *   ctx.addTracer(exporter);        // called at boot time
 * </pre>
 *
 * The exporter batches spans and sends them asynchronously — agent execution
 * is never blocked by export latency.
 */
public class OtelSpanExporter implements TraceExporter {

    private final OtelExporterConfig    config;
    private final ExecutorService       executor;
    private final AtomicLong            exported  = new AtomicLong(0);
    private final AtomicLong            failed    = new AtomicLong(0);

    public OtelSpanExporter(OtelExporterConfig config) {
        this.config   = config;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "squados-otel-exporter");
            t.setDaemon(true);
            return t;
        });
    }

    // ── TraceExporter implementation ──────────────────────────────────

    @Override
    public void export(AgentSpan span) {
        if (!config.enabled()) return;
        executor.submit(() -> doExport(span));
    }

    @Override
    public String name() { return "OtelSpanExporter[" + config.traceEndpoint() + "]"; }

    @Override
    public void flush() { shutdown(); }

    // ── Stats ─────────────────────────────────────────────────────────

    public long exportedCount() { return exported.get(); }
    public long failedCount()   { return failed.get(); }

    public void shutdown() {
        executor.shutdown();
        try { executor.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── Export ────────────────────────────────────────────────────────

    private void doExport(AgentSpan span) {
        try {
            String body = toOtlpJson(span);
            sendHttp(body);
            exported.incrementAndGet();
        } catch (Exception e) {
            failed.incrementAndGet();
            System.err.printf("[OtelExporter] Failed to export span '%s': %s%n",
                span.getSpanName(), e.getMessage());
        }
    }

    private void sendHttp(String body) throws Exception {
        URL url = new URI(config.traceEndpoint()).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(config.timeoutMs());
        conn.setReadTimeout(config.timeoutMs());
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "SquadOS/3.9.0");
        config.headers().forEach(conn::setRequestProperty);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String resp = readResponse(conn.getErrorStream());
            throw new IOException("OTLP export returned HTTP " + code + ": " + resp);
        }
    }

    private String readResponse(InputStream is) {
        if (is == null) return "(no body)";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.substring(0, Math.min(200, sb.length()));
        } catch (IOException e) { return "(error reading body)"; }
    }

    // ── OTLP JSON serialisation ───────────────────────────────────────

    /**
     * Serialise to OTLP/JSON format:
     * https://opentelemetry.io/docs/specs/otel/protocol/otlp/
     *
     * Uses hand-built JSON (no Jackson/Gson dependency).
     */
    public String toOtlpJson(AgentSpan span) {
        long startNanos = toEpochNanos(span.getStartTime());
        long endNanos   = toEpochNanos(span.getEndTime());
        String status   = span.getStatus() == AgentSpan.Status.OK ? "STATUS_CODE_OK"
                                                                   : "STATUS_CODE_ERROR";

        return "{"
            + "\"resourceSpans\":[{"
            +   "\"resource\":{"
            +     "\"attributes\":[" + attr("service.name", config.serviceName()) + "]"
            +   "},"
            +   "\"scopeSpans\":[{"
            +     "\"scope\":{\"name\":\"io.squados\",\"version\":\"3.9.0\"},"
            +     "\"spans\":[{"
            +       "\"traceId\":\""  + padHex(span.getTraceId(), 32)  + "\","
            +       "\"spanId\":\""   + padHex(span.getSpanId(), 16)   + "\","
            +       "\"name\":\""     + esc(span.getSpanName())        + "\","
            +       "\"kind\":2,"   // SPAN_KIND_SERVER
            +       "\"startTimeUnixNano\":\"" + startNanos + "\","
            +       "\"endTimeUnixNano\":\""   + endNanos   + "\","
            +       "\"status\":{\"code\":\"" + status + "\"},"
            +       "\"attributes\":["
            +         attr("agent.name",  span.getAgentName()) + ","
            +         attr("agent.role",  span.getAgentRole() != null
                              ? span.getAgentRole().name() : "UNKNOWN") + ","
            +         attrInt("agent.prompt_tokens",      span.getPromptTokens()) + ","
            +         attrInt("agent.completion_tokens",  span.getCompletionTokens()) + ","
            +         attrInt("agent.total_tokens",       span.getTotalTokens()) + ","
            +         attrInt("agent.duration_ms",        (int) span.getDurationMs())
            +       "]"
            +     "}]"
            +   "}]"
            + "}]"
            + "}";
    }

    private static String attr(String key, String value) {
        return "{\"key\":\"" + esc(key) + "\",\"value\":{\"stringValue\":\"" + esc(value) + "\"}}";
    }

    private static String attrInt(String key, int value) {
        return "{\"key\":\"" + esc(key) + "\",\"value\":{\"intValue\":\"" + value + "\"}}";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String padHex(String hex, int len) {
        if (hex == null) hex = "0";
        hex = hex.replace("-", "");
        while (hex.length() < len) hex = "0" + hex;
        return hex.length() > len ? hex.substring(0, len) : hex;
    }

    private static long toEpochNanos(Instant instant) {
        if (instant == null) return 0L;
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
