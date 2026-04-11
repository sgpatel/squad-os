package io.squados.boot;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.squados.context.AgentWrapper;
import io.squados.context.SquadContext;
import io.squados.llm.StreamToken;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

/**
 * Dynamic Spring MVC controller that handles @AgentAPI HTTP endpoints.
 *
 * Endpoints per @AgentAPI squad:
 *   POST {path}/submit               — Submit to lead agent
 *   POST {path}/submit/{role}        — Submit to specific role
 *   POST {path}/submit/stream        — SSE streaming to lead
 *   POST {path}/submit/{role}/stream — SSE streaming to role
 *   GET  {path}/info                 — Squad metadata
 *   GET  {path}/health               — Health check
 *
 * Registered programmatically by AgentApiRegistrar.
 * Not mapped directly — this class provides handler methods invoked by AgentApiRegistrar.
 */
public class AgentApiController {

    private final SquadContext context;
    private final String       path;
    private final String       apiKey;  // empty = no auth

    public AgentApiController(SquadContext context, String path, String apiKey) {
        this.context = context;
        this.path    = path;
        this.apiKey  = apiKey;
    }

    /** Handle POST {path}/submit */
    public ResponseEntity<Map<String, Object>> handleSubmit(
            String task,
            String providedKey) {
        if (!checkAuth(providedKey)) return unauthorized();
        AgentResponse response = context.submit(task);
        return ResponseEntity.ok(toMap(response));
    }

    /** Handle POST {path}/submit/{role} */
    public ResponseEntity<Map<String, Object>> handleSubmitToRole(
            String roleName, String task, String providedKey) {
        if (!checkAuth(providedKey)) return unauthorized();
        try {
            AgentRole role = AgentRole.valueOf(roleName.toUpperCase());
            AgentResponse response = context.submitTo(role, task);
            return ResponseEntity.ok(toMap(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown role: " + roleName));
        }
    }

    /** Handle POST {path}/submit/stream — SSE streaming */
    public SseEmitter handleStream(String task, String providedKey) {
        if (!checkAuth(providedKey)) {
            SseEmitter emitter = new SseEmitter();
            try { emitter.send("Unauthorized"); } catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        Thread.startVirtualThread(() -> {
            try {
                context.submitStream(task, token -> {
                    try {
                        emitter.send(SseEmitter.event().data(token.text()));
                        if (token.isLast()) emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** Handle GET {path}/info */
    public ResponseEntity<Map<String, Object>> handleInfo() {
        List<String> agents = context.getRegistry().all().stream()
            .map(AgentWrapper::getName).toList();
        List<String> roles = context.getRegistry().all().stream()
            .map(w -> w.getRole().name()).distinct().toList();
        return ResponseEntity.ok(Map.of(
            "squadName", context.getConfig().getName(),
            "version",   "3.7.0",
            "agents",    agents,
            "roles",     roles,
            "path",      path
        ));
    }

    /** Handle GET {path}/health */
    public ResponseEntity<Map<String, Object>> handleHealth() {
        return ResponseEntity.ok(Map.of("status", "UP", "squad", context.getConfig().getName()));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private boolean checkAuth(String providedKey) {
        if (apiKey == null || apiKey.isBlank()) return true;  // no auth configured
        return apiKey.equals(providedKey);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
    }

    private Map<String, Object> toMap(AgentResponse r) {
        return Map.of(
            "content",    r.content() != null ? r.content() : "",
            "agentName",  r.agentName(),
            "role",       r.role().name(),
            "success",    r.isSuccess(),
            "tokens",     r.totalTokens(),
            "latencyMs",  r.latency().toMillis(),
            "error",      r.errorMessage() != null ? r.errorMessage() : ""
        );
    }

    public String getPath() { return path; }
}
