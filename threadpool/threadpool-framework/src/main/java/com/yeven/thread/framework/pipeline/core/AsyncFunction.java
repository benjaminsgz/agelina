package com.yeven.thread.framework.pipeline.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 异步处理的基础抽象函数式接口。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>极简的单输入/单异步输出抽象：</b> 统一了 JDK 标准 {@link java.util.function.Function} 的异步形态，将传统同步计算模型无缝升级为基于 {@code CompletableFuture} 的非阻塞计算模型。</li>
 *   <li><b>管道式逻辑组合能力：</b> 提供默认方法 {@link #thenAsync(AsyncFunction)}，允许开发者通过类似于 Linux 管道的函数式声明（Pipeline-like Composition），将多个不同输入输出类型的异步步骤串联起来，保持了极佳的扩展性与可读性。</li>
 * </ul>
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
