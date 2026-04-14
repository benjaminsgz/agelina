package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Scoped writer API used by {@link AsyncGraphTemplate} instances.
 *
 * <p>The context automatically prefixes local node names with the template namespace and resolves
 * dependency aliases through the provided binding map.</p>
 *
 * @param <C> graph context type
 */
public final class AsyncGraphTemplateContext<C> {

    private final AsyncGraphBuilder<C> builder;
    private final AsyncGraphTemplateInstance<C> instance;

    AsyncGraphTemplateContext(
            AsyncGraphBuilder<C> builder,
            String namespace,
            Map<String, String> bindings
    ) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.instance = new AsyncGraphTemplateInstance<>(namespace, bindings);
    }

    /**
     * Registers a template-local root node.
     *
     * @param localName local node name
     * @param mode execution mode
     * @param handler business handler
     * @return same context for chaining
     */
    public AsyncGraphTemplateContext<C> addRootStep(String localName, ExecutionMode mode, Function<C, C> handler) {
        builder.addRootStep(instance.ref(localName), mode, handler);
        return this;
    }

    /**
     * Registers a template-local single-parent node.
     *
     * @param localName local node name
     * @param dependencyRef local dependency alias or external binding key
     * @param mode execution mode
     * @param handler business handler
     * @return same context for chaining
     */
    public AsyncGraphTemplateContext<C> addStep(
            String localName,
            String dependencyRef,
            ExecutionMode mode,
            Function<C, C> handler
    ) {
        builder.addStep(instance.ref(localName), instance.ref(dependencyRef), mode, handler);
        return this;
    }

    /**
     * Registers a join node inside the template.
     *
     * @param localName local node name
     * @param dependencyRefs local dependency aliases or external binding keys
     * @param mode execution mode
     * @param merger merge function
     * @param handler business handler applied after merge
     * @return same context for chaining
     */
    public AsyncGraphTemplateContext<C> addJoinStep(
            String localName,
            List<String> dependencyRefs,
            ExecutionMode mode,
            Function<List<C>, C> merger,
            Function<C, C> handler
    ) {
        builder.addJoinStep(
                instance.ref(localName),
                dependencyRefs.stream().map(instance::ref).toList(),
                mode,
                merger,
                handler
        );
        return this;
    }

    /**
     * Registers a pure join node inside the template.
     *
     * @param localName local node name
     * @param dependencyRefs local dependency aliases or external binding keys
     * @param mode execution mode
     * @param merger merge function
     * @return same context for chaining
     */
    public AsyncGraphTemplateContext<C> addJoinStep(
            String localName,
            List<String> dependencyRefs,
            ExecutionMode mode,
            Function<List<C>, C> merger
    ) {
        builder.addJoinStep(
                instance.ref(localName),
                dependencyRefs.stream().map(instance::ref).toList(),
                mode,
                merger
        );
        return this;
    }

    /**
     * Resolves one local or bound name into the actual graph node name.
     *
     * @param localName local node name or external binding key
     * @return actual graph node name
     */
    public String ref(String localName) {
        return instance.ref(localName);
    }

    /**
     * @return immutable instance handle for referencing exported nodes from the outer graph
     */
    public AsyncGraphTemplateInstance<C> instance() {
        return instance;
    }
}
