package io.squados.annotation;
/** Determines how votes are counted to reach a decision. */
public enum VoteRule {
    /** More than 50% must vote APPROVE. */
    MAJORITY,
    /** Every voter must vote APPROVE. */
    UNANIMOUS,
    /** At least one APPROVE is enough. */
    ANY,
    /** Two-thirds or more must vote APPROVE. */
    SUPERMAJORITY,
    /** Agents have weights; highest weighted total wins. */
    WEIGHTED
}