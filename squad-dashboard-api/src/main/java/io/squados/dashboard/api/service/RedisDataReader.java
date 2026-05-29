package io.squados.dashboard.api.service;

import io.squados.dashboard.api.model.TraceSpan;
import io.squados.dashboard.api.model.WorkflowItem;
import org.springframework.lang.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reads live SquadOS data from the shared Redis instance.
 *
 * Key layout written by SquadOS agents:
 *   squados:traces          LIST  — JSON AgentSpan (newest first, max 500)
 *   squados:traces:tokens   STRING — cumulative total tokens (INCRBY)
 *   squados:durable:{id}    STRING — pipe-delimited WorkflowState
 *
 * This reader is entirely read-only — it never writes to Redis.
 * The dashboard writes nothing; it only visualises what agents have stored.
 *
 * Falls back gracefully (returns empty / 0) if Redis is unavailable.
 */
public class RedisDataReader implements AutoCloseable {

    private static final Logger log = Logger.getLogger(RedisDataReader.class.getName());

    private static final String KEY_SPANS  = "squados:traces";
    private static final String KEY_TOKENS = "squados:traces:tokens";
    private static final String KEY_DURABLE_PREFIX = "squados:durable:";

    private final JedisPool pool;

    public RedisDataReader(String host, int port, @Nullable String password) {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(8);
        cfg.setMaxIdle(4);
        cfg.setMinIdle(1);
        cfg.setTestOnBorrow(true);
        cfg.setTestWhileIdle(true);

        if (password != null && !password.isBlank()) {
            this.pool = new JedisPool(cfg, host, port, 2000, password);
        } else {
            this.pool = new JedisPool(cfg, host, port, 2000);
        }
        log.info("[RedisDataReader] Connected to Redis " + host + ":" + port);
    }

    // ── Traces ────────────────────────────────────────────────────────────────

