package io.squados.trace;

import io.squados.annotation.AgentRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RedisTraceExporter — shared trace store for multi-JVM SquadOS deployments.
 *
 * All SquadOS nodes (fraud-detection, dashboard, any service) that share
 * the same Redis instance will see each others spans in real-time.
 *
 * Key layout:
 *   squados:traces        Redis LIST  — JSON AgentSpan records (newest first)
 *   squados:traces:tokens Redis STRING — cumulative total token count
 *
 * Usage:
 *   RedisTraceExporter exp = new RedisTraceExporter("localhost", 6379);
 *   SquadTracer.configure(exp);
 *
 * No external JSON library — uses a minimal built-in serialiser.
 * Requires redis.clients:jedis on the classpath.
 */
public class RedisTraceExporter implements TraceExporter {

    private static final String KEY_SPANS  = "squados:traces";
    private static final String KEY_TOKENS = "squados:traces:tokens";
    private static final int    MAX_SPANS  = 500;

    private final String host;
    private final int    port;
    private final String password;
    private final Object pool;

    public RedisTraceExporter(String host, int port) {
        this(host, port, null);
    }

    public RedisTraceExporter(String host, int port, String password) {
        this.host     = host;
        this.port     = port;
        this.password = password;
        this.pool     = buildPool();
    }

    private Object buildPool() {
        try {
            Class<?> pc = Class.forName("redis.clients.jedis.JedisPool");
            if (password != null && !password.isBlank()) {
                return pc.getConstructor(String.class, int.class, int.class, int.class, String.class)
                         .newInstance(host, port, 2000, 2000, password);
            }
            return pc.getConstructor(String.class, int.class).newInstance(host, port);
        } catch (Exception e) {
            throw new IllegalStateException("[RedisTraceExporter] Jedis not found. " +
                "Add redis.clients:jedis to pom.xml", e);
        }
    }

    private interface JedisAction<T> { T run(Object jedis) throws Exception; }

    private <T> T exec(JedisAction<T> action) {
        try {
            Object jedis = pool.getClass().getMethod("getResource").invoke(pool);
            try   { return action.run(jedis); }
            finally { jedis.getClass().getMethod("close").invoke(jedis); }
        } catch (Exception e) {
            throw new RuntimeException("Redis error", e);
        }
    }

    @Override
    public void export(AgentSpan span) {
        if (span == null) return;
        String json = toJson(span);
        exec(j -> {
            j.getClass().getMethod("lpush", String.class, String[].class)
             .invoke(j, KEY_SPANS, new String[]{json});
            j.getClass().getMethod("ltrim", String.class, long.class, long.class)
             .invoke(j, KEY_SPANS, 0L, (long)(MAX_SPANS - 1));
            long tok = span.getTotalTokens();
            if (tok > 0) {
                j.getClass().getMethod("incrBy", String.class, long.class)
                 .invoke(j, KEY_TOKENS, tok);
            }
            return null;
        });
    }

    @Override
    public String name() { return "redis"; }

    // ── Extended query methods (used by DashboardState + DashboardController) ──

    public List<AgentSpan> getSpans() {
        return exec(j -> {
            @SuppressWarnings("unchecked")
            List<String> raw = (List<String>) j.getClass()
                .getMethod("lrange", String.class, long.class, long.class)
                .invoke(j, KEY_SPANS, 0L, (long)(MAX_SPANS - 1));
            List<AgentSpan> result = new ArrayList<>();
            for (String s : raw) {
                try { result.add(fromJson(s)); } catch (Exception ignored) {}
            }
            return result;
        });
    }

    public long totalTokens() {
        return exec(j -> {
            String v = (String) j.getClass().getMethod("get", String.class)
                .invoke(j, KEY_TOKENS);
            return v != null ? Long.parseLong(v) : 0L;
        });
    }

    public int size() { return getSpans().size(); }

    public double avgDurationMs() {
        List<AgentSpan> spans = getSpans();
        return spans.isEmpty() ? 0.0 :
            spans.stream().mapToLong(AgentSpan::getDurationMs).average().orElse(0.0);
    }

    public List<AgentSpan> getErrors() {
        List<AgentSpan> errors = new ArrayList<>();
        for (AgentSpan s : getSpans())
            if (s.getStatus() == AgentSpan.Status.ERROR) errors.add(s);
        return errors;
    }

    public void clear() {
        exec(j -> {
            j.getClass().getMethod("del", String[].class)
             .invoke(j, (Object) new String[]{KEY_SPANS, KEY_TOKENS});
            return null;
        });
    }

    // ── Minimal no-dependency JSON ─────────────────────────────────

    private String toJson(AgentSpan s) {
        return "{"
            + "\"spanId\":\"" + s.getSpanId() + "\","
            + "\"traceId\":\"" + s.getTraceId() + "\","
            + "\"spanName\":\"" + esc(s.getSpanName()) + "\","
            + "\"agentName\":\"" + esc(s.getAgentName()) + "\","
            + "\"agentRole\":\"" + s.getAgentRole().name() + "\","
            + "\"durationMs\":" + s.getDurationMs() + ","
            + "\"status\":\"" + s.getStatus().name() + "\","
            + "\"promptTokens\":" + s.getPromptTokens() + ","
            + "\"completionTokens\":" + s.getCompletionTokens() + ","
            + "\"totalTokens\":" + s.getTotalTokens() + ","
            + "\"inputLength\":" + s.getInputLength() + ","
            + "\"outputLength\":" + s.getOutputLength() + ","
            + "\"error\":" + (s.getErrorMessage() != null
                ? "\"" + esc(s.getErrorMessage()) + "\""
                : "null") + ","
            + "\"timestamp\":\"" + s.getStartTime().toString() + "\""
            + "}";
    }

    private AgentSpan fromJson(String json) {
        return AgentSpan.builder(extract(json, "spanName"))
            .agentRole(AgentRole.valueOf(extract(json, "agentRole")))
            .agentName(extract(json, "agentName"))
            .durationMs(Long.parseLong(extractNum(json, "durationMs")))
            .status(AgentSpan.Status.valueOf(extract(json, "status")))
            .promptTokens(Integer.parseInt(extractNum(json, "promptTokens")))
            .completionTokens(Integer.parseInt(extractNum(json, "completionTokens")))
            .inputLength(Integer.parseInt(extractNum(json, "inputLength")))
            .outputLength(Integer.parseInt(extractNum(json, "outputLength")))
            .build();
    }

    private String extract(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int s = json.indexOf(marker);
        if (s < 0) return "";
        s += marker.length();
        int e = json.indexOf("\"", s);
        return e < 0 ? "" : json.substring(s, e);
    }

    private String extractNum(String json, String key) {
        String marker = "\"" + key + "\":";
        int s = json.indexOf(marker);
        if (s < 0) return "0";
        s += marker.length();
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        String v = json.substring(s, e).trim();
        return v.isEmpty() ? "0" : v;
    }

    private String esc(String s) {
        return s == null ? "" :
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}