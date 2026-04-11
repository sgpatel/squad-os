package io.squados.remote;

/**
 * No-op authentication — for public/internal remote squads.
 */
public class NoAuth implements AgentAuthProvider {

    @Override
    public String authHeader() { return null; }
}
