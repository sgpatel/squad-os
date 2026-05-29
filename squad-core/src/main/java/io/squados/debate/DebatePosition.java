package io.squados.debate;

/**
 * One agent's position (answer) at a specific debate round.
 */
public record DebatePosition(
        String agentName,
        int    round,
        String position,      // the agent's current answer/stance
        String critiqueGiven  // critique of OTHER agents' positions (null in round 0)
) {
    public static DebatePosition initial(String agentName, String position) {
        return new DebatePosition(agentName, 0, position, null);
    }

    public static DebatePosition revised(String agentName, int round,
                                         String position, String critique) {
        return new DebatePosition(agentName, round, position, critique);
    }

    @Override
    public String toString() {
        return String.format("DebatePosition{agent='%s', round=%d, pos='%s...'}",
            agentName, round,
            position.substring(0, Math.min(60, position.length())));
    }
}
