package com.yeven.thread.framework.plugin.runtime;

import com.yeven.thread.framework.dispatcher.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutorRegistry;

/**
 * 启动期从引导线程池注册表（ExecutorRegistry）中构建分发器的扩展程序接口。
 */
@FunctionalInterface
public interface RuntimeProvider {

    /**
     * 创建一个任务分发器。
     *
     * @param executorRegistry 线程池执行器注册表
     * @return 供编译后执行流（如 DAG）运行期使用的分发器
     */
    ExecutionDispatcher create(ExecutorRegistry executorRegistry);
}
