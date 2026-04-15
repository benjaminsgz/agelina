package com.yeven.thread.framework.pipeline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Immutable asynchronous DAG executor.
 *
 * <p>Execution is driven by iterative topological sort. Each node is scheduled only after all
 * declared dependencies complete successfully. Independent branches can run in parallel.</p>
 *
 * <p><b>Warning:</b> The context object {@code C} is shared across all nodes. Since independent
 * branches can run in parallel, any state modification in the context object must be thread-safe
 * (e.g., using concurrent collections, atomic variables, or synchronization) to avoid visibility
 * issues or race conditions.</p>
 *
 * @param <C> graph context type
 */
public class AsyncGraph<C> {

    private final Map<String, GraphNode<C>> nodes;
    private final List<String> terminalNodes;
    /**
     * Order of execution that satisfies all dependency constraints.
     */
    private final List<String> topologicalOrder;

    /**
     * Constructs a graph and validates its structure.
     *
     * @param nodes node name to definition mapping
     * @throws IllegalArgumentException if cycle is detected or dependency is missing
     */
    public AsyncGraph(Map<String, GraphNode<C>> nodes) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        validateDependencies();
        List<String> order = new ArrayList<>();
        validateAcyclicAndBuildOrder(order);
        this.topologicalOrder = Collections.unmodifiableList(order);
        this.terminalNodes = List.copyOf(findTerminalNodes());
    }

    /**
     * Executes the graph and returns the only terminal node result.
     *
     * @param initialContext graph input context
     * @return terminal node future
     * @throws IllegalStateException when the graph has zero or multiple terminal nodes
     */
    public CompletableFuture<C> execute(C initialContext) {
        if (terminalNodes.isEmpty()) {
            throw new IllegalStateException("Graph does not contain a terminal node");
        }
        if (terminalNodes.size() > 1) {
            throw new IllegalStateException(
                    "Graph has multiple terminal nodes " + terminalNodes + ". "
                            + "Use executeAll(...) or add an explicit join node."
            );
        }
        String terminalNode = terminalNodes.get(0);
        return executeAll(initialContext).thenApply(results -> results.get(terminalNode));
    }

    /**
     * Executes all graph nodes and returns every node result.
     *
     * @param initialContext graph input context
     * @return future containing all node outputs keyed by node name
     */
    public CompletableFuture<Map<String, C>> executeAll(C initialContext) {
        Map<String, CompletableFuture<C>> futures = new LinkedHashMap<>();

        for (String nodeName : topologicalOrder) {
            GraphNode<C> node = getNode(nodeName);
            List<CompletableFuture<C>> dependencyFutures = new ArrayList<>();
            for (String depName : node.dependencies()) {
                dependencyFutures.add(futures.get(depName));
            }

            CompletableFuture<?>[] allDeps = dependencyFutures.toArray(CompletableFuture[]::new);
            CompletableFuture<C> nodeFuture = CompletableFuture.allOf(allDeps)
                    .thenCompose(unused -> {
                        try {
                            Map<String, C> dependencyResults = new LinkedHashMap<>();
                            for (int i = 0; i < node.dependencies().size(); i++) {
                                dependencyResults.put(node.dependencies().get(i), dependencyFutures.get(i).join());
                            }
                            GraphNodeInput<C> input = new GraphNodeInput<>(initialContext, dependencyResults);
                            C resolvedInput = node.inputResolver().apply(input);
                            return node.step().apply(resolvedInput);
                        } catch (Throwable t) {
                            return CompletableFuture.failedFuture(t);
                        }
                    });
            futures.put(nodeName, nodeFuture);
        }

        CompletableFuture<?>[] allNodes = futures.values().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(allNodes)
                .thenApply(ignored -> {
                    Map<String, C> results = new LinkedHashMap<>();
                    futures.forEach((name, f) -> results.put(name, f.join()));
                    return Collections.unmodifiableMap(results);
                });
    }

    /**
     * @return terminal node names
     */
    public List<String> getTerminalNodes() {
        return terminalNodes;
    }

    private GraphNode<C> getNode(String nodeName) {
        GraphNode<C> node = nodes.get(nodeName);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node: " + nodeName);
        }
        return node;
    }

    private void validateDependencies() {
        for (GraphNode<C> node : nodes.values()) {
            for (String dependency : node.dependencies()) {
                if (!nodes.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Node '" + node.name() + "' depends on missing node '" + dependency + "'"
                    );
                }
            }
        }
    }

    private void validateAcyclicAndBuildOrder(List<String> order) {
        Map<String, VisitState> states = new LinkedHashMap<>();
        Deque<String> path = new ArrayDeque<>();
        for (String nodeName : nodes.keySet()) {
            if (states.get(nodeName) == null) {
                dfs(nodeName, states, path, order);
            }
        }
    }

    private void dfs(String nodeName, Map<String, VisitState> states, Deque<String> path, List<String> order) {
        states.put(nodeName, VisitState.VISITING);
        path.addLast(nodeName);
        for (String dependency : getNode(nodeName).dependencies()) {
            VisitState state = states.get(dependency);
            if (state == VisitState.VISITING) {
                StringBuilder cycle = new StringBuilder();
                boolean inCycle = false;
                for (String entry : path) {
                    if (entry.equals(dependency)) {
                        inCycle = true;
                    }
                    if (inCycle) {
                        if (!cycle.isEmpty()) {
                            cycle.append(" -> ");
                        }
                        cycle.append(entry);
                    }
                }
                cycle.append(" -> ").append(dependency);
                throw new IllegalArgumentException("Cycle detected: " + cycle);
            }
            if (state == null) {
                dfs(dependency, states, path, order);
            }
        }
        path.removeLast();
        states.put(nodeName, VisitState.VISITED);
        order.add(nodeName);
    }

    private List<String> findTerminalNodes() {
        Set<String> nonTerminalNodes = new LinkedHashSet<>();
        for (GraphNode<C> node : nodes.values()) {
            nonTerminalNodes.addAll(node.dependencies());
        }

        List<String> terminals = new ArrayList<>();
        for (String nodeName : nodes.keySet()) {
            if (!nonTerminalNodes.contains(nodeName)) {
                terminals.add(nodeName);
            }
        }
        return terminals;
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    /**
     * Runtime node metadata used by the graph executor.
     */
    public static final class GraphNode<C> {

        private final String name;
        private final List<String> dependencies;
        private final Function<GraphNodeInput<C>, C> inputResolver;
        private final AsyncStep<C> step;

        public GraphNode(
                String name,
                List<String> dependencies,
                Function<GraphNodeInput<C>, C> inputResolver,
                AsyncStep<C> step
        ) {
            this.name = name;
            this.dependencies = List.copyOf(dependencies);
            this.inputResolver = inputResolver;
            this.step = step;
        }

        public String name() {
            return name;
        }

        public List<String> dependencies() {
            return dependencies;
        }

        public Function<GraphNodeInput<C>, C> inputResolver() {
            return inputResolver;
        }

        public AsyncStep<C> step() {
            return step;
        }
    }
}
