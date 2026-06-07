package com.yeven.thread.framework.plugin;

/**
 * Startup-only extension point for Agelina.
 *
 * <p>Plugins must only contribute builders, handlers, schemas, and runtime providers
 * during application startup. The execution hot path should use compiled arrays and
 * function tables, not plugin lookup.</p>
 */
@FunctionalInterface
public interface AgelinaPlugin {

    /**
     * Registers startup contributions.
     *
     * @param contributions mutable startup contribution target
     */
    void contribute(AgelinaContributions contributions);
}
