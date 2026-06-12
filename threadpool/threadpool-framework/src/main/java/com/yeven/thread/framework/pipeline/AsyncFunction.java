package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 异步处理的基础抽象函数式接口。
 *
 * <p>
 * 该接口设计极简：一个输入参数，返回一个 {@link CompletableFuture} 输出结果。
 * 它可以非常方便地将多个异步处理链进行拼接组合，避免引入沉重的策略类或过渡对象。
 * </p>
 *
 * @param <I> 输入参数类型
 * @param <O> 异步输出结果类型
 */
@FunctionalInterface
public interface AsyncFunction<I, O> extends Function<I, CompletableFuture<O>> {

    /**
     * 对输入的参数启动异步处理逻辑。
     *
     * @param input 输入参数
     * @return 异步执行完成后返回转换后结果的 {@link CompletableFuture}；若发生异常，则返回异常结束的 Future
     */
    @Override
    CompletableFuture<O> apply(I input);

    /**
     * 将当前异步函数与下一个异步函数进行链式组合。
     *
     * <p>
     * 返回的组合函数保留异常传递行为（Fail-Fast）。如果第一阶段的执行发生异常，
     * 第二阶段的异步函数将不会被触发调用。
     * </p>
     *
     * @param next 下一个异步函数
     * @param <N>  下一个异步函数的输出结果类型
     * @return 组合后的新异步函数
     */
    default <N> AsyncFunction<I, N> thenAsync(AsyncFunction<O, N> next) {
        return input -> this.apply(input).thenCompose(next::apply);
    }

    /**
     * 创建一个异步恒等函数，直接返回输入值。
     *
     * @param <T> 值类型
     * @return 一个接收到输入即以该值直接完成的 {@link CompletableFuture} 的异步恒等函数
     */
    static <T> AsyncFunction<T, T> identity() {
        return CompletableFuture::completedFuture;
    }
}
