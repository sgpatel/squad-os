package io.squados.vote;

/**
 * A single agent vote on a decision.
 *
 * Usage:
 * <pre>
 * return Vote.approve("Risk score 0.2 — within limits");
 * return Vote.reject("Anomalous transaction pattern detected");
 * return Vote.abstain("Insufficient data to decide");
 * </pre>
 */
public class Vote {

    public enum Decision { APPROVE, REJECT, ABSTAIN }

    private final Decision decision;
    private final String   reason;
    private final String   voterName;
    private final double   weight;   // for WEIGHTED rule
    private final long     castedAt;

    private Vote(Decision decision, String reason, String voterName, double weight) {
        this.decision  = decision;
        this.reason    = reason;
        this.voterName = voterName;
        this.weight    = weight;
        this.castedAt  = System.currentTimeMillis();
    }

    // ── Factory methods ──────────────────────────────────────────
    public static Vote approve(String reason) {
        return new Vote(Decision.APPROVE, reason, "unknown", 1.0);
    }

    public static Vote reject(String reason) {
        return new Vote(Decision.REJECT, reason, "unknown", 1.0);
    }

    public static Vote abstain(String reason) {
        return new Vote(Decision.ABSTAIN, reason, "unknown", 0.0);
    }

    public static Vote approve(String reason, double weight) {
        return new Vote(Decision.APPROVE, reason, "unknown", weight);
    }

    public static Vote reject(String reason, double weight) {
        return new Vote(Decision.REJECT, reason, "unknown", weight);
    }

    // ── With voter name ──────────────────────────────────────────
    public Vote withVoter(String name) {
        return new Vote(decision, reason, name, weight);
    }

    // ── Accessors ────────────────────────────────────────────────
    public Decision getDecision()  { return decision; }
    public String   getReason()    { return reason; }
    public String   getVoterName() { return voterName; }
    public double   getWeight()    { return weight; }
    public long     getCastedAt()  { return castedAt; }

    public boolean isApprove() { return decision == Decision.APPROVE; }
    public boolean isReject()  { return decision == Decision.REJECT; }
    public boolean isAbstain() { return decision == Decision.ABSTAIN; }

    @Override
    public String toString() {
        return String.format("Vote{%s by %s: %s}", decision, voterName, reason);
    }
}