package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.core.AsyncStep;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 步骤日志装饰器，负责记录每一个异步步骤的执行耗时与失败异常。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>透明的可观测性：</b> 提供了零侵入式的方法级耗时度量与异常抓取通道，无需为每个 AsyncStep 手写日志记录代码，确保了代码库的整洁和诊断记录的统一。</li>
 *   <li><b>可扩展的微型切面：</b> 本身可作为一个低开销的默认实现，易于在本地调试或非生产环境下提供快速的执行流耗时记录，同时也充当了对接第三方监控（如 OpenTelemetry、SkyWalking）的参考标杆。</li>
 * </ul>
 */
public class LoggingStepDecorator implements StepDecorator {

    private static final Logger LOGGER = Logger.getLogger(LoggingStepDecorator.class.getName());

    @Override
    public <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step) {
        return context -> {
            long start = System.currentTimeMillis();
            return step.apply(context).whenComplete((result, ex) -> {
                long cost = System.currentTimeMillis() - start;
                if (ex == null) {
                    LOGGER.info(() -> "[STEP] " + stepName + " SUCCESS cost=" + cost + "ms");
                } else {
                    LOGGER.log(Level.WARNING,
                            "[STEP] " + stepName + " FAILED cost=" + cost + "ms",
                            ex);
                }
            });
        };
    }
}
