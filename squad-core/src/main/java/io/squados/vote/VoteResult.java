package io.squados.vote;

import java.util.List;

/**
 * The outcome of a {@literal @}SquadVote — contains all individual votes
 * and the final consensus decision.
 */
public class VoteResult {

    public enum Outcome { APPROVED, REJECTED, TIE, TIMEOUT }

    private final Outcome    outcome;
    private final List<Vote> votes;
    private final String     topic;
    private final int        approveCount;
    private final int        rejectCount;
    private final int        abstainCount;
    private final double     approveWeight;
    private final double     rejectWeight;
    private final String     summary;

    public VoteResult(Outcome outcome, List<Vote> votes, String topic) {
        this.outcome      = outcome;
        this.votes        = List.copyOf(votes);
        this.topic        = topic;
        this.approveCount = (int) votes.stream().filter(Vote::isApprove).count();
        this.rejectCount  = (int) votes.stream().filter(Vote::isReject).count();
        this.abstainCount = (int) votes.stream().filter(Vote::isAbstain).count();
        this.approveWeight = votes.stream().filter(Vote::isApprove)
            .mapToDouble(Vote::getWeight).sum();
        this.rejectWeight  = votes.stream().filter(Vote::isReject)
            .mapToDouble(Vote::getWeight).sum();
        this.summary = buildSummary();
    }

    public Outcome    getOutcome()      { return outcome; }
    public List<Vote> getVotes()        { return votes; }
    public String     getTopic()        { return topic; }
    public int        getApproveCount() { return approveCount; }
    public int        getRejectCount()  { return rejectCount; }
    public int        getAbstainCount() { return abstainCount; }
    public double     getApproveWeight(){ return approveWeight; }
    public double     getRejectWeight() { return rejectWeight; }
    public String     getSummary()      { return summary; }
    public int        getTotalVoters()  { return votes.size(); }

    public boolean isApproved() { return outcome == Outcome.APPROVED; }
    public boolean isRejected() { return outcome == Outcome.REJECTED; }
    public boolean isTie()      { return outcome == Outcome.TIE; }

    private String buildSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[SquadVote] %s: %s (%d approve, %d reject, %d abstain)%n",
            topic, outcome, approveCount, rejectCount, abstainCount));
        for (Vote v : votes) {
            sb.append(String.format("  [%s] %s — %s%n",
                v.getDecision(), v.getVoterName(), v.getReason()));
        }
        return sb.toString();
    }

    @Override
    public String toString() { return summary; }
}