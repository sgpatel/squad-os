package io.squados.debate;

import io.squados.vote.VoteResult;

import java.util.List;

/**
 * The complete outcome of a multi-agent debate.
 */
public class DebateResult {

    private final String           topic;
    private final List<DebateRound> rounds;
    private final VoteResult        voteResult;
    private final String            consensus;    // final agreed position
    private final boolean           converged;    // true if early-stopped by similarity
    private final long              elapsedMs;

    public DebateResult(String topic, List<DebateRound> rounds,
                        VoteResult voteResult, String consensus,
                        boolean converged, long elapsedMs) {
        this.topic      = topic;
        this.rounds     = rounds;
        this.voteResult = voteResult;
        this.consensus  = consensus;
        this.converged  = converged;
        this.elapsedMs  = elapsedMs;
    }

    public String           topic()      { return topic; }
    public List<DebateRound> rounds()    { return rounds; }
    public VoteResult       voteResult() { return voteResult; }
    public String           consensus()  { return consensus; }
    public boolean          converged()  { return converged; }
    public long             elapsedMs()  { return elapsedMs; }
    public int              totalRounds(){ return rounds.size(); }

    public void print() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.printf ("║  Debate: %-52s  ║%n", topic.substring(0, Math.min(52, topic.length())));
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.printf ("  Rounds:   %d%s%n", totalRounds(), converged ? " (converged early)" : "");
        System.out.printf ("  Outcome:  %s%n", voteResult.getOutcome());
        System.out.printf ("  Elapsed:  %dms%n", elapsedMs);
        System.out.println();
        for (DebateRound r : rounds) System.out.print(r.summary());
        System.out.println();
        System.out.println("  Consensus: " + consensus);
        System.out.println();
    }

    @Override
    public String toString() {
        return String.format("DebateResult{topic='%s', rounds=%d, outcome=%s, converged=%b}",
            topic, totalRounds(), voteResult.getOutcome(), converged);
    }
}
