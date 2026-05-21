# 使用 AsyncPipeline 编排线性顺序业务流

`AsyncPipeline` 是 Agelina 中的线性顺序编排引擎。它专门用于处理流程清晰、步骤固定的业务流程。在这种流程中，每个执行阶段都严格依赖前一个阶段的成功执行结果。

## AsyncPipeline 的核心特征

* **严格顺序执行**：所有步骤按照它们被添加到构建器中的精确顺序依次串行执行。
* **快速失败（Fail-Fast）**：只要有任何一步抛出异常，整个管道的执行将立即中断，返回异常完成的 Future，并跳过后续所有未执行的步骤。
* **共享上下文流动**：一个统一的上下文对象 `C` 会作为输入输出流经整个执行步骤链。
* **显式资源调度**：虽然编排链在逻辑上是串行的，但每个步骤都可以在其声明的独立线程池（`IO` 或 `CPU`）上运行，或者在调用者线程（`DIRECT`）上运行。

---

## 架构组合机制

`AsyncPipeline` 的底层包装了一个由 `AsyncStep<C>` 组合而成的步骤链。其核心设计是利用了 `AsyncStep` 接口中声明的 Monadic `then` 操作符进行步骤叠加：

```java
default AsyncStep<C> then(AsyncStep<C> next) {
    return ctx -> this.apply(ctx).thenCompose(next::apply);
}
```

这会在底层构建一个高度嵌套的 `CompletableFuture.thenCompose()` 调用链，从而完美保证：
1. 仅在前一步骤 N-1 返回的 Future 正常 Resolved 之后，步骤 N 才会启动。
2. 步骤 N-1 的计算结果会作为新的 Context 入参直接传递给步骤 N 的应用逻辑。

---

## 代码示例：线性登录校验流

以下示例演示了如何使用 `AsyncPipeline` 构建并异步执行一个线性的用户登录校验流。

### 1. 定义共享上下文对象

上下文用于承载整个业务流的流转状态。我们强烈建议使用 Java Record，并搭配 "wither" 方法以实现安全优雅的状态更新：

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

### 2. 装配异步线性流

在 Service 业务类中，使用 `AsyncPipelineBuilder` 完成步骤的流水线式装配：

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

### 3. 执行管道任务

异步提交管道任务并处理最终结果。管道执行的返回值为 `CompletableFuture<C>`：

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

## 异常处理与性能设计

### 快速失败保障
一旦某个步骤触发异常（例如第一步 `loadUser` 中用户不存在），Future 异常链路将直接向下游传递该异常，并且后续步骤（`verifyPassword` 和 `generateToken`）将被全部跳过。这不仅能够保护下游服务，也能最大程度节省 CPU 算力，避免在无效或损坏的状态上进行多余计算。

### 线程可见性与多线程安全
在整个管道执行期间，上下文对象 `C` 在各个串行步骤之间共享。如果您使用了可变的 POJO 对象而非只读的 Record 对象，必须特别注意线程安全。由于步骤切换经常跨越线程池边界（例如从 `IO` 线程切换至 `CPU` 线程），JVM 内部的内存可见性屏障已由底层 `CompletableFuture.thenCompose()` 提供的 Happen-Before 机制予以保障，但仍然要严禁任何管道外部的线程在管道执行期间并发修改此共享上下文。
