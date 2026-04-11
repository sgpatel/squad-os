package io.squados.remote;

import io.squados.annotation.AgentRole;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * HTTP client for calling a remote @AgentAPI squad.
 *
 * Injected into @RemoteSquad fields by RemoteSquadInjector
 * in squad-spring-boot-starter.
 *
 * Usage:
 * <pre>
 *   @RemoteSquad(url = "http://analyst-squad:8080/api/analyst")
 *   private SquadClient analyst;
 *
 *   String result = analyst.submit("Analyse this data");
 * </pre>
 */
public class SquadClient {

    private final String              baseUrl;
    private final AgentAuthProvider   auth;
    private final int                 timeoutMs;
    private final HttpClient          http;

    public SquadClient(String baseUrl, AgentAuthProvider auth, int timeoutMs) {
        this.baseUrl   = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.auth      = auth;
        this.timeoutMs = timeoutMs;
        this.http      = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    /** Submit a task to the remote squad's lead agent. */
    public String submit(String task) {
        return post("/submit", task);
    }

    /** Submit a task to a specific role in the remote squad. */
    public String submitTo(AgentRole role, String task) {
        return post("/submit/" + role.name().toLowerCase(), task);
    }

    /** Stream tokens from the remote squad (SSE). Calls handler for each chunk. */
    public void stream(String task, Consumer<String> handler) {
        // Simplified SSE: falls back to blocking submit + single call
        String response = submit(task);
        handler.accept(response);
    }

    /** Get squad metadata from the /info endpoint. */
    public RemoteSquadInfo info() {
        String body = get("/info");
        // Simple JSON parsing without external deps
        String name    = extractJson(body, "squadName");
        String version = extractJson(body, "version");
        return new RemoteSquadInfo(name, version, java.util.List.of(), java.util.List.of());
    }

    /** Check health of the remote squad. Returns true if UP. */
    public boolean health() {
        try {
            String body = get("/health");
            return body.contains("UP");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────

    private String post(String path, String body) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(body));

            applyAuth(req);

            HttpResponse<String> resp = http.send(req.build(),
                HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 300) {
                throw new RemoteSquadException(baseUrl, resp.statusCode(),
                    "Remote squad returned " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        } catch (RemoteSquadException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteSquadException(baseUrl, "POST to " + baseUrl + path + " failed: " + e.getMessage(), e);
        }
    }

    private String get(String path) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET();

            applyAuth(req);

            HttpResponse<String> resp = http.send(req.build(),
                HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 300) {
                throw new RemoteSquadException(baseUrl, resp.statusCode(),
                    "Remote squad returned " + resp.statusCode());
            }
            return resp.body();
        } catch (RemoteSquadException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteSquadException(baseUrl, "GET " + baseUrl + path + " failed: " + e.getMessage(), e);
        }
    }

    private void applyAuth(HttpRequest.Builder req) {
        if (auth == null) return;
        String authHeader = auth.authHeader();
        if (authHeader != null) req.header("Authorization", authHeader);
        auth.additionalHeaders().forEach(req::header);
    }

    private String extractJson(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        int colon = json.indexOf(":", idx + search.length());
        if (colon < 0) return "";
        int start = json.indexOf("\"", colon + 1);
        if (start < 0) return "";
        int end = json.indexOf("\"", start + 1);
        if (end < 0) return "";
        return json.substring(start + 1, end);
    }

    public String getBaseUrl() { return baseUrl; }
}
