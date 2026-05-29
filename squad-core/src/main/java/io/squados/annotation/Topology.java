package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Declares a formal agent graph topology on a squad orchestrator class.
 *
 * The annotated class acts as the entry point for {@code SquadContext.submitTopology()}.
 * Edges define the wiring between named agents; the layout governs how the
 * {@link io.squados.topology.TopologyEngine} validates and traverses the graph.
 *
 * <pre>
 * {@literal @}Agent(role = AgentRole.STRATEGIST, name = "Orchestrator")
 * {@literal @}Topology(
 *     name   = "FraudPipeline",
 *     layout = TopologyLayout.PIPELINE,
 *     edges  = {
 *         {@literal @}AgentEdge(from = "Ingestor",   to = "Classifier",  type = EdgeType.DELEGATES),
 *         {@literal @}AgentEdge(from = "Classifier", to = "Reviewer",    type = EdgeType.DELEGATES),
 *         {@literal @}AgentEdge(from = "Reviewer",   to = "AuditLogger", type = EdgeType.NOTIFIES)
 *     }
 * )
 * public class FraudOrchestrator { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Topology {

    /** Human-readable name for this topology (used in logs and the dashboard). */
    String name() default "";

    /** Graph layout — governs validation and traversal strategy. */
    TopologyLayout layout() default TopologyLayout.PIPELINE;

    /** All edges in the agent graph. */
    AgentEdge[] edges() default {};

    /**
     * Maximum rounds of traversal for cyclic layouts (MESH, RING).
     * Ignored for acyclic layouts.
     */
    int maxRounds() default 1;
}
