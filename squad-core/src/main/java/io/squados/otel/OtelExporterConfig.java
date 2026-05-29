package io.squados.otel;

import java.util.Map;

/**
 * Configuration for the OpenTelemetry OTLP HTTP span exporter.
 *
 * Mirrors standard OTEL environment variables:
 *   OTEL_EXPORTER_OTLP_ENDPOINT   → endpoint
 *   OTEL_SERVICE_NAME              → serviceName
 *   OTEL_EXPORTER_OTLP_HEADERS    → headers
 *
 * Usage:
 * <pre>
 *   OtelExporterConfig cfg = OtelExporterConfig.builder()
 *       .endpoint("http://localhost:4318")
 *       .serviceName("my-squad-app")
 *       .header("Authorization", "Bearer " + token)
 *       .build();
 *   OtelSpanExporter exporter = new OtelSpanExporter(cfg);
 *   ctx.addTracer(exporter);
 * </pre>
 */
public class OtelExporterConfig {

    private final String              endpoint;
    private final String              serviceName;
    private final Map<String, String> headers;
    private final int                 timeoutMs;
    private final boolean             enabled;

    private OtelExporterConfig(Builder b) {
        this.endpoint    = b.endpoint;
        this.serviceName = b.serviceName;
        this.headers     = Map.copyOf(b.headers);
        this.timeoutMs   = b.timeoutMs;
        this.enabled     = b.enabled;
    }

    public String              endpoint()    { return endpoint; }
    public String              serviceName() { return serviceName; }
    public Map<String, String> headers()     { return headers; }
    public int                 timeoutMs()   { return timeoutMs; }
    public boolean             enabled()     { return enabled; }

    /** OTLP trace endpoint: endpoint + /v1/traces */
    public String traceEndpoint() { return endpoint.replaceAll("/$", "") + "/v1/traces"; }

    public static Builder builder() { return new Builder(); }

    /** Load from standard OTEL environment variables. */
    public static OtelExporterConfig fromEnv() {
        String ep  = System.getenv().getOrDefault(
            "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318");
        String svc = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "squados");
        return builder().endpoint(ep).serviceName(svc).build();
    }

    public static final class Builder {
        private String              endpoint    = "http://localhost:4318";
        private String              serviceName = "squados";
        private Map<String, String> headers     = Map.of();
        private int                 timeoutMs   = 5000;
        private boolean             enabled     = true;
        private final java.util.HashMap<String, String> hdrs = new java.util.HashMap<>();

        public Builder endpoint(String e)    { this.endpoint    = e; return this; }
        public Builder serviceName(String s) { this.serviceName = s; return this; }
        public Builder timeoutMs(int t)      { this.timeoutMs   = t; return this; }
        public Builder enabled(boolean e)    { this.enabled     = e; return this; }
        public Builder header(String k, String v) { hdrs.put(k, v); return this; }
        public OtelExporterConfig build() {
            this.headers = Map.copyOf(hdrs);
            return new OtelExporterConfig(this);
        }
    }
}
