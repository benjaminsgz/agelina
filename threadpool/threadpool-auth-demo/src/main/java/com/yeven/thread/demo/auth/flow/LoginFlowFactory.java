package com.yeven.thread.demo.auth.flow;

import com.yeven.thread.demo.auth.context.LoginContext;
import com.yeven.thread.demo.auth.repository.UserRepository;
import com.yeven.thread.demo.common.exception.BizException;
import com.yeven.thread.demo.common.model.User;
import com.yeven.thread.demo.util.JwtUtils;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.pipeline.linear.AsyncPipeline;
import com.yeven.thread.framework.pipeline.linear.AsyncPipelineBuilder;
import com.yeven.thread.framework.pipeline.core.AsyncStep;
import com.yeven.thread.framework.pipeline.core.AsyncStepFactory;
import com.yeven.thread.framework.pipeline.core.StepDefinition;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * Factory that assembles the login pipeline.
 *
 * <p>
 * Current chain:
 * </p>
 * <ol>
 * <li>load user from database on {@link ExecutionMode#IO}</li>
 * <li>verify bcrypt password on {@link ExecutionMode#CPU}</li>
 * <li>issue JWT token after authentication succeeds on
 * {@link ExecutionMode#CPU}</li>
 * </ol>
 */
@Component
public class LoginFlowFactory {

    private final AsyncStepFactory stepFactory;
    private final UserRepository userRepository;
    private final CompositeStepDecorator decorator;
    private final JwtUtils jwtUtils;

    public LoginFlowFactory(
            AsyncStepFactory stepFactory,
            UserRepository userRepository,
            CompositeStepDecorator decorator,
            JwtUtils jwtUtils) {
        this.stepFactory = stepFactory;
        this.userRepository = userRepository;
        this.decorator = decorator;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Creates one fresh login pipeline instance.
     *
     * @return login pipeline
     */
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
                        })));

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
                        })));

        AsyncStep<LoginContext> issueToken = decorator.decorate(
                "issueToken",
                stepFactory.create(new StepDefinition<>(
                        "issueToken",
                        ExecutionMode.CPU,
                        context -> context.withToken(jwtUtils.createToken(context.getUser())))));

        return new AsyncPipelineBuilder<LoginContext>()
                .addStep(loadUser)
                .addStep(verifyPassword)
                .addStep(issueToken)
                .build();
    }
}
