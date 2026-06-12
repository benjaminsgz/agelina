package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 步骤日志装饰器，负责记录每一个异步步骤的执行耗时与失败异常。
 *
 * <p>该装饰器被设计为一个极低开销的默认可观测层。
 * 在生产级追踪系统中，建议实现自定义的 {@link StepDecorator}（例如接入 OpenTelemetry 或 SkyWalking）并将其注册为 Spring Bean。</p>
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
