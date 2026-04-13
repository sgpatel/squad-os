package io.squados.otel;

import java.util.UUID;

/**
 * W3C TraceContext propagation support (traceparent / tracestate).
 *
 * Format: 00-{traceId(32hex)}-{spanId(16hex)}-{flags(2hex)}
 * Example: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
 *
 * SquadOS uses this when exporting to OTEL collectors that require
 * proper trace context propagation for distributed tracing.
 */
public record OtelTraceContext(
        String traceId,  // 32 hex chars (128-bit)
        String spanId,   // 16 hex chars (64-bit)
        String flags     // 2 hex chars — "01" = sampled
) {
    /** Generate a fresh root trace context. */
    public static OtelTraceContext root() {
        return new OtelTraceContext(
            uuid128(), uuid64(), "01");
    }

    /** Generate a child span context under this trace. */
    public OtelTraceContext child() {
        return new OtelTraceContext(traceId, uuid64(), flags);
    }

    /** W3C traceparent header value. */
    public String traceparent() {
        return "00-" + traceId + "-" + spanId + "-" + flags;
    }

    /** Parse a traceparent header value. Returns null if malformed. */
    public static OtelTraceContext parse(String traceparent) {
        if (traceparent == null) return null;
        String[] parts = traceparent.split("-");
        if (parts.length < 4) return null;
        return new OtelTraceContext(parts[1], parts[2], parts[3]);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String uuid128() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String uuid64() {
        return Long.toHexString(UUID.randomUUID().getMostSignificantBits())
            + Long.toHexString(UUID.randomUUID().getLeastSignificantBits() & 0xFFFFFFFFL);
    }
}
