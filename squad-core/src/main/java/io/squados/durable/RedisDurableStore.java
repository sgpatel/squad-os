package io.squados.durable;

import io.squados.annotation.AgentRole;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Redis-backed DurableStore — survives JVM restarts.
 *
 * Uses raw Redis RESP protocol (no external client library) to stay
 * zero-dependency in squad-core.
 *
 * Key scheme:
 *   squados:durable:{workflowId}  — serialized WorkflowState (Hash)
 */
public class RedisDurableStore implements DurableStore {

    private final String host;
    private final int    port;
    private final String password;
    private final int    ttlHours;

    public RedisDurableStore(String host, int port, String password, int ttlHours) {
        this.host     = host;
        this.port     = port;
        this.password = password;
        this.ttlHours = ttlHours;
    }

    @Override
    public void save(WorkflowState state) {
        String key = key(state.getWorkflowId());
        String serialized = serialize(state);
        redisCommand("SET", key, serialized);
        if (ttlHours > 0) {
            redisCommand("EXPIRE", key, String.valueOf(ttlHours * 3600L));
        }
    }

    @Override
    public Optional<WorkflowState> load(String workflowId) {
        String result = redisCommand("GET", key(workflowId));
        if (result == null || result.isEmpty() || result.equals("nil")) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(workflowId, result));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<WorkflowState> loadAll() {
        // Simplified: scan is not implemented here; return empty for now
        // Full implementation would use SCAN squados:durable:*
        return List.of();
    }

    @Override
    public void delete(String workflowId) {
        redisCommand("DEL", key(workflowId));
    }

    // ── Serialization ─────────────────────────────────────────────────

    private String serialize(WorkflowState s) {
        // Simple pipe-delimited format: id|role|status|input|output|error|steps_json
        StringBuilder steps = new StringBuilder();
        for (WorkflowStep step : s.getSteps()) {
            steps.append(step.stepIndex()).append(",")
                 .append(escape(step.stepName())).append(",")
                 .append(step.success()).append(",")
                 .append(escape(step.output())).append(";");
        }
        return String.join("|",
            s.getWorkflowId(),
            s.getRole().name(),
            s.getStatus().name(),
            escape(s.getInitialInput()),
            escape(s.getFinalOutput()),
            escape(s.getErrorMessage()),
            steps.toString()
        );
    }

    private WorkflowState deserialize(String workflowId, String raw) {
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 7) throw new IllegalArgumentException("Invalid durable state: " + raw);

        AgentRole role = AgentRole.valueOf(parts[1]);
        WorkflowState state = new WorkflowState(parts[0], role, unescape(parts[3]));
        WorkflowStatus status = WorkflowStatus.valueOf(parts[2]);

        // Replay steps
        if (!parts[6].isBlank()) {
            for (String stepStr : parts[6].split(";")) {
                if (stepStr.isBlank()) continue;
                String[] sp = stepStr.split(",", 4);
                if (sp.length == 4) {
                    boolean success = Boolean.parseBoolean(sp[2]);
                    WorkflowStep step = success
                        ? WorkflowStep.success(Integer.parseInt(sp[0]), unescape(sp[1]), unescape(sp[3]))
                        : WorkflowStep.failure(Integer.parseInt(sp[0]), unescape(sp[1]), unescape(sp[3]));
                    state.addStep(step);
                }
            }
        }

        // Set final status
        switch (status) {
            case COMPLETED -> state.complete(unescape(parts[4]));
            case FAILED    -> state.fail(unescape(parts[5]));
            case PAUSED    -> { state.start(); state.pause(); }
            case RUNNING   -> state.start();
            default        -> {}
        }
        return state;
    }

    private static String escape(String s)   { return s == null ? "" : s.replace("|", "§").replace(";", "¶").replace(",", "•"); }
    private static String unescape(String s) { return s == null ? null : s.replace("§", "|").replace("¶", ";").replace("•", ","); }
    private static String key(String id)     { return "squados:durable:" + id; }

    // ── Minimal RESP client ───────────────────────────────────────────

    private String redisCommand(String... args) {
        try (Socket sock = new Socket(host, port);
             OutputStream out = sock.getOutputStream();
             InputStream  in  = sock.getInputStream()) {

            StringBuilder sb = new StringBuilder();
            sb.append("*").append(args.length).append("\r\n");
            for (String arg : args) {
                byte[] b = arg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                sb.append("$").append(b.length).append("\r\n");
                sb.append(arg).append("\r\n");
            }
            out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) return null;
            if (line.startsWith("+") || line.startsWith(":")) return line.substring(1);
            if (line.startsWith("-")) throw new RuntimeException("Redis error: " + line);
            if (line.startsWith("$")) {
                int len = Integer.parseInt(line.substring(1));
                if (len < 0) return "nil";
                char[] buf = new char[len];
                reader.read(buf, 0, len);
                return new String(buf);
            }
            return line;
        } catch (IOException e) {
            throw new RuntimeException("Redis command failed: " + e.getMessage(), e);
        }
    }
}