    /**
     * Fetches up to {@code limit} AgentSpan records from squados:traces.
     * Returns newest-first (as SquadOS pushes with LPUSH).
     */
    public List<TraceSpan> getTraces(int limit) {
        try (Jedis j = pool.getResource()) {
            List<String> raw = j.lrange(KEY_SPANS, 0, limit - 1);
            List<TraceSpan> result = new ArrayList<>(raw.size());
            for (String json : raw) {
                try {
                    result.add(parseSpan(json));
                } catch (Exception e) {
                    log.fine("[RedisDataReader] Could not parse span: " + e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.warning("[RedisDataReader] getTraces failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Total tokens written by all SquadOS nodes.
     */
    public long totalTokens() {
        try (Jedis j = pool.getResource()) {
            String v = j.get(KEY_TOKENS);
            return v != null ? Long.parseLong(v) : 0L;
        } catch (Exception e) {
            log.warning("[RedisDataReader] totalTokens failed: " + e.getMessage());
            return 0L;
        }
    }

    /**
     * All durable workflow states, scanned from squados:durable:*.
     */
    public List<WorkflowItem> getWorkflows() {
        try (Jedis j = pool.getResource()) {
            List<WorkflowItem> result = new ArrayList<>();
            ScanParams params = new ScanParams().match(KEY_DURABLE_PREFIX + "*").count(100);
            String cursor = ScanParams.SCAN_POINTER_START;

            do {
                ScanResult<String> scan = j.scan(cursor, params);
                cursor = scan.getCursor();
                for (String key : scan.getResult()) {
                    String raw = j.get(key);
                    if (raw != null) {
                        try {
                            result.add(parseWorkflow(key, raw));
                        } catch (Exception e) {
                            log.fine("[RedisDataReader] Could not parse workflow " + key + ": " + e.getMessage());
                        }
                    }
                }
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

            return result;
        } catch (Exception e) {
            log.warning("[RedisDataReader] getWorkflows failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Quick connectivity check — returns true if Redis responds to PING.
     */
    public boolean isAvailable() {
        try (Jedis j = pool.getResource()) {
            return "PONG".equals(j.ping());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    /**
     * Parses the JSON format written by RedisTraceExporter.toJson().
     *
     * Format (no arrays, simple flat object):
     * {"spanId":"...","traceId":"...","spanName":"...","agentName":"...",
     *  "agentRole":"...","durationMs":N,"status":"OK|ERROR",
     *  "promptTokens":N,"completionTokens":N,"totalTokens":N,
     *  "inputLength":N,"outputLength":N,"error":null|"msg","timestamp":"ISO"}
     */
    private TraceSpan parseSpan(String json) {
        String spanId           = str(json, "spanId");
        String traceId          = str(json, "traceId");
        String spanName         = str(json, "spanName");
        String agentName        = str(json, "agentName");
        String agentRole        = str(json, "agentRole");
        long   durationMs       = num(json, "durationMs");
        String status           = str(json, "status");
        int    promptTokens     = (int) num(json, "promptTokens");
        int    completionTokens = (int) num(json, "completionTokens");
        int    totalTokens      = (int) num(json, "totalTokens");
        int    inputLength      = (int) num(json, "inputLength");
        int    outputLength     = (int) num(json, "outputLength");
        String timestamp        = str(json, "timestamp");
        String errorMsg         = strNullable(json, "error");

        Instant start = timestamp != null && !timestamp.isBlank()
            ? Instant.parse(timestamp) : Instant.now();
        Instant end = start.plusMillis(durationMs);

        return new TraceSpan(
            traceId, spanId, spanName,
            agentName, agentRole.toLowerCase(),
            start, end, durationMs,
            status, errorMsg,
            promptTokens, completionTokens, totalTokens,
            inputLength, outputLength,
            Map.of("source", "redis", "squad.version", "3.7.0")
        );
    }

    /**
     * Parses the pipe-delimited format written by RedisDurableStore.serialize().
     *
     * Format: workflowId|role|status|initialInput|finalOutput|errorMessage|steps
     * Steps:  index,name,success,output;index,name,success,output;...
     * Escape: | → § , ; → ¶ , , → •
     */
    private WorkflowItem parseWorkflow(String redisKey, String raw) {
        String workflowId = redisKey.substring(KEY_DURABLE_PREFIX.length());
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 7) {
            return new WorkflowItem(workflowId, "unknown", "UNKNOWN",
                Instant.now(), Instant.now(), 0, 0, List.of(), null, null);
        }

        String agentName = parts[1]; // this is the role name from SquadOS
        String state     = parts[2]; // PENDING, RUNNING, PAUSED, COMPLETED, FAILED
        String error     = parts[5].isBlank() ? null : unescape(parts[5]);

        List<String> completedSteps = new ArrayList<>();
        String lastStep = null;
        if (parts.length > 6 && !parts[6].isBlank()) {
            for (String stepStr : parts[6].split(";")) {
                if (stepStr.isBlank()) continue;
                String[] sp = stepStr.split(",", 4);
                if (sp.length >= 2) {
                    String stepName = unescape(sp[1]);
                    completedSteps.add(stepName);
                    lastStep = stepName;
                }
            }
        }

        return new WorkflowItem(
            workflowId,
            agentName,
            state,
            Instant.now().minusSeconds(300), // Redis doesn't store createdAt
            Instant.now(),
            0L,
            completedSteps.size(),
            completedSteps,
            lastStep,
            error
        );
    }

    // ── Minimal JSON field extractors (no external library) ───────────────────

    private String str(String json, String key) {
        String result = strNullable(json, key);
        return result != null ? result : "";
    }

    private String strNullable(String json, String key) {
        String marker = "\"" + key + "\":";
        int s = json.indexOf(marker);
        if (s < 0) return null;
        s += marker.length();
        // skip whitespace
        while (s < json.length() && json.charAt(s) == ' ') s++;
        if (s >= json.length()) return null;
        if (json.charAt(s) == 'n') return null; // null literal
        if (json.charAt(s) == '"') {
            s++; // skip opening quote
            StringBuilder sb = new StringBuilder();
            while (s < json.length() && json.charAt(s) != '"') {
                if (json.charAt(s) == '\\' && s + 1 < json.length()) {
                    s++; // skip escape char
                    sb.append(json.charAt(s));
                } else {
                    sb.append(json.charAt(s));
                }
                s++;
            }
            return sb.toString();
        }
        return null;
    }

    private long num(String json, String key) {
        String marker = "\"" + key + "\":";
        int s = json.indexOf(marker);
        if (s < 0) return 0;
        s += marker.length();
        while (s < json.length() && json.charAt(s) == ' ') s++;
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        if (e == s) return 0;
        try { return Long.parseLong(json.substring(s, e)); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("§", "|").replace("¶", ";").replace("•", ",");
    }
}
