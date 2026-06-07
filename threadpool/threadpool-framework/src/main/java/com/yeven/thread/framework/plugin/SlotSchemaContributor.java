package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.pipeline.SlotSymbolTable;

/**
 * Startup procedure that contributes symbolic slots.
 */
@FunctionalInterface
public interface SlotSchemaContributor {

    /**
     * Allocates symbolic slot ids.
     *
     * @param builder slot symbol builder
     */
    void contribute(SlotSymbolTable.Builder builder);
}
