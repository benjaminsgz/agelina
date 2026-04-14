package com.yeven.thread.demo.auth.controller;

import com.yeven.thread.demo.auth.dto.LoginRequest;
import com.yeven.thread.demo.auth.dto.LoginResponse;
import com.yeven.thread.demo.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Auth HTTP entrypoint.
 *
 * <p>This controller delegates login processing to an async pipeline and returns
 * {@link Mono} for Spring WebFlux (Reactor Netty).</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login endpoint.
     *
     * @param request login request payload
     * @return async login response
     */
    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Mono.fromFuture(authService.login().apply(request));
    }
}
