package io.squados.mcp.server;

/**
 * Configuration for the MCP server.
 *
 * Controls the port, base HTTP path, maximum request body size,
 * and the server identity fields returned in the MCP initialize response.
 *
 * Obtain a config via the builder:
 * <pre>
 *   McpServerConfig config = McpServerConfig.builder()
 *       .port(3000)
 *       .basePath("/mcp")
 *       .build();
 * </pre>
 */
public class McpServerConfig {

    private int    port            = 3000;
    private String basePath        = "/mcp";
    private int    maxRequestBytes = 65536;
    private String serverName      = "squad-mcp-server";
    private String serverVersion   = "3.9.0";

    private McpServerConfig() {}

    // ── Getters ───────────────────────────────────────────────────────

    public int    port()            { return port; }
    public String basePath()        { return basePath; }
    public int    maxRequestBytes() { return maxRequestBytes; }
    public String serverName()      { return serverName; }
    public String serverVersion()   { return serverVersion; }

    // ── Builder ───────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {

        private final McpServerConfig cfg = new McpServerConfig();

        /** Port to bind (use 0 for OS-assigned ephemeral port). */
        public Builder port(int port) {
            cfg.port = port;
            return this;
        }

        /** HTTP path for the MCP endpoint (default: /mcp). */
        public Builder basePath(String basePath) {
            cfg.basePath = basePath;
            return this;
        }

        /** Maximum request body size in bytes (default: 65536). */
        public Builder maxRequestBytes(int maxRequestBytes) {
            cfg.maxRequestBytes = maxRequestBytes;
            return this;
        }

        /** Server name returned in the MCP initialize response. */
        public Builder serverName(String serverName) {
            cfg.serverName = serverName;
            return this;
        }

        /** Server version returned in the MCP initialize response. */
        public Builder serverVersion(String serverVersion) {
            cfg.serverVersion = serverVersion;
            return this;
        }

        public McpServerConfig build() { return cfg; }
    }

    @Override
    public String toString() {
        return "McpServerConfig{port=" + port
                + ", basePath='" + basePath + '\''
                + ", maxRequestBytes=" + maxRequestBytes
                + ", serverName='" + serverName + '\''
                + ", serverVersion='" + serverVersion + '\''
                + '}';
    }
}
