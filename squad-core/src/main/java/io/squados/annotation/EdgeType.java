package io.squados.annotation;

/**
 * Semantics of a directed edge between two agents in a {@link Topology}.
 *
 * <ul>
 *   <li>{@code DELEGATES} — the output of {@code from} becomes the input of {@code to}.
 *   <li>{@code INFORMS}   — the output of {@code from} is appended as context to the
 *                           task given to {@code to}; the original input is preserved.
 *   <li>{@code APPROVES}  — {@code to} receives the output of {@code from} as an
 *                           approval request; the execution halts until {@code to} replies.
 *   <li>{@code NOTIFIES}  — {@code to} is called with the output of {@code from} but
 *                           its result is not propagated downstream (fire-and-forget).
 *   <li>{@code COMPETES}  — {@code from} and {@code to} run in parallel on the same input;
 *                           the first response that completes wins.
 * </ul>
 */
public enum EdgeType {
    /** Output of 'from' becomes the input of 'to'. */
    DELEGATES,
    /** Output of 'from' is appended as context to 'to'; original input preserved. */
    INFORMS,
    /** 'to' acts as an approval gate on 'from's output. */
    APPROVES,
    /** 'to' is called with 'from's output but result is not propagated (fire-and-forget). */
    NOTIFIES,
    /** 'from' and 'to' run in parallel on same input; first-to-complete wins. */
    COMPETES
}
