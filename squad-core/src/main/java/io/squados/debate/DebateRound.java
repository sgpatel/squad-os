package io.squados.debate;

import java.util.List;

/**
 * All positions produced in one round of a debate.
 */
public record DebateRound(
        int                  roundNumber,
        List<DebatePosition> positions,
        boolean              converged
) {
    /** Summary of all agents' stances in this round. */
    public String summary() {
        StringBuilder sb = new StringBuilder("Round " + roundNumber + ":\n");
        for (DebatePosition p : positions) {
            sb.append("  [").append(p.agentName()).append("] ")
              .append(p.position(), 0, Math.min(120, p.position().length()))
              .append("\n");
        }
        return sb.toString();
    }
}
