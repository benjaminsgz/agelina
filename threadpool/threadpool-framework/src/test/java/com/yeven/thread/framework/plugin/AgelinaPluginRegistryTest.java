package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.dispatcher.DefaultExecutionDispatcher;
import com.yeven.thread.framework.dispatcher.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutorRegistry;
import com.yeven.thread.framework.table.SlotSymbolTable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgelinaPluginRegistryTest {

    @Test
    void shouldCollectAndSortStartupContributions() {
        AgelinaPlugin metricsPlugin = contributions -> contributions
                .slotSchema("quote-slots", 20, builder -> builder.getOrAllocate("payableAmount"))
                .runtime("default", 10, DefaultExecutionDispatcher::new);
        AgelinaPlugin flowPlugin = contributions -> contributions
                .slotSchema("base-slots", 10, builder -> builder.getOrAllocate("validatedContext"))
                .pipeline("login", 30, (builder, stepFactory) -> {
                })
                .graph("quote", 5, builder -> {
                });

        AgelinaPluginRegistry registry = AgelinaPluginRegistry.from(List.of(metricsPlugin, flowPlugin));

        assertEquals("default", registry.runtimes()[0].name());
        assertEquals("quote", registry.graphs()[0].name());
        assertEquals("login", registry.pipelines()[0].name());
        assertEquals("base-slots", registry.slotSchemas()[0].name());
        assertEquals("quote-slots", registry.slotSchemas()[1].name());
        assertNotNull(registry.runtime("default"));
        assertNotNull(registry.graph("quote"));
        assertNotNull(registry.pipeline("login"));
        assertNotNull(registry.slotSchema("base-slots"));
    }

    @Test
    void shouldRejectDuplicateContributionNamesWithinSameBucket() {
        AgelinaPlugin first = contributions -> contributions.runtime("default", 1, DefaultExecutionDispatcher::new);
        AgelinaPlugin second = contributions -> contributions.runtime("default", 2, DefaultExecutionDispatcher::new);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgelinaPluginRegistry.from(List.of(first, second))
        );

        assertEquals("Duplicate runtime contribution name: default", exception.getMessage());
    }

    @Test
    void shouldExposeStartupProceduresWithoutRuntimeLookupRequirement() {
        Executor directExecutor = Runnable::run;
        ExecutorRegistry executorRegistry = new ExecutorRegistry(Map.of(
                ExecutionMode.IO, directExecutor,
                ExecutionMode.CPU, directExecutor
        ));
        AgelinaPluginRegistry.Builder registryBuilder = AgelinaPluginRegistry.builder();
        registryBuilder
                .runtime("default", 0, DefaultExecutionDispatcher::new)
                .slotSchema("quote", 0, builder -> {
                    builder.getOrAllocate("validatedContext");
                    builder.getOrAllocate("availableStock");
                });
        AgelinaPluginRegistry registry = registryBuilder.build();

        ExecutionDispatcher dispatcher = registry.runtime("default").provider().create(executorRegistry);
        SlotSymbolTable.Builder symbolBuilder = SlotSymbolTable.builder();
        registry.slotSchema("quote").contributor().contribute(symbolBuilder);
        SlotSymbolTable symbolTable = symbolBuilder.build();

        assertSame(DefaultExecutionDispatcher.class, dispatcher.getClass());
        assertEquals(0, symbolTable.slotIdOf("validatedContext"));
        assertEquals(1, symbolTable.slotIdOf("availableStock"));
    }
}
