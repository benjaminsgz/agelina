package com.yeven.thread.framework.pipeline;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable handle returned after one subgraph template is instantiated.
 *
 * <p>Use this handle to reference namespaced node names from the outer graph definition.</p>
 *
 * @param <C> graph context type
 */
public final class AsyncGraphTemplateInstance<C> {

    private final String namespace;
    private final Map<String, String> bindings;

    AsyncGraphTemplateInstance(String namespace, Map<String, String> bindings) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.bindings = Map.copyOf(bindings);
    }

    /**
     * Resolves one local template node name into the actual graph node name.
     *
     * @param localName local template node name or bound external alias
     * @return actual graph node name
     */
    public String ref(String localName) {
        Objects.requireNonNull(localName, "localName");
        String bound = bindings.get(localName);
        return bound != null ? bound : namespace + "." + localName;
    }

    /**
     * @return namespace prefix used for the instantiated template
     */
    public String getNamespace() {
        return namespace;
    }
}
