package com.yeven.thread.framework.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Collections;

/**
 * Read-only view of the input available to one DAG node execution.
 *
 * <p>A node can read the initial graph context as well as the outputs produced by all declared
 * dependency nodes. Dependency results preserve the declaration order from the node definition.</p>
 *
 * @param <C> graph context type
 */
public final class GraphNodeInput<C> {

    private final C initialContext;
    private final Map<String, C> dependencyResults;

    public GraphNodeInput(C initialContext, Map<String, C> dependencyResults) {
        this.initialContext = initialContext;
        this.dependencyResults = Collections.unmodifiableMap(new LinkedHashMap<>(dependencyResults));
    }

    /**
     * @return the original graph input context
     */
    public C getInitialContext() {
        return initialContext;
    }

    /**
     * Returns one dependency result by node name.
     *
     * @param nodeName dependency node name
     * @return dependency result
     * @throws NoSuchElementException when the dependency is absent
     */
    public C getDependency(String nodeName) {
        C value = dependencyResults.get(nodeName);
        if (value == null && !dependencyResults.containsKey(nodeName)) {
            throw new NoSuchElementException("Dependency result not found: " + nodeName);
        }
        return value;
    }

    /**
     * Looks up a dependency result without throwing.
     *
     * @param nodeName dependency node name
     * @return optional dependency result
     */
    public Optional<C> findDependency(String nodeName) {
        return Optional.ofNullable(dependencyResults.get(nodeName));
    }

    /**
     * Returns the only dependency result.
     *
     * @return dependency result
     * @throws IllegalStateException when the node does not depend on exactly one upstream node
     */
    public C getOnlyDependency() {
        if (dependencyResults.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one dependency result but found " + dependencyResults.size()
            );
        }
        return dependencyResults.values().iterator().next();
    }

    /**
     * @return immutable dependency result map keyed by node name
     */
    public Map<String, C> getDependencyResults() {
        return dependencyResults;
    }

    /**
     * @return dependency results in declared dependency order
     */
    public List<C> getDependencyResultsInOrder() {
        return List.copyOf(dependencyResults.values());
    }
}
