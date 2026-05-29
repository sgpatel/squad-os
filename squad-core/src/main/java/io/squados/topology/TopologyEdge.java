package io.squados.topology;

import io.squados.annotation.EdgeType;

/**
 * An immutable directed edge in an agent topology graph.
 */
public record TopologyEdge(String from, String to, EdgeType type) {

    @Override
    public String toString() {
        return from + " -[" + type + "]-> " + to;
    }
}
