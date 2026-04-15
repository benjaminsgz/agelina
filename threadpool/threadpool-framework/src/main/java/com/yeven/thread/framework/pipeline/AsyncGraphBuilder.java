package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.decorator.StepDecorator;
import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.function.Function;

/**
 * Builder for DAG-style async execution graphs.
 *
 * <p>The builder preserves node insertion order to keep execution result maps deterministic.</p>
 *
 * @param <C> graph context type
 */
public class AsyncGraphBuilder<C> {

    private final AsyncStepFactory stepFactory;
    private final StepDecorator decorator;
    private final Map<String, AsyncGraphNodeDefinition<C>> definitions = Collections.synchronizedMap(new LinkedHashMap<>());

    public AsyncGraphBuilder(AsyncStepFactory stepFactory) {
        this(stepFactory, new StepDecorator() {
            @Override
            public <T> AsyncStep<T> decorate(String stepName, AsyncStep<T> step) {
                return step;
            }
        });
    }

    public AsyncGraphBuilder(AsyncStepFactory stepFactory, StepDecorator decorator) {
        this.stepFactory = Objects.requireNonNull(stepFactory, "stepFactory");
        this.decorator = Objects.requireNonNull(decorator, "decorator");
    }

    /**
     * Registers a root node whose input is the initial graph context.
     *
     * @param name node name
     * @param mode execution mode
     * @param handler business handler
     * @return same builder for chaining
     */
    public synchronized AsyncGraphBuilder<C> addRootStep(String name, ExecutionMode mode, Function<C, C> handler) {
        return addNode(new AsyncGraphNodeDefinition<>(
                name,
                mode,
                List.of(),
                GraphNodeInput::getInitialContext,
                handler
        ));
    }

    /**
     * Registers a single-parent node.
     *
     * @param name node name
     * @param dependency upstream node name
     * @param mode execution mode
     * @param handler business handler
     * @return same builder for chaining
     */
    public synchronized AsyncGraphBuilder<C> addStep(
            String name,
            String dependency,
            ExecutionMode mode,
            Function<C, C> handler
    ) {
        return addNode(new AsyncGraphNodeDefinition<>(
                name,
                mode,
                List.of(dependency),
                GraphNodeInput::getOnlyDependency,
                handler
        ));
    }

    /**
     * Registers a join node that merges ordered dependency outputs into one context before running
     * the business handler.
     *
     * @param name node name
     * @param dependencies upstream node names in merge order
     * @param mode execution mode
     * @param merger merge function that combines dependency outputs
     * @param handler business handler applied after merge
     * @return same builder for chaining
     */
    public synchronized AsyncGraphBuilder<C> addJoinStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            Function<List<C>, C> merger,
            Function<C, C> handler
    ) {
        return addNode(new AsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                input -> merger.apply(input.getDependencyResultsInOrder()),
                handler
        ));
    }

    /**
     * Registers a pure join node whose output is the merge result itself.
     *
     * @param name node name
     * @param dependencies upstream node names in merge order
     * @param mode execution mode
     * @param merger merge function that combines dependency outputs
     * @return same builder for chaining
     */
    public synchronized AsyncGraphBuilder<C> addJoinStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            Function<List<C>, C> merger
    ) {
        return addJoinStep(name, dependencies, mode, merger, Function.identity());
    }

    /**
     * Registers one custom node definition.
     *
     * @param definition node definition
     * @return same builder for chaining
     */
    public synchronized AsyncGraphBuilder<C> addNode(AsyncGraphNodeDefinition<C> definition) {
        Objects.requireNonNull(definition, "definition");
        AsyncGraphNodeDefinition<C> previous = definitions.putIfAbsent(definition.getName(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate node name: " + definition.getName());
        }
        return this;
    }

    /**
     * Instantiates one reusable subgraph template under the given namespace.
     *
     * @param namespace template namespace prefix
     * @param template subgraph template
     * @return instance handle for referencing namespaced nodes
     */
    public synchronized AsyncGraphTemplateInstance<C> addTemplate(String namespace, AsyncGraphTemplate<C> template) {
        return addTemplate(namespace, template, Collections.emptyMap());
    }

    /**
     * Instantiates one reusable subgraph template under the given namespace and resolves the
     * provided binding aliases to already-registered graph nodes.
     *
     * @param namespace template namespace prefix
     * @param template subgraph template
     * @param bindings alias to existing node name map
     * @return instance handle for referencing namespaced nodes
     */
    public synchronized AsyncGraphTemplateInstance<C> addTemplate(
            String namespace,
            AsyncGraphTemplate<C> template,
            Map<String, String> bindings
    ) {
        Objects.requireNonNull(template, "template");
        AsyncGraphTemplateContext<C> context = new AsyncGraphTemplateContext<>(
                this,
                normalizeNamespace(namespace),
                bindings == null ? Collections.emptyMap() : bindings
        );
        template.apply(context);
        return context.instance();
    }

    private String normalizeNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        String trimmed = namespace.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * Builds one immutable execution graph.
     *
     * @return async graph snapshot
     */
    public synchronized AsyncGraph<C> build() {
        Map<String, AsyncGraph.GraphNode<C>> nodes = new LinkedHashMap<>();
        synchronized (definitions) {
            for (AsyncGraphNodeDefinition<C> definition : definitions.values()) {
                AsyncStep<C> step = decorator.decorate(
                        definition.getName(),
                        stepFactory.create(new StepDefinition<>(
                                definition.getName(),
                                definition.getMode(),
                                definition.getHandler()
                        ))
                );
                nodes.put(
                        definition.getName(),
                        new AsyncGraph.GraphNode<>(
                                definition.getName(),
                                definition.getDependencies(),
                                definition.getInputResolver(),
                                step
                        )
                );
            }
        }
        return new AsyncGraph<>(nodes);
    }
}
