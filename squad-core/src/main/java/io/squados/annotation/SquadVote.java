package io.squados.annotation;
import java.lang.annotation.*;

/**
 * Collects votes from multiple agents and resolves to a consensus decision.
 *
 * Each participating agent returns a {@link io.squados.vote.Vote} object.
 * The framework collects all votes and applies the configured VoteRule.
 *
 * Vote rules:
 *   MAJORITY    - more than half must agree (default)
 *   UNANIMOUS   - all agents must agree
 *   ANY         - at least one agree is enough
 *   WEIGHTED    - agents have different weights, highest weighted total wins
 *   SUPERMAJORITY - two-thirds must agree
 *
 * Tie-breaking:
 *   ESCALATE    - escalate to @AwaitApproval (default)
 *   REJECT      - reject on tie
 *   APPROVE     - approve on tie
 *   ABSTAIN     - return null (no decision)
 *
 * Usage:
 * <pre>
 * {@literal @}SquadVote(
 *     quorum   = VoteRule.UNANIMOUS,
 *     onTie    = TieBreaker.ESCALATE,
 *     topic    = "Loan approval"
 * )
 * public Vote evaluateLoan(LoanApplication app) {
 *     // Each agent returns Vote.approve("reason") or Vote.reject("reason")
 *     return Vote.approve("Risk within acceptable limits");
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface SquadVote {
    /** Voting rule to determine consensus. */
    VoteRule quorum() default VoteRule.MAJORITY;

    /** What to do when votes are tied. */
    TieBreaker onTie() default TieBreaker.ESCALATE;

    /** Human-readable topic shown in vote summary. */
    String topic() default "Squad decision";

    /** Timeout in seconds to wait for all votes. */
    int timeoutSeconds() default 60;
}