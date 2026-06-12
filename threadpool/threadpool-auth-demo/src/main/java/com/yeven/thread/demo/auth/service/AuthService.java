package com.yeven.thread.demo.auth.service;

import com.yeven.thread.demo.auth.context.LoginContext;
import com.yeven.thread.demo.auth.dto.LoginRequest;
import com.yeven.thread.demo.auth.dto.LoginResponse;
import com.yeven.thread.demo.auth.flow.LoginFlowFactory;
import com.yeven.thread.framework.pipeline.AsyncPipeline;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Application service that exposes login as a composable process.
 *
 * <p>Returning {@code Function<LoginRequest, CompletableFuture<LoginResponse>>} keeps control-flow
 * composition explicit and avoids introducing extra stateful service objects.</p>
 */
@Service
public class AuthService {

    private final LoginFlowFactory loginFlowFactory;

    public AuthService(LoginFlowFactory loginFlowFactory) {
        this.loginFlowFactory = loginFlowFactory;
    }

    /**
     * Builds the login processing function.
     *
     * @return function that maps request to async response
     */
    public Function<LoginRequest, CompletableFuture<LoginResponse>> login() {
        return request -> {
            LoginContext initContext = LoginContext.init(request.getUsername(), request.getPassword());
            AsyncPipeline<LoginContext> pipeline = loginFlowFactory.createLoginPipeline();
            return pipeline.execute(initContext)
                    .thenApply(context -> new LoginResponse(context.getUsername(), context.getToken()));
        };
    }
}
