package io.squados.remote;

import java.util.List;

/**
 * Metadata returned from a remote squad's GET /info endpoint.
 */
public record RemoteSquadInfo(
        String       squadName,
        String       version,
        List<String> agentNames,
        List<String> roles
) {
    @Override
    public String toString() {
        return "RemoteSquadInfo{name='" + squadName + "', version='" + version
               + "', agents=" + agentNames + ", roles=" + roles + "}";
    }
}
