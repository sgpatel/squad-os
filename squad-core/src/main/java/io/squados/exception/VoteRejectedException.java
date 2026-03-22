package io.squados.exception;
import io.squados.vote.VoteResult;

/** Thrown when a @SquadVote resolves to REJECTED. */
public class VoteRejectedException extends RuntimeException {
    private final VoteResult result;
    public VoteRejectedException(VoteResult result) {
        super("Vote REJECTED on \"" + result.getTopic() + "\" — " +
              result.getApproveCount() + " approve, " +
              result.getRejectCount()  + " reject");
        this.result = result;
    }
    public VoteResult getResult() { return result; }
}