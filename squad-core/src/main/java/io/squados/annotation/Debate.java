package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Enables multi-agent debate: agents critique each other's positions and
 * revise iteratively until consensus is reached or rounds are exhausted.
 *
 * Debate protocol:
 *   Round 0 (Initial positions):
 *     Each participant agent produces its initial answer independently.
 *   Round 1..N (Critique + Revision):
 *     Each agent receives all other agents' positions and produces a critique,
 *     then revises its own position incorporating the critiques received.
 *   Convergence check:
 *     If all positions are sufficiently similar (cosine similarity ≥ convergenceThreshold),
 *     debate terminates early.
 *   Final resolution:
 *     VoteCollector tallies the final positions using the configured VoteRule.
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.EXECUTOR, name = "EthicsCommittee")
 * {@literal @}Debate(
 *     participants   = {"EthicsAgent", "LegalAgent", "SafetyAgent"},
 *     rounds         = 3,
 *     voteRule       = VoteRule.MAJORITY,
 *     convergenceThreshold = 0.90f
 * )
 * public class EthicsCommitteeAgent {}
 *
 * DebateResult result = debateEngine.run("Should we deploy this model?");
 * System.out.println("Consensus: " + result.consensus());
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Debate {

    /** Names of the agents that participate in the debate. */
    String[] participants();

    /** Maximum debate rounds (initial positions = round 0; not counted here). */
    int rounds() default 3;

    /** Vote rule for final resolution. */
    VoteRule voteRule() default VoteRule.MAJORITY;

    /** Tie-breaking strategy. */
    TieBreaker tieBreaker() default TieBreaker.APPROVE;

    /**
     * If all participants' positions agree at this similarity level (0.0–1.0),
     * stop early — consensus is reached.
     * 0 disables early stopping.
     */
    float convergenceThreshold() default 0.90f;

    /**
     * Seconds each agent has to produce a position or critique.
     * 0 = no timeout.
     */
    int timeoutSeconds() default 30;
}
