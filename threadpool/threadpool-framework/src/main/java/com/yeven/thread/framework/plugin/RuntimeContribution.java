package com.yeven.thread.framework.plugin;

import java.util.Objects;

/**
 * Runtime provider descriptor registered during startup.
 *
 * @param name unique contribution name
 * @param order startup application order
 * @param provider runtime provider
 */
public record RuntimeContribution(
        String name,
        int order,
        RuntimeProvider provider
) implements NamedContribution {

    public RuntimeContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(provider, "provider");
    }
}
