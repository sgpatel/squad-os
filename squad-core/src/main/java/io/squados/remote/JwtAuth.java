package io.squados.remote;

/**
 * Bearer JWT authentication for @RemoteSquad calls.
 */
public class JwtAuth implements AgentAuthProvider {

    private final String token;

    public JwtAuth(String token) {
        this.token = token;
    }

    @Override
    public String authHeader() {
        return "Bearer " + token;
    }
}
