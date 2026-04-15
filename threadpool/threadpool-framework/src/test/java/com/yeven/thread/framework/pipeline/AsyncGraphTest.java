package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.DefaultExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.executor.ExecutorRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncGraphTest {

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService cpuExecutor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        ioExecutor.shutdownNow();
        cpuExecutor.shutdownNow();
    }

    @Test
    void shouldHandleSynchronousExceptionInStep() {
        AsyncGraph<Integer> graph = new AsyncGraphBuilder<Integer>(stepFactory())
                .addRootStep("root", ExecutionMode.DIRECT, value -> {
                    throw new RuntimeException("Sync error");
                })
                .build();

        CompletableFuture<Integer> future = graph.execute(1);
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        assertTrue(exception.getCause().getMessage().contains("Sync error"));
    }

    @Test
    void shouldExecuteParallelBranchesAndJoinResults() {
        AsyncStepFactory stepFactory = stepFactory();
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        AtomicInteger sequence = new AtomicInteger();

        AsyncGraph<String> graph = new AsyncGraphBuilder<String>(stepFactory)
                .addRootStep("start", ExecutionMode.DIRECT, value -> value + "-start")
                .addStep("left", "start", ExecutionMode.IO, value -> {
                    threadNames.add(Thread.currentThread().getName());
                    sleep(150);
                    return value + "-left-" + sequence.incrementAndGet();
                })
                .addStep("right", "start", ExecutionMode.CPU, value -> {
                    threadNames.add(Thread.currentThread().getName());
                    sleep(150);
                    return value + "-right-" + sequence.incrementAndGet();
                })
                .addJoinStep(
                        "join",
                        List.of("left", "right"),
                        ExecutionMode.DIRECT,
                        results -> results.get(0) + "|" + results.get(1)
                )
                .build();

        String result = graph.execute("seed").join();

        assertTrue(result.startsWith("seed-start-left-") || result.startsWith("seed-start-right-"));
        assertTrue(result.contains("|"));
        assertEquals(List.of("join"), graph.getTerminalNodes());
        assertEquals(2, threadNames.size());
    }

    @Test
    void shouldReturnAllNodeResults() {
        AsyncGraph<Integer> graph = new AsyncGraphBuilder<Integer>(stepFactory())
                .addRootStep("root", ExecutionMode.DIRECT, value -> value + 1)
                .addStep("double", "root", ExecutionMode.DIRECT, value -> value * 2)
                .addStep("triple", "root", ExecutionMode.DIRECT, value -> value * 3)
                .build();

        Map<String, Integer> results = graph.executeAll(2).join();

        assertEquals(3, results.get("root"));
        assertEquals(6, results.get("double"));
        assertEquals(9, results.get("triple"));
        assertIterableEquals(List.of("double", "triple"), graph.getTerminalNodes());
    }

    @Test
    void shouldRejectMultipleTerminalNodesForSingleExecute() {
        AsyncGraph<Integer> graph = new AsyncGraphBuilder<Integer>(stepFactory())
                .addRootStep("root", ExecutionMode.DIRECT, value -> value)
                .addStep("left", "root", ExecutionMode.DIRECT, value -> value + 1)
                .addStep("right", "root", ExecutionMode.DIRECT, value -> value + 2)
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> graph.execute(1));

        assertTrue(exception.getMessage().contains("multiple terminal nodes"));
    }

    @Test
    void shouldRejectCycles() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new AsyncGraphBuilder<Integer>(stepFactory())
                        .addNode(new AsyncGraphNodeDefinition<>(
                                "a",
                                ExecutionMode.DIRECT,
                                List.of("b"),
                                GraphNodeInput::getOnlyDependency,
                                value -> value
                        ))
                        .addNode(new AsyncGraphNodeDefinition<>(
                                "b",
                                ExecutionMode.DIRECT,
                                List.of("a"),
                                GraphNodeInput::getOnlyDependency,
                                value -> value
                        ))
                        .build()
        );

        assertTrue(exception.getMessage().contains("Cycle detected"));
    }

    @Test
    void shouldReuseTemplateWithDifferentNamespaces() {
        AsyncGraphTemplate<Integer> incrementTemplate = context ->
                context.addStep("increment", "input", ExecutionMode.DIRECT, value -> value + 1);

        AsyncGraphBuilder<Integer> builder = new AsyncGraphBuilder<Integer>(stepFactory())
                .addRootStep("start", ExecutionMode.DIRECT, value -> value);

        AsyncGraphTemplateInstance<Integer> left = builder.addTemplate(
                "leftBranch",
                incrementTemplate,
                Map.of("input", "start")
        );
        AsyncGraphTemplateInstance<Integer> right = builder.addTemplate(
                "rightBranch",
                incrementTemplate,
                Map.of("input", left.ref("increment"))
        );

        AsyncGraph<Integer> graph = builder
                .addJoinStep(
                        "join",
                        List.of(left.ref("increment"), right.ref("increment")),
                        ExecutionMode.DIRECT,
                        results -> results.get(0) + results.get(1)
                )
                .build();

        assertEquals(5, graph.execute(1).join());
    }

    private AsyncStepFactory stepFactory() {
        return new AsyncStepFactory(new DefaultExecutionDispatcher(new ExecutorRegistry(Map.of(
                ExecutionMode.IO, ioExecutor,
                ExecutionMode.CPU, cpuExecutor
        ))));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", e);
        }
    }
}
