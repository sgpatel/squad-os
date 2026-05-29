package io.squados.tests;

import com.sun.net.httpserver.*;
import io.squados.annotation.*;
import io.squados.remote.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Phase 44 — @RemoteSquad tests.
 *
 * RS01  SquadClient constructor strips trailing slash from baseUrl
 * RS02  NoAuth.authHeader() returns null (no auth header)
 * RS03  ApiKeyAuth sends key via X-API-Key header (additionalHeaders)
 * RS04  JwtAuth.authHeader() returns "Bearer <token>"
 * RS05  RemoteSquadInvoker.inject() throws for non-SquadClient field type
 * RS06  RemoteSquadInvoker.inject() injects SquadClient into @RemoteSquad field
 * RS07  SquadClient.submit() makes HTTP POST to /submit and returns body
 * RS08  SquadClient.health() returns true when server responds with "UP"
 * RS09  SquadClient throws RemoteSquadException on HTTP 500 response
 * RS10  @RemoteSquad annotation readable with url, auth, timeoutMs attributes
 */
public class SquadOsPhase44Tests {

    // ── Class with @RemoteSquad field for injection test ──────────────

    static class OrchestratorService {
        @RemoteSquad(url = "http://analyst-squad:8080/api/analyst",
                     auth = "api-key", apiKey = "test-key-123",
                     timeoutMs = 5000)
        SquadClient analystSquad;

        String badField;  // not a SquadClient — should throw on inject
    }

    static class BadFieldService {
        @RemoteSquad(url = "http://any:8080")
        String notASquadClient;  // wrong type
    }

    // ── Runner ────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 44 — @RemoteSquad                             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "RS01_clientStripsTrailingSlash",
            "RS02_noAuthReturnsNull",
            "RS03_apiKeyAuthUsesAdditionalHeader",
            "RS04_jwtAuthReturnsBearerToken",
            "RS05_injectorThrowsForWrongFieldType",
            "RS06_injectorInjectsClientIntoField",
            "RS07_clientSubmitPostsToServer",
            "RS08_clientHealthReturnsTrueWhenUp",
            "RS09_clientThrowsOnHttpError",
            "RS10_remoteSquadAnnotationReadable",
        };
        var t = new SquadOsPhase44Tests();
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
        System.out.printf("Phase 44 result: %d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ── Tests ─────────────────────────────────────────────────────────

    void RS01_clientStripsTrailingSlash() {
        SquadClient c1 = new SquadClient("http://host:8080/api/",  new NoAuth(), 1000);
        SquadClient c2 = new SquadClient("http://host:8080/api",   new NoAuth(), 1000);

        assert "http://host:8080/api".equals(c1.getBaseUrl())
            : "Trailing slash should be stripped: " + c1.getBaseUrl();
        assert "http://host:8080/api".equals(c2.getBaseUrl())
            : "No trailing slash should be unchanged: " + c2.getBaseUrl();
    }

    void RS02_noAuthReturnsNull() {
        NoAuth auth = new NoAuth();
        assert auth.authHeader() == null          : "NoAuth should return null authHeader";
        assert auth.additionalHeaders().isEmpty() : "NoAuth should return empty additionalHeaders";
    }

    void RS03_apiKeyAuthUsesAdditionalHeader() {
        ApiKeyAuth auth = new ApiKeyAuth("my-secret-key-abc123");
        assert auth.authHeader() == null          : "ApiKeyAuth authHeader() should be null";
        var headers = auth.additionalHeaders();
        assert headers.containsKey("X-API-Key")  : "Should have X-API-Key header";
        assert "my-secret-key-abc123".equals(headers.get("X-API-Key"))
            : "API key should match";
    }

    void RS04_jwtAuthReturnsBearerToken() {
        JwtAuth auth = new JwtAuth("eyJhbGciOiJIUzI1NiJ9.test.sig");
        String header = auth.authHeader();
        assert header != null                      : "JwtAuth should return non-null header";
        assert header.startsWith("Bearer ")        : "Should start with 'Bearer '";
        assert header.contains("eyJhbGciOiJIUzI1NiJ9") : "Should contain the token";
    }

    void RS05_injectorThrowsForWrongFieldType() {
        BadFieldService svc = new BadFieldService();
        boolean threw = false;
        try {
            RemoteSquadInvoker.inject(svc);
        } catch (IllegalArgumentException e) {
            threw = true;
            assert e.getMessage().contains("SquadClient") : "Should mention SquadClient";
        }
        assert threw : "Should throw IllegalArgumentException for non-SquadClient field";
    }

    void RS06_injectorInjectsClientIntoField() throws Exception {
        OrchestratorService svc = new OrchestratorService();
        assert svc.analystSquad == null : "Field should be null before injection";

        RemoteSquadInvoker.inject(svc);

        assert svc.analystSquad != null : "Field should be injected";
        assert svc.analystSquad.getBaseUrl().contains("analyst-squad")
            : "Injected client should have correct URL: " + svc.analystSquad.getBaseUrl();
        System.out.printf("         → injected client baseUrl=%s%n",
            svc.analystSquad.getBaseUrl());
    }

    void RS07_clientSubmitPostsToServer() throws Exception {
        // Start a mini HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/submit", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = "Analysis of: " + body;
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });
        server.start();

        int port = server.getAddress().getPort();
        try {
            SquadClient client = new SquadClient(
                "http://localhost:" + port + "/api", new NoAuth(), 5000);
            String result = client.submit("quarterly earnings data");

            assert result.contains("quarterly earnings") : "Response should reflect task: " + result;
            System.out.printf("         → server responded: %s%n", result);
        } finally {
            server.stop(0);
        }
    }

    void RS08_clientHealthReturnsTrueWhenUp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();

        int port = server.getAddress().getPort();
        try {
            SquadClient client = new SquadClient(
                "http://localhost:" + port + "/api", new NoAuth(), 5000);
            boolean up = client.health();
            assert up : "health() should return true when server returns UP";
            System.out.printf("         → health=%b%n", up);
        } finally {
            server.stop(0);
        }
    }

    void RS09_clientThrowsOnHttpError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/submit", exchange -> {
            byte[] body = "Internal Server Error".getBytes();
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();

        int port = server.getAddress().getPort();
        try {
            SquadClient client = new SquadClient(
                "http://localhost:" + port + "/api", new NoAuth(), 5000);
            boolean threw = false;
            try {
                client.submit("trigger 500 error");
            } catch (RemoteSquadException e) {
                threw = true;
                assert e.getStatusCode() == 500 : "Status code should be 500";
                assert e.getRemoteUrl() != null  : "Remote URL should be set";
                System.out.printf("         → caught RemoteSquadException: code=%d%n",
                    e.getStatusCode());
            }
            assert threw : "Should throw RemoteSquadException on HTTP 500";
        } finally {
            server.stop(0);
        }
    }

    void RS10_remoteSquadAnnotationReadable() throws Exception {
        var field = OrchestratorService.class.getDeclaredField("analystSquad");
        RemoteSquad ann = field.getAnnotation(RemoteSquad.class);

        assert ann != null                                    : "@RemoteSquad should be present";
        assert ann.url().contains("analyst-squad")            : "URL should match";
        assert "api-key".equals(ann.auth())                  : "Auth should be api-key";
        assert "test-key-123".equals(ann.apiKey())           : "API key should match";
        assert ann.timeoutMs() == 5000                       : "Timeout should be 5000ms";
    }
}
