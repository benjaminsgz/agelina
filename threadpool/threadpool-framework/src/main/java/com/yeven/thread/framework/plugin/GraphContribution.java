package com.yeven.thread.framework.plugin;

import java.util.Objects;

/**
 * Graph contribution descriptor registered during startup.
 *
 * @param name unique contribution name
 * @param order startup application order
 * @param contributor graph contributor
 * @param <C> graph context type
 */
public record GraphContribution<C>(
        String name,
        int order,
        GraphContributor<C> contributor
) implements NamedContribution {

    public GraphContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(contributor, "contributor");
    }
}
