package com.yeven.thread.framework.plugin;

import java.util.Objects;

/**
 * Pipeline contribution descriptor registered during startup.
 *
 * @param name unique contribution name
 * @param order startup application order
 * @param contributor pipeline contributor
 * @param <C> pipeline context type
 */
public record PipelineContribution<C>(
        String name,
        int order,
        PipelineContributor<C> contributor
) implements NamedContribution {

    public PipelineContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(contributor, "contributor");
    }
}
