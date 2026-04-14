package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Declarative definition for one DAG node.
 *
 * <p>The graph runtime first resolves the node input from the initial graph context and dependency
 * outputs, then dispatches the node handler according to {@link ExecutionMode}.</p>
 *
 * @param <C> graph context type
 */
public final class AsyncGraphNodeDefinition<C> {

    private final String name;
    private final ExecutionMode mode;
    private final List<String> dependencies;
    private final Function<GraphNodeInput<C>, C> inputResolver;
    private final Function<C, C> handler;

    public AsyncGraphNodeDefinition(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            Function<GraphNodeInput<C>, C> inputResolver,
            Function<C, C> handler
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        this.inputResolver = Objects.requireNonNull(inputResolver, "inputResolver");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public String getName() {
        return name;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public Function<GraphNodeInput<C>, C> getInputResolver() {
        return inputResolver;
    }

    public Function<C, C> getHandler() {
        return handler;
    }
}
