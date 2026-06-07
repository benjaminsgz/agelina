package com.yeven.thread.framework.plugin;

import java.util.Objects;

/**
 * Slot schema contribution descriptor registered during startup.
 *
 * @param name unique contribution name
 * @param order startup application order
 * @param contributor slot schema contributor
 */
public record SlotSchemaContribution(
        String name,
        int order,
        SlotSchemaContributor contributor
) implements NamedContribution {

    public SlotSchemaContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(contributor, "contributor");
    }
}
