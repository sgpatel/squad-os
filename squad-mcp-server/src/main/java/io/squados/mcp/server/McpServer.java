package io.squados.mcp.server;

import com.sun.net.httpserver.HttpServer;
import io.squados.llm.LlmPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Lightweight MCP server backed by the JDK's built-in {@code com.sun.net.httpserver.HttpServer}.
 *
 * Starts an HTTP server that exposes SquadOS {@code @Agent}-annotated classes
 * as MCP (Model Context Protocol) tools via JSON-RPC 2.0.
 *
 * Usage:
 * <pre>
 *   McpServerConfig config = McpServerConfig.builder().port(3000).build();
 *   McpServer server = McpServer.start(config, List.of(ResearchAgent.class), llm);
 *   // ... handle requests ...
 *   server.stop();
 * </pre>
 */
public final class McpServer {

    private final HttpServer      httpServer;
    private final McpServerConfig config;
    private volatile boolean      running;

    private McpServer(HttpServer httpServer, McpServerConfig config) {
        this.httpServer = httpServer;
        this.config     = config;
        this.running    = true;
    }

    /**
     * Start the MCP server.
     *
     * @param config       server configuration (port, basePath, etc.)
     * @param agentClasses list of @Agent-annotated classes to expose as tools
     * @param llm          LLM port used when executing agent tools
     * @return a running McpServer instance
     * @throws IOException if the server cannot bind to the specified port
     */
    public static McpServer start(McpServerConfig config,
                                   List<Class<?>> agentClasses,
                                   LlmPort llm) throws IOException {
        HttpServer httpServer = HttpServer.create(
            new InetSocketAddress(config.port()), 0);

        McpRequestHandler handler = new McpRequestHandler(config, agentClasses, llm);
        httpServer.createContext(config.basePath(), handler);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        httpServer.start();

        System.out.printf("[SquadOS MCP] Server started on port %d at %s%n",
            httpServer.getAddress().getPort(), config.basePath());

        return new McpServer(httpServer, config);
    }

    /**
     * Stop the server.
     * Uses a 0-second delay to stop immediately (pending requests are dropped).
     */
    public void stop() {
        if (running) {
            httpServer.stop(0);
            running = false;
            System.out.printf("[SquadOS MCP] Server stopped (was on port %d)%n", port());
        }
    }

    /**
     * Return the actual port the server is bound to.
     * Useful when the configured port was 0 (OS-assigned ephemeral port).
     */
    public int port() {
        return httpServer.getAddress().getPort();
    }

    /**
     * Return true if the server is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    @Override
    public String toString() {
        return "McpServer{port=" + port()
                + ", basePath='" + config.basePath() + '\''
                + ", running=" + running + '}';
    }
}
