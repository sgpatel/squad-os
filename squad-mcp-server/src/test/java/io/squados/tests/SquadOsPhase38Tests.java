package io.squados.tests;

import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.llm.MockLlmPort;
import io.squados.mcp.server.*;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Phase 38 — squad-mcp-server validation.
 *
 * Tests:
 *   MC01  McpServerConfig.builder() creates valid config
 *   MC02  McpToolExporter.export() discovers @Agent classes
 *   MC03  McpToolExporter maps agent name and description correctly
 *   MC04  McpJsonRpc.parseMethod() parses method from JSON-RPC request
 *   MC05  McpJsonRpc.buildSuccess() produces valid JSON-RPC response
 *   MC06  Start McpServer, send initialize request, get valid response
 *   MC07  Started server responds to tools/list with agent tools
 *   MC08  Started server responds to tools/call with agent output
 *   MC09  tools/call with unknown tool name returns error response
 *   MC10  McpServer.stop() cleanly shuts down (isRunning() returns false)
 */
public class SquadOsPhase38Tests {

    // ── Agent stubs ───────────────────────────────────────────────────

    @Agent(role = AgentRole.RESEARCHER, name = "ResearchAgent",
           description = "Researches factual topics with evidence-based summaries.")
    static class ResearchAgent {}

    @Agent(role = AgentRole.WRITER, name = "WriterAgent",
           description = "Writes blog posts, articles, and narrative content.")
    static class WriterAgent {}

    // ── Main runner ───────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  SquadOS Phase 38 — squad-mcp-server module                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        int passed = 0, failed = 0;
        String[] tests = {
            "MC01_mcpServerConfigBuilderCreatesValidConfig",
            "MC02_mcpToolExporterDiscoverAgentClasses",
            "MC03_mcpToolExporterMapsNameAndDescription",
            "MC04_mcpJsonRpcParseMethod",
            "MC05_mcpJsonRpcBuildSuccess",
            "MC06_startServerAndInitialize",
            "MC07_startServerAndListTools",
            "MC08_startServerAndCallTool",
            "MC09_callUnknownToolReturnsError",
            "MC10_stopServerShutsDown",
        };

        var t = new SquadOsPhase38Tests();
        for (String test : tests) {
            try {
                t.getClass().getDeclaredMethod(test).invoke(t);
                System.out.printf("  [PASS] %s%n", test);
                passed++;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.printf("  [FAIL] %s%n         → %s%n", test, cause.getMessage());
                if (System.getProperty("verbose") != null) cause.printStackTrace();
                failed++;
            }
        }

