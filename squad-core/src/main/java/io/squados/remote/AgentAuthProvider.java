package io.squados.remote;

/**
 * SPI for injecting authentication headers into remote squad HTTP requests.
 */
public interface AgentAuthProvider {

    /** Return the HTTP Authorization header value, or null for no auth. */
    String authHeader();

    /** Return any additional headers (e.g. X-API-Key). Default: none. */
    default java.util.Map<String, String> additionalHeaders() {
        return java.util.Map.of();
    }
}
