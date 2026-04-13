package com.yeven.thread.demo.auth.service;

import com.yeven.thread.demo.auth.context.LoginContext;
import com.yeven.thread.demo.auth.dto.LoginRequest;
import com.yeven.thread.demo.auth.dto.LoginResponse;
import com.yeven.thread.demo.auth.flow.LoginFlowFactory;
import com.yeven.thread.framework.pipeline.AsyncPipeline;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final LoginFlowFactory loginFlowFactory;

    public AuthService(LoginFlowFactory loginFlowFactory) {
        this.loginFlowFactory = loginFlowFactory;
    }

    public Function<LoginRequest, CompletableFuture<LoginResponse>> login() {
        return request -> {
            LoginContext initContext = LoginContext.init(request.getUsername(), request.getPassword());
            AsyncPipeline<LoginContext> pipeline = loginFlowFactory.createLoginPipeline();
            return pipeline.execute(initContext)
                    .thenApply(context -> new LoginResponse(context.getUsername(), "bcrypt-only"));
        };
    }
}