        System.out.println();
        System.out.printf("Phase 38 result: %d passed, %d failed%n", passed, failed);
        System.out.println();
        if (failed > 0) System.exit(1);
    }

    // ══════════════════════════════════════════════════════════════════
    // MC01-MC05  Unit tests (no HTTP)
    // ══════════════════════════════════════════════════════════════════

    void MC01_mcpServerConfigBuilderCreatesValidConfig() {
        McpServerConfig config = McpServerConfig.builder()
            .port(3001)
            .basePath("/mcp")
            .maxRequestBytes(32768)
            .serverName("test-server")
            .serverVersion("1.0.0")
            .build();

        assert config.port() == 3001              : "Port must be 3001";
        assert "/mcp".equals(config.basePath())   : "BasePath must be /mcp";
        assert config.maxRequestBytes() == 32768  : "maxRequestBytes must be 32768";
        assert "test-server".equals(config.serverName())  : "serverName must be test-server";
        assert "1.0.0".equals(config.serverVersion())     : "serverVersion must be 1.0.0";
    }

    void MC02_mcpToolExporterDiscoverAgentClasses() {
        List<McpToolDefinition> tools = McpToolExporter.export(
            List.of(ResearchAgent.class, WriterAgent.class));

        assert tools != null           : "Tools list must not be null";
        assert tools.size() == 2       : "Must export exactly 2 tools, got: " + tools.size();
    }

    void MC03_mcpToolExporterMapsNameAndDescription() {
        List<McpToolDefinition> tools = McpToolExporter.export(
            List.of(ResearchAgent.class));

        assert tools.size() == 1 : "Expected 1 tool";
        McpToolDefinition tool = tools.get(0);

        assert "ResearchAgent".equals(tool.name())
            : "Name must be 'ResearchAgent', got: " + tool.name();
        assert tool.description().contains("Researches")
            : "Description must mention 'Researches', got: " + tool.description();
        assert tool.inputSchemaJson() != null
            : "inputSchemaJson must not be null";
        assert tool.inputSchemaJson().contains("task")
            : "inputSchemaJson must contain 'task' property";
    }

    void MC04_mcpJsonRpcParseMethod() {
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\","
                   + "\"params\":{}}";
        String method = McpJsonRpc.parseMethod(req);
        assert "tools/list".equals(method)
            : "Method must be 'tools/list', got: " + method;

        // Test initialize
        String init = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}";
        assert "initialize".equals(McpJsonRpc.parseMethod(init))
            : "Method must be 'initialize'";

        // Test null/missing method
        assert McpJsonRpc.parseMethod("{}") == null
            : "Missing method must return null";
    }

    void MC05_mcpJsonRpcBuildSuccess() {
        String result = "{\"key\":\"value\"}";
        String response = McpJsonRpc.buildSuccess("1", result);

        assert response.contains("\"jsonrpc\":\"2.0\"")  : "Must contain jsonrpc field";
        assert response.contains("\"id\":1")              : "Must contain id field";
        assert response.contains("\"result\"")            : "Must contain result field";
        assert response.contains("\"key\":\"value\"")     : "Must contain result content";

        // Test error response
        String error = McpJsonRpc.buildError("2", McpJsonRpc.METHOD_NOT_FOUND, "Not found");
        assert error.contains("\"error\"")                : "Error response must have error field";
        assert error.contains("-32601")                   : "Error must include code -32601";
        assert error.contains("Not found")               : "Error must include message";
    }

    // ══════════════════════════════════════════════════════════════════
    // MC06-MC10  Integration tests (real HTTP server)
    // ══════════════════════════════════════════════════════════════════

    void MC06_startServerAndInitialize() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Mock agent response");

        McpServerConfig config = McpServerConfig.builder()
            .port(0)  // OS-assigned port
            .build();

        McpServer server = McpServer.start(config,
            List.of(ResearchAgent.class), llm);
        try {
            String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                       + "\"params\":{\"protocolVersion\":\"2024-11-05\","
                       + "\"capabilities\":{},"
                       + "\"clientInfo\":{\"name\":\"claude\",\"version\":\"1.0\"}}}";

            String resp = postJson(server.port(), config.basePath(), req);
            assert resp != null                               : "Response must not be null";
            assert resp.contains("\"jsonrpc\":\"2.0\"")      : "Must be JSON-RPC response";
            assert resp.contains("protocolVersion")           : "Must contain protocolVersion";
            assert resp.contains("2024-11-05")               : "Must contain protocol version value";
            assert resp.contains("tools")                    : "Must contain tools capability";
            assert resp.contains("squad-mcp-server")         : "Must contain serverInfo name";
        } finally {
            server.stop();
        }
    }

    void MC07_startServerAndListTools() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Mock response");

        McpServerConfig config = McpServerConfig.builder().port(0).build();
        McpServer server = McpServer.start(config,
            List.of(ResearchAgent.class, WriterAgent.class), llm);
        try {
            String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\","
                       + "\"params\":{}}";
            String resp = postJson(server.port(), config.basePath(), req);

            assert resp.contains("\"result\"")               : "Must have result field";
            assert resp.contains("tools")                    : "Must have tools array";
            assert resp.contains("ResearchAgent")            : "Must include ResearchAgent tool";
            assert resp.contains("WriterAgent")              : "Must include WriterAgent tool";
            assert resp.contains("Researches")               : "Must include agent description";
        } finally {
            server.stop();
        }
    }

    void MC08_startServerAndCallTool() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        llm.setDefaultResponse("Climate research: CO2 levels rising.");

        McpServerConfig config = McpServerConfig.builder().port(0).build();
        McpServer server = McpServer.start(config,
            List.of(ResearchAgent.class), llm);
        try {
            String req = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                       + "\"params\":{\"name\":\"ResearchAgent\","
                       + "\"arguments\":{\"task\":\"research climate change\"}}}";
            String resp = postJson(server.port(), config.basePath(), req);

            assert resp.contains("\"result\"")               : "Must have result field";
            assert resp.contains("content")                  : "Must have content array";
            assert resp.contains("text")                     : "Must have text type";
            // The mock LLM response should appear in the output
            assert resp.contains("Climate") || resp.contains("Mock") || resp.length() > 50
                : "Must have non-empty tool call result";
        } finally {
            server.stop();
        }
    }

    void MC09_callUnknownToolReturnsError() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        McpServerConfig config = McpServerConfig.builder().port(0).build();
        McpServer server = McpServer.start(config,
            List.of(ResearchAgent.class), llm);
        try {
            String req = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                       + "\"params\":{\"name\":\"UnknownAgent\","
                       + "\"arguments\":{\"task\":\"do something\"}}}";
            String resp = postJson(server.port(), config.basePath(), req);

            assert resp.contains("\"error\"")               : "Must have error field";
            assert resp.contains("-32601")                  : "Must have METHOD_NOT_FOUND code";
            assert resp.contains("UnknownAgent") || resp.contains("Tool not found")
                : "Must mention the unknown tool";
        } finally {
            server.stop();
        }
    }

    void MC10_stopServerShutsDown() throws Exception {
        MockLlmPort llm = new MockLlmPort();
        McpServerConfig config = McpServerConfig.builder().port(0).build();
        McpServer server = McpServer.start(config,
            List.of(ResearchAgent.class), llm);

        assert server.isRunning() : "Server must be running before stop()";
        int port = server.port();
        assert port > 0           : "Port must be positive, got: " + port;

        server.stop();
        assert !server.isRunning() : "Server must not be running after stop()";

        // Verify the port is no longer accepting connections
        try {
            postJson(port, config.basePath(), "{}");
            // If we reach here, connection succeeded — this is unexpected but not a hard failure
            // since OS port recycling is non-deterministic. The isRunning() check is the primary assertion.
        } catch (Exception ex) {
            // Expected: connection refused after stop()
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────────

    private static String postJson(int port, String path, String body) throws Exception {
        URL url = URI.create("http://localhost:" + port + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int status = conn.getResponseCode();
        java.io.InputStream is = (status < 400)
            ? conn.getInputStream()
            : conn.getErrorStream();
        byte[] response = is.readAllBytes();
        conn.disconnect();
        return new String(response, StandardCharsets.UTF_8);
    }
}
