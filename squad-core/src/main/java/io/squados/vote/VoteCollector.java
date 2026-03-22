package io.squados.vote;

import io.squados.annotation.TieBreaker;
import io.squados.annotation.VoteRule;
import io.squados.exception.VoteRejectedException;

import java.util.*;
import java.util.concurrent.*;

/**
 * Collects votes from multiple agents and resolves them using the
 * configured {@link VoteRule} and {@link TieBreaker}.
 *
 * Thread-safe — votes can be submitted from parallel agent threads.
 */
public class VoteCollector {

    private final String      topic;
    private final VoteRule    rule;
    private final TieBreaker  onTie;
    private final int         expectedVoters;
    private final long        timeoutMs;
    private final List<Vote>  votes = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch;

    public VoteCollector(String topic, VoteRule rule, TieBreaker onTie,
                         int expectedVoters, int timeoutSeconds) {
        this.topic          = topic;
        this.rule           = rule;
        this.onTie          = onTie;
        this.expectedVoters = expectedVoters;
        this.timeoutMs      = timeoutSeconds * 1000L;
        this.latch          = new CountDownLatch(expectedVoters);
    }

    /**
     * Submit a vote from one agent.
     * Thread-safe — can be called from parallel threads.
     */
    public void submit(Vote vote, String voterName) {
        votes.add(vote.withVoter(voterName));
        System.out.printf("[SquadVote] Vote received from %s: %s — %s%n",
            voterName, vote.getDecision(), vote.getReason());
        latch.countDown();
    }

    /**
     * Wait for all votes then resolve to a VoteResult.
     * Blocks the calling thread until all voters respond or timeout.
     */
    public VoteResult resolve() throws InterruptedException {
        boolean allVoted = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!allVoted) {
            System.out.printf("[SquadVote] Timeout — received %d/%d votes%n",
                votes.size(), expectedVoters);
            return new VoteResult(VoteResult.Outcome.TIMEOUT, votes, topic);
        }
        return tally(votes);
    }

    /**
     * Tally votes using the configured rule.
     */
    public VoteResult tally(List<Vote> allVotes) {
        int total   = allVotes.size();
        int approve = (int) allVotes.stream().filter(Vote::isApprove).count();
        int reject  = (int) allVotes.stream().filter(Vote::isReject).count();
        double approveW = allVotes.stream().filter(Vote::isApprove)
            .mapToDouble(Vote::getWeight).sum();
        double rejectW  = allVotes.stream().filter(Vote::isReject)
            .mapToDouble(Vote::getWeight).sum();

        VoteResult.Outcome outcome = switch (rule) {
            case MAJORITY -> {
                if (approve > total / 2.0)       yield VoteResult.Outcome.APPROVED;
                else if (reject > total / 2.0)   yield VoteResult.Outcome.REJECTED;
                else                              yield VoteResult.Outcome.TIE;
            }
            case UNANIMOUS -> {
                if (approve == total)             yield VoteResult.Outcome.APPROVED;
                else if (reject > 0)              yield VoteResult.Outcome.REJECTED;
                else                              yield VoteResult.Outcome.TIE;
            }
            case ANY -> {
                if (approve > 0)                  yield VoteResult.Outcome.APPROVED;
                else                              yield VoteResult.Outcome.REJECTED;
            }
            case SUPERMAJORITY -> {
                double threshold = 2.0 / 3.0;
                if (approve >= total * threshold) yield VoteResult.Outcome.APPROVED;
                else if (reject >= total * threshold) yield VoteResult.Outcome.REJECTED;
                else                              yield VoteResult.Outcome.TIE;
            }
            case WEIGHTED -> {
                if (approveW > rejectW)           yield VoteResult.Outcome.APPROVED;
                else if (rejectW > approveW)      yield VoteResult.Outcome.REJECTED;
                else                              yield VoteResult.Outcome.TIE;
            }
        };

        // Apply tie-breaker
        if (outcome == VoteResult.Outcome.TIE) {
            outcome = resolveTie();
        }

        VoteResult result = new VoteResult(outcome, allVotes, topic);
        System.out.print(result.getSummary());
        return result;
    }

    private VoteResult.Outcome resolveTie() {
        System.out.printf("[SquadVote] TIE on \"%s\" — applying TieBreaker.%s%n",
            topic, onTie);
        return switch (onTie) {
            case APPROVE  -> VoteResult.Outcome.APPROVED;
            case REJECT   -> VoteResult.Outcome.REJECTED;
            case ABSTAIN  -> VoteResult.Outcome.TIE;
            case ESCALATE -> VoteResult.Outcome.TIE; // caller handles escalation
        };
    }

    public List<Vote> getVotes()      { return Collections.unmodifiableList(votes); }
    public int        getVoteCount()  { return votes.size(); }
    public String     getTopic()      { return topic; }
    public VoteRule   getRule()       { return rule; }
    public TieBreaker getOnTie()      { return onTie; }
}