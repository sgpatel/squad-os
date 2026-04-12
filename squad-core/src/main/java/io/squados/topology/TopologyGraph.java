package io.squados.topology;

import io.squados.annotation.AgentEdge;
import io.squados.annotation.EdgeType;

import java.util.*;

/**
 * Directed adjacency-list graph of agent edges.
 *
 * Built from {@code @Topology.edges()} at boot time.
 * Supports topological sort, cycle detection, BFS and DFS traversal.
 */
public class TopologyGraph {

    private final Map<String, List<TopologyEdge>> adjacency = new LinkedHashMap<>();
    private final Set<String> nodes = new LinkedHashSet<>();

    // ── Construction ──────────────────────────────────────────────────

    public void addEdge(String from, String to, EdgeType type) {
        nodes.add(from);
        nodes.add(to);
        adjacency.computeIfAbsent(from, k -> new ArrayList<>())
                 .add(new TopologyEdge(from, to, type));
        // Ensure 'to' has an entry (even if it has no outgoing edges)
        adjacency.computeIfAbsent(to, k -> new ArrayList<>());
    }

    public static TopologyGraph from(AgentEdge[] edges) {
        TopologyGraph g = new TopologyGraph();
        for (AgentEdge e : edges) {
            g.addEdge(e.from(), e.to(), e.type());
        }
        return g;
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public Set<String> nodes() {
        return Collections.unmodifiableSet(nodes);
    }

    public List<TopologyEdge> getSuccessors(String node) {
        return adjacency.getOrDefault(node, List.of());
    }

    public List<TopologyEdge> allEdges() {
        List<TopologyEdge> all = new ArrayList<>();
        adjacency.values().forEach(all::addAll);
        return all;
    }

    // ── Cycle detection ───────────────────────────────────────────────

    /**
     * Returns true if the graph contains at least one directed cycle.
     */
    public boolean hasCycle() {
        Set<String> visited  = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String node : nodes) {
            if (!visited.contains(node) && dfsCycle(node, visited, recStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfsCycle(String node, Set<String> visited, Set<String> recStack) {
        visited.add(node);
        recStack.add(node);
        for (TopologyEdge edge : adjacency.getOrDefault(node, List.of())) {
            if (!visited.contains(edge.to())) {
                if (dfsCycle(edge.to(), visited, recStack)) return true;
            } else if (recStack.contains(edge.to())) {
                return true;
            }
        }
        recStack.remove(node);
        return false;
    }

    // ── Traversal ─────────────────────────────────────────────────────

    /**
     * BFS order starting from {@code start} node.
     */
    public List<String> bfsOrder(String start) {
        List<String> order = new ArrayList<>();
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            order.add(node);
            for (TopologyEdge e : adjacency.getOrDefault(node, List.of())) {
                if (!visited.contains(e.to())) {
                    visited.add(e.to());
                    queue.add(e.to());
                }
            }
        }
        return order;
    }

    /**
     * DFS order starting from {@code start} node (pre-order).
     */
    public List<String> dfsOrder(String start) {
        List<String> order = new ArrayList<>();
        dfsVisit(start, new HashSet<>(), order);
        return order;
    }

    private void dfsVisit(String node, Set<String> visited, List<String> order) {
        visited.add(node);
        order.add(node);
        for (TopologyEdge e : adjacency.getOrDefault(node, List.of())) {
            if (!visited.contains(e.to())) dfsVisit(e.to(), visited, order);
        }
    }

    /**
     * Topological sort (Kahn's algorithm).
     * Returns nodes in dependency-first order.
     * Throws {@link IllegalStateException} if the graph has a cycle.
     */
    public List<String> topologicalSort() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String n : nodes) inDegree.put(n, 0);
        for (List<TopologyEdge> edges : adjacency.values()) {
            for (TopologyEdge e : edges) {
                inDegree.merge(e.to(), 1, Integer::sum);
            }
        }
        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((n, d) -> { if (d == 0) queue.add(n); });

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (TopologyEdge e : adjacency.getOrDefault(node, List.of())) {
                int deg = inDegree.merge(e.to(), -1, Integer::sum);
                if (deg == 0) queue.add(e.to());
            }
        }
        if (sorted.size() != nodes.size()) {
            throw new IllegalStateException(
                "[SquadOS] Topology graph contains a cycle — cannot topologically sort. "
                + "Use TopologyLayout.MESH or RING for cyclic graphs.");
        }
        return sorted;
    }

    /**
     * Source nodes — nodes with no incoming edges.
     */
    public List<String> sources() {
        Set<String> hasIncoming = new HashSet<>();
        for (List<TopologyEdge> edges : adjacency.values()) {
            edges.forEach(e -> hasIncoming.add(e.to()));
        }
        List<String> sources = new ArrayList<>();
        for (String n : nodes) {
            if (!hasIncoming.contains(n)) sources.add(n);
        }
        return sources;
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return (int) adjacency.values().stream().mapToLong(List::size).sum(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TopologyGraph{\n");
        for (List<TopologyEdge> edges : adjacency.values()) {
            for (TopologyEdge e : edges) {
                sb.append("  ").append(e.from()).append(" -[").append(e.type())
                  .append("]-> ").append(e.to()).append("\n");
            }
        }
        return sb.append("}").toString();
    }
}
