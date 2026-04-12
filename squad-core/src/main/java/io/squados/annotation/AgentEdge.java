package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Declares a directed edge between two agents in a {@link Topology}.
 *
 * <pre>
 * {@literal @}Topology(layout = TopologyLayout.PIPELINE, edges = {
 *     {@literal @}AgentEdge(from = "IngestAgent",  to = "ClassifyAgent", type = EdgeType.DELEGATES),
 *     {@literal @}AgentEdge(from = "ClassifyAgent", to = "AuditAgent",   type = EdgeType.NOTIFIES)
 * })
 * </pre>
 *
 * {@code from} and {@code to} reference agent names as declared in {@code @Agent(name = ...)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})   // only used as member of @Topology.edges()
@Documented
public @interface AgentEdge {

    /** Source agent name (must match {@code @Agent(name = ...)}). */
    String from();

    /** Target agent name (must match {@code @Agent(name = ...)}). */
    String to();

    /** Relationship semantics of this edge. */
    EdgeType type() default EdgeType.INFORMS;
}
