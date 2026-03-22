package io.squados.annotation;
/** Strategy for selecting which agent to delegate to. */
public enum DelegateStrategy {
    /** LLM reads the task and picks the most appropriate role. */
    LLM_CHOICE,
    /** Cycle through candidates in order. */
    ROUND_ROBIN,
    /** Route to candidate with fewest active delegations. */
    LOAD_BALANCE,
    /** Route to first candidate whose condition matches. */
    FIRST_MATCH
}