# Agelina 全量测试与 CI 质量门禁设计

## 目标

为 `threadpool` 多模块 Maven 项目建立覆盖所有有意义业务分支的自动化测试体系，并将每个含业务逻辑模块的 JaCoCo 行覆盖率门槛设置为 80%。CI 同时执行阻断式代码规范检查、静态缺陷分析和依赖漏洞监测，只有全部质量门禁通过后才允许发布。

## 范围

测试覆盖以下五个 Maven 子模块：

- `threadpool-framework`
- `threadpool-spring-boot-autoconfigure`
- `threadpool-spring-boot-starter`
- `threadpool-auth-demo`
- `threadpool-dag-demo`

覆盖率统计排除没有业务分支的启动类、纯标记类以及仅承载数据的 DTO。排除规则必须在父 POM 中集中声明，并保持最小化；包含校验、转换或派生逻辑的数据类不得排除。

## 测试策略

### Framework

对公共行为和关键内部状态转换进行单元测试，覆盖：

- `pipeline/core` 的异步函数组合、步骤工厂、同步异常和 Future 异常传播。
- `pipeline/linear` 的顺序执行、空管道、短路和上下文传递。
- `pipeline/slot` 的符号分配、补丁合并、重复写入、非法索引和只读视图。
- `pipeline/graph` 与现有 `graph/runtime` 实现的依赖校验、并发节点、终端路径、失败传播、指标钩子和资源释放。
- `dispatcher/executor` 的 DIRECT、IO、CPU 路由，拒绝策略，取消和异常完成。
- `decorator` 的顺序、空装饰链、日志成功与失败路径。
- `plugin` 的排序、重复名称、空名称、查找缺失和贡献执行。
- 注解元数据、定义对象、枚举以及具备行为的辅助类型。

测试以真实对象为主，仅在需要隔离线程池、时钟、日志或外部协作者时使用 Mockito。并发测试使用 latch、barrier 或可控 executor，不使用固定 `sleep` 作为同步手段。

### Spring Boot Auto-config 与 Starter

使用 `ApplicationContextRunner` 和最小 Spring 上下文验证：

- 默认 Bean 完整注册。
- 用户自定义 Bean 能覆盖默认实现。
- IO/CPU 线程池属性绑定、合法边界和非法配置。
- 拒绝静默丢弃任务的策略。
- `AsyncStepBeanPostProcessor` 的默认方法名、显式名称、空白裁剪、代理、非 public 方法、重复名称、所有非法签名和业务异常传播。
- 装饰器的有序聚合以及缺失装饰器时的行为。
- 生命周期关闭、超时强制关闭和中断恢复。
- Starter 自动配置发现和最小应用启动冒烟测试。

### Auth Demo

测试不连接真实数据库或外部服务。使用内存协作者、Mockito 和 MockMvc 覆盖：

- JWT 生成、解析、过期、错误签名和非法 token。
- 用户查询存在、不存在和数据访问异常。
- 登录请求校验、密码失败、禁用用户、成功签发 token。
- 登录管道步骤顺序和任一步骤失败时的短路。
- Service 成功与异常映射。
- Controller 请求校验、成功响应和统一异常响应。
- 配置属性边界和上下文数据转换。

### DAG Demo

使用确定性的测试替身覆盖：

- Inventory、会员和商品 Gateway 的正常、缺失与非法输入路径。
- 报价上下文标准化和各价格计算分支。
- 图构建、库存不足、会员折扣、优惠叠加、节点异常和终端输出。
- 并发预览限流的获取、拒绝、释放与异常释放。
- Service 的成功、超时、图执行失败和响应转换。
- Controller 的输入校验、成功响应、业务异常与未知异常。
- 配置属性边界和统一异常格式。

## 覆盖率门禁

父 POM 统一配置 JaCoCo `check`，在 `verify` 阶段执行。`threadpool-framework`、`threadpool-spring-boot-autoconfigure`、`threadpool-auth-demo` 和 `threadpool-dag-demo` 分别要求行覆盖率不低于 0.80。

`threadpool-spring-boot-starter` 只有标记和装配职责，使用启动冒烟测试验证，不单独要求代码覆盖率。CI 上传各模块 XML 报告到 Codecov，但 GitHub 外部服务不可用时，本地 JaCoCo 门禁仍是最终判定依据。

覆盖率规则以行覆盖率作为硬门槛；分支覆盖率持续展示但首轮不设硬门槛，避免因编译器生成分支造成错误阻断。所有明确识别的业务分支仍必须在测试矩阵中有对应测试。

## Lint 与静态分析

- 将 Maven Checkstyle 的 `failOnViolation` 和 `failsOnError` 打开，使规范错误直接阻断构建。
- 增加 SpotBugs Maven 插件，在 `verify` 阶段执行并对高置信度缺陷失败。
- Lint 配置集中在父 POM，子模块继承同一规则。
- 首轮只修复新门禁实际发现的问题，不顺带进行无关格式重构。

## 漏洞监测

- 保留现有 CodeQL，在 push、pull request 和每周计划任务执行。
- 增加 `.github/dependabot.yml`，每周检查 Maven 与 GitHub Actions 依赖更新，并限制单次打开的 PR 数量。
- 增加 Dependency Review workflow，在 pull request 中阻断引入已知高危或严重漏洞的依赖。
- 安全扫描只申请所需最小权限，不在 fork PR 中暴露密钥。

## CI/CD 流程

Pull request 和 main push 执行：

1. `lint`：JDK 17 下运行 Checkstyle 与 SpotBugs。
2. `test`：JDK 17、21 矩阵运行 `mvn clean verify`，执行测试与 80% 覆盖率门禁。
3. `dependency-review`：仅 PR 执行依赖漏洞差异审查。
4. `codeql`：保留独立安全分析工作流。

发布 job 依赖 lint、test 和适用的安全检查。PR 永不发布；main 或版本标签仅在所有依赖 job 成功后发布。

## 测试可靠性要求

- 测试不得依赖执行顺序、真实网络、真实数据库或本机端口。
- 每个测试负责关闭自己创建的 executor 和 Spring context。
- 时间相关测试使用可控时钟或宽松边界，不依赖毫秒级竞态。
- 并发测试设置明确超时，失败时输出可诊断信息。
- 生产缺陷在修复前必须先由失败测试稳定复现。

## 验收标准

- `mvn clean verify` 在 JDK 17 和 JDK 21 下通过。
- 四个业务模块的 JaCoCo 行覆盖率均达到或超过 80%。
- 所有识别出的成功、失败、边界、异常和并发业务分支都有自动化测试。
- Checkstyle 和 SpotBugs 发现的问题会使 CI 失败。
- Dependabot、Dependency Review 与 CodeQL 配置可被 GitHub 正确识别。
- 发布 job 不会绕过测试、lint 或安全门禁。
- 工作区不遗留测试生成文件、线程、端口监听或未清理上下文。
