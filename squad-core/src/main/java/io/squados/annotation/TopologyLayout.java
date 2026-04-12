package io.squados.annotation;

/**
 * Graph layout that governs how {@link io.squados.topology.TopologyEngine}
 * validates and traverses the agent graph.
 *
 * <ul>
 *   <li>{@code PIPELINE} — Directed Acyclic Graph. Execution follows topological order.
 *                          Cycles are rejected at boot time.
 *   <li>{@code STAR}     — One hub agent fans out to N leaves. Cycles rejected.
 *   <li>{@code HIERARCHY}— Tree-shaped DAG. Cycles rejected.
 *   <li>{@code MESH}     — Arbitrary directed graph, cycles permitted.
 *                          Execution bounded by {@code Topology.maxRounds()}.
 *   <li>{@code RING}     — Each agent forwards output to the next in a cycle.
 *                          Bounded by {@code Topology.maxRounds()}.
 * </ul>
 */
public enum TopologyLayout {
    /** Linear DAG — output flows from source to sink. */
    PIPELINE,
    /** One hub fans out to N leaves in parallel, results aggregated. */
    STAR,
    /** Tree-shaped hierarchy — parent delegates to children. */
    HIERARCHY,
    /** Arbitrary directed graph, cycles allowed, bounded by maxRounds. */
    MESH,
    /** Circular chain, bounded by maxRounds. */
    RING
}
