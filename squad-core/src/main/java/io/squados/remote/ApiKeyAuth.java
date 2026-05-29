package io.squados.remote;

import java.util.Map;

/**
 * API-key authentication for @RemoteSquad calls.
 * Sends the key as X-API-Key header.
 */
public class ApiKeyAuth implements AgentAuthProvider {

    private final String apiKey;

    public ApiKeyAuth(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String authHeader() { return null; }

    @Override
    public Map<String, String> additionalHeaders() {
        return Map.of("X-API-Key", apiKey);
    }
}
