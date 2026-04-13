package com.yeven.thread.demo.auth.flow;

import com.yeven.thread.demo.auth.context.LoginContext;
import com.yeven.thread.demo.auth.repository.UserRepository;
import com.yeven.thread.demo.common.exception.BizException;
import com.yeven.thread.demo.common.model.User;
import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncPipeline;
import com.yeven.thread.framework.pipeline.AsyncPipelineBuilder;
import com.yeven.thread.framework.pipeline.AsyncStep;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;
import com.yeven.thread.framework.pipeline.StepDefinition;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class LoginFlowFactory {

    private final AsyncStepFactory stepFactory;
    private final UserRepository userRepository;
    private final CompositeStepDecorator decorator;

    public LoginFlowFactory(
            AsyncStepFactory stepFactory,
            UserRepository userRepository,
            CompositeStepDecorator decorator
    ) {
        this.stepFactory = stepFactory;
        this.userRepository = userRepository;
        this.decorator = decorator;
    }

    public AsyncPipeline<LoginContext> createLoginPipeline() {
        AsyncStep<LoginContext> loadUser = decorator.decorate(
                "loadUser",
                stepFactory.create(new StepDefinition<>(
                        "loadUser",
                        ExecutionMode.IO,
                        context -> {
                            User user = userRepository.findByUsername(context.getUsername());
                            if (user == null) {
                                throw new BizException("用户不存在");
                            }
                            return context.withUser(user);
                        }
                ))
        );

        AsyncStep<LoginContext> verifyPassword = decorator.decorate(
                "verifyPassword",
                stepFactory.create(new StepDefinition<>(
                        "verifyPassword",
                        ExecutionMode.CPU,
                        context -> {
                            if (context.getUser() == null) {
                                throw new BizException("用户上下文缺失");
                            }
                            boolean ok = BCrypt.checkpw(context.getPassword(), context.getUser().getPassword());
                            if (!ok) {
                                throw new BizException("密码错误");
                            }
                            return context;
                        }
                ))
        );

        return new AsyncPipelineBuilder<LoginContext>()
                .addStep(loadUser)
                .addStep(verifyPassword)
                .build();
    }
}
