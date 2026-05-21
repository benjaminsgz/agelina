# Orchestrating Sequential Workflows with AsyncPipeline

`AsyncPipeline` is the sequential orchestration engine in Agelina. It is designed for straightforward business workflows that require a predictable, step-by-step execution path where each stage depends on the successful completion of the preceding step.

## Characteristics of AsyncPipeline

* **Sequential Execution**: Steps execute one by one in the exact order they are added.
* **Fail-Fast**: If any step encounters an exception, the entire pipeline immediately halts execution, completes exceptionally, and skips all remaining steps.
* **Shared Context Flow**: A single mutable or immutable context object `C` flows through the entire pipeline.
* **Explicit Resource Dispatching**: Although the orchestration flow is linear, each individual step runs on its declared thread pool (`IO` or `CPU`), or directly on the caller thread (`DIRECT`).

---

## Architectural Composition

An `AsyncPipeline` wraps an `AsyncStep<C>` composite chain. Under the hood, the pipeline uses the monadic `then` operator exposed in `AsyncStep` to compose the steps.

```java
default AsyncStep<C> then(AsyncStep<C> next) {
    return ctx -> this.apply(ctx).thenCompose(next::apply);
}
```

This constructs a nested chain of `CompletableFuture.thenCompose()` invocations, ensuring that:
1. Step N starts only after the future returned by Step N-1 resolves successfully.
2. The resolved output context of Step N-1 is passed directly as the input context to Step N.

---

## Code Example: Linear Login Flow

The following example demonstrates how to build and execute a sequential login flow using `AsyncPipeline`.

### 1. Define the Context Object

The context maintains the state of the request. We recommend using immutable records with "wither" methods for clean state updates:

```java
public record LoginContext(
    String username,
    String rawPassword,
    UserEntity user,
    boolean passwordVerified,
    String token,
    String errorMessage
) {
    public LoginContext withUser(UserEntity user) {
        return new LoginContext(username, rawPassword, user, passwordVerified, token, errorMessage);
    }

    public LoginContext withPasswordVerified(boolean passwordVerified) {
        return new LoginContext(username, rawPassword, user, passwordVerified, token, errorMessage);
    }

    public LoginContext withToken(String token) {
        return new LoginContext(username, rawPassword, user, passwordVerified, token, errorMessage);
    }
}
```

### 2. Assemble the Pipeline

Assemble the linear flow in your service class using `AsyncPipelineBuilder`:

```java
@Service
public class AuthFlowOrchestrator {

    private final AsyncStepFactory stepFactory;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    public AuthFlowOrchestrator(
            AsyncStepFactory stepFactory,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider
    ) {
        this.stepFactory = stepFactory;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public AsyncPipeline<LoginContext> buildLoginPipeline() {
        return new AsyncPipelineBuilder<LoginContext>()
            .addStep(stepFactory.create(new StepDefinition<>(
                "loadUser",
                ExecutionMode.IO,
                ctx -> {
                    UserEntity user = userRepository.findByUsername(ctx.username());
                    if (user == null) {
                        throw new IllegalArgumentException("User not found: " + ctx.username());
                    }
                    return ctx.withUser(user);
                }
            )))
            .addStep(stepFactory.create(new StepDefinition<>(
                "verifyPassword",
                ExecutionMode.CPU,
                ctx -> {
                    boolean verified = passwordEncoder.matches(ctx.rawPassword(), ctx.user().getPasswordHash());
                    if (!verified) {
                        throw new SecurityException("Invalid password credentials");
                    }
                    return ctx.withPasswordVerified(true);
                }
            )))
            .addStep(stepFactory.create(new StepDefinition<>(
                "generateToken",
                ExecutionMode.CPU,
                ctx -> {
                    String token = tokenProvider.createToken(ctx.user().getId(), ctx.user().getRole());
                    return ctx.withToken(token);
                }
            )))
            .build();
    }
}
```

### 3. Execute the Pipeline

Invoke the pipeline asynchronously. The execution returns a `CompletableFuture<C>`:

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthFlowOrchestrator orchestrator;

    public AuthController(AuthFlowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/login")
    public CompletableFuture<ResponseEntity<AuthResponse>> login(@RequestBody LoginRequest request) {
        LoginContext initialCtx = new LoginContext(request.username(), request.password(), null, false, null, null);
        AsyncPipeline<LoginContext> pipeline = orchestrator.buildLoginPipeline();

        return pipeline.execute(initialCtx)
            .thenApply(finalCtx -> ResponseEntity.ok(new AuthResponse(finalCtx.token())))
            .exceptionally(throwable -> {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, cause.getMessage()));
            });
    }
}
```

---

## Error Handling Semantics

### Fail-Fast Behavior
When a step throws an exception (e.g., `User not found` in the first step), the future chain immediately propagates this exception down the execution line without executing subsequent steps (`verifyPassword` and `generateToken` are skipped). This protects downstream services and conserves CPU resources from executing meaningless calculations on failed states.

### Thread Visibility Warning
The context object `C` is shared across sequential steps. If your context is mutable (such as a generic POJO rather than an immutable record), ensure that modifications are thread-safe. Since step transitions typically cross thread pool boundaries (e.g., switching from an `IO` thread to a `CPU` thread), JVM memory visibility barriers are guaranteed by the internal `CompletableFuture.thenCompose()` dispatching logic. However, parallel reads or writes from outside the pipeline during pipeline execution must be avoided.
