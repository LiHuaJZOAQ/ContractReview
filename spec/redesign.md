# 重设计文档 (Redesign Plan)

> **版本**: V1.1
> **日期**: 2026-09-01
> **范围**: 后端架构加固 + 前端关键安全项 + 生产化部署
> **当前开发分支**: ai/auto/redesign（保留到 develop 后改用 work 分支）
> **目标合并分支**: develop

## 开发准则

- 禁止提交git，严格禁止推送push操作
- 非必要不修改已经验证过可用的模块
- 必须保持前后端对齐
- 需求+测试驱动开发
- 禁止修改、删除未被git追踪的文件
- 后续开发要排除冗余设计和代码，但是发现已有的冗余禁止清理
- 如果我明确要求提交git，可以提交并严格遵循下面要求： 
  - git提交信息格式： <提交类型>: <标题一句话概括>  <换行！！！一定要换行（\n\n）>  <后面用- 一点一行写details具体改动> 。标题禁止出现+/等符号除非属于技术名词需要，标题使用动作描述如“增加了...修改了...”。
  - git需要分批提交，一个最小的逻辑改动为一次提交，一次提交只解决一个问题。不同的问题不能混合提交。

## 当前进度总览（2026-09-01）

| 阶段 | 状态 |
|---|---|
| Phase 1 安全与可靠性 | 已完成 |
| Phase 2 可观测性 | 已完成 |
| Phase 3 工程化 | 已完成 |
| Phase 4 功能完善 | 进行中（管理后台、监控、操作日志已落地；门户页面/法规录入多格式/个人中心细节优化待办） |
| Phase 5 生产化部署 | 未启动 |

## 后续工作

- 将 ai/auto/redesign 分支以 --no-ff 方式合并到 develop
- 基于最新的 develop 创建 work 分支继续 Phase 4 余下需求与 Phase 5


---

## 1. 文档概述

### 1.1 背景

ContractReview 是一个基于 Multi-Agent + RAG 的智能合同风险审查系统。当前项目已完成核心功能原型（上传→解析→分类→RAG检索→风险扫描→报告生成），但在安全、可靠性、可观测性、工程化四个维度存在系统性差距，不满足企业级后端项目标准。

本文档基于全量代码审查和 TODO.md 分析，制定分阶段重设计方案。

### 1.2 目标

- 达到企业级后端安全与可靠性标准
- 建立可观测性体系，支持生产运维
- 统一代码规范与工程实践
- 整合 TODO.md 中的功能需求到统一路线图

### 1.3 不在范围内

- 管理后台（独立项目，另行规划）
- 数据库迁移工具 Flyway（暂不引入）
- 性能压测与容量规划
- 前端全面重构

---

## 2. 现状评估

### 2.1 已达成的能力

| 维度 | 状态 |
|------|------|
| 核心审查流水线 | Agent A->B->C 完整可用 |
| 异步消息驱动 | RabbitMQ + DLX 死信重试 |
| RAG 法条检索 | Chroma向量检索 + LLM fallback |
| SSE 实时推送 | 按Agent步骤推送进度 |
| JWT 认证 | Access/Refresh Token + BCrypt |
| 限流 | Redis Lua 滑动窗口 |
| 文件存储 | MinIO 对象存储 |
| 操作审计 | AOP 注解式审计日志 |

### 2.2 关键差距

| 维度 | 等级 | 核心问题 |
|------|------|----------|
| **安全** | 严重 | 密钥硬编码、CORS全开、JWT URL传参、无输入校验、无角色权限 |
| **可靠性** | 严重 | @Transactional自调用失效、MQ auto-ack、DLX重试状态不匹配、额度竞态 |
| **可观测性** | 不足 | 零MDC、零追踪、零Actuator、静默吞异常 |
| **工程化** | 不足 | 枚举未使用、死代码、无API文档、无结构化日志 |

---

## 3. 重设计目标

### 3.1 安全目标

- 所有密钥通过环境变量注入，源码中零明文
- CORS 仅允许前端实际域名
- JWT 仅通过 Authorization Header 传递
- 所有输入有长度和格式校验
- 基础 RBAC 角色模型就绪（为管理后台铺路）

### 3.2 可靠性目标

- 事务注解实际生效（修复自调用问题）
- MQ 消息不丢失（手动 ack + 失败重入队）
- 幂等重试（状态机 + 去重）
- 并发安全（额度扣减原子化）

### 3.3 可观测性目标

- MDC 全链路（userId、taskId、traceId）
- Actuator 健康检查 + 线程池监控
- 请求级日志（method、URI、status、duration）
- 异常分级（业务异常 vs 系统异常）

### 3.4 工程化目标

- 枚举替代字符串字面量（TaskStatus、RiskLevel、ContractType）
- 类型安全 DTO 替代 Map<String, Object>
- 死代码清理
- 统一响应格式（requestId 全量返回）
- OpenAPI 文档

---

## 4. 阶段规划

> **图例**: ✅ 已完成 · 🟡 进行中 · ⚪ 未启动

### Phase 1: 安全加固与可靠性修复 ✅

> 目标: 消除上线阻塞项，确保核心流程正确

#### P1-1 密钥外部化

| 项 | 内容 |
|---|------|
| **涉及文件** | `application.yml`, `application-dev.yml`, `docker-compose.yml`, 新建 `.env.example` |
| **核心思路** | 所有密钥替换为 `${ENV_VAR:default}` 格式；提供 `.env.example` 模板；`.env` 加入 `.gitignore`；`docker-compose.yml` 用 `env_file` 引入 |
| **验收标准** | 源码 `grep -r "root123|minioadmin|sk-fRAC|ContractReviewSecret" src/ docker-compose.yml` 返回零结果；`.env.example` 包含所有必要变量 |

#### P1-2 CORS 收紧

| 项 | 内容 |
|---|------|
| **涉及文件** | `SecurityConfig.java`, `application.yml` |
| **核心思路** | `allowedOriginPatterns` 读取配置 `${cors.allowed-origins}`，dev环境允许 localhost:5173，prod仅允许实际域名；保留 `allowCredentials(true)` |
| **验收标准** | 配置项可切换；非白名单来源收到 CORS 拒绝 |

#### P1-3 移除 JWT URL 传参

| 项 | 内容 |
|---|------|
| **涉及文件** | `JwtAuthenticationFilter.java` |
| **核心思路** | 删除 `request.getParameter("token")` 逻辑（第52-55行），仅保留 `Authorization` Header提取 |
| **验收标准** | `?token=xxx` 请求返回 401；Header传递正常工作 |

#### P1-4 输入校验

| 项 | 内容 |
|---|------|
| **涉及文件** | `AuthRequest.java`, `ContractController.java`（upload/size校验） |
| **核心思路** | AuthRequest 加 `@Size(min=2,max=50)` username、`@Size(min=6,max=128)` password；upload 方法加 `@Max` 文件大小校验；page/size 参数加 `@Min(1)` `@Max(100)` |
| **验收标准** | 超长/过短输入返回 400 + 明确错误信息 |

#### P1-5 修复 @Transactional 自调用

| 项 | 内容 |
|---|------|
| **涉及文件** | `ReviewMessageListener.java` |
| **核心思路** | `handleSuccess` 和 `handleFailure` 方法提取到独立的 `ReviewResultHandler` Service 类中；`ReviewMessageListener` 通过注入该 Service 调用，确保代理 AOP 生效 |
| **验收标准** | `handleSuccess` 内的多个 DB 操作在同一事务中；模拟任一操作失败时整体回滚 |

#### P1-6 MQ 可靠性

| 项 | 内容 |
|---|------|
| **涉及文件** | `ReviewMessageListener.java`, `RabbitMqConfig.java` |
| **核心思路** | 1) `@RabbitListener` 加 `acknowledgeMode = MANUAL`；2) 在异步 `CompletableFuture.whenComplete()` 回调中手动 ack/nack；3) nack 时设置 `requeue=false` 让消息进入 DLX；4) DLX 重试前先将任务状态重置为 PENDING |
| **验收标准** | 模拟异步处理失败后，消息正确进入 DLX 并重试；任务状态在重试前被正确重置 |

#### P1-7 额度扣减原子化

| 项 | 内容 |
|---|------|
| **涉及文件** | `ContractServiceImpl.java`, Lua 脚本 |
| **核心思路** | 将 check-then-set（第126-139行）合并为单个 Lua 脚本：原子执行 `GET -> 判断 -> DECR -> 返回`；首次扣减时 Lua 脚本内 SET + DECR 原子完成 |
| **验收标准** | 并发100个 submit 请求，扣减总数不超过用户额度；Redis quota 值与 DB review_quota 一致 |

#### P1-8 SSE 连接清理

| 项 | 内容 |
|---|------|
| **涉及文件** | `SseServiceImpl.java` |
| **核心思路** | 1) emitter 设置合理超时（如 300s）；2) 注册 `onCompletion`/`onError`/`onTimeout` 回调，从 map 中移除；3) 加定时任务扫描过期 emitter |
| **验收标准** | 客户端断开后 emitter 从 map 中移除；运行24小时后 map 大小不持续增长 |

#### P1-9 SSE 端点鉴权

| 项 | 内容 |
|---|------|
| **涉及文件** | `ContractController.java` |
| **核心思路** | `progress()` 方法中验证 taskId 属于当前 userId，拒绝越权访问 |
| **验收标准** | 用户A无法订阅用户B的进度流；返回 403 |

#### P1-10 UserContext 异步传递

| 项 | 内容 |
|---|------|
| **涉及文件** | `UserContext.java`, `AgentOrchestratorImpl.java` |
| **核心思路** | 改用 `InheritableThreadLocal` 或在 `executeReview` 入口处手动从 MQ message 中提取 userId 并设置到新线程的 UserContext |
| **验收标准** | 异步线程中 `UserContext.getUserId()` 返回正确值；审计日志中 userId 不为 null |

---

### Phase 2: 可观测性与运维能力 ✅

> 目标: 能看到系统在做什么，出了问题能快速定位

#### P2-1 MDC 全链路标识

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `MdcFilter.java`, `logback-spring.xml`（或在 `application.yml` 配置） |
| **核心思路** | HTTP Filter 中生成/读取 `X-Request-Id`（UUID），放入 MDC `traceId`；MQ 消息 Header 中传递 traceId；Async 线程入口处从 Message 中提取并设置 MDC；日志格式加入 `%X{traceId}` |
| **验收标准** | 一次完整审查的所有日志（HTTP->MQ->3个Agent）包含相同 traceId |

#### P2-2 请求级访问日志

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `AccessLogFilter.java` |
| **核心思路** | 记录 method、URI、status、duration、userId；放在 Filter 链最外层；日志格式统一 |
| **验收标准** | 每个 HTTP 请求有一行 access log，包含上述字段 |

#### P2-3 Actuator 健康检查

| 项 | 内容 |
|---|------|
| **涉及文件** | `pom.xml`, `application.yml`, `application-dev.yml` |
| **核心思路** | 引入 `spring-boot-starter-actuator`；暴露 `/actuator/health`、`/actuator/info`、`/actuator/metrics`；自定义 `HealthIndicator` 检查 MySQL/Redis/MinIO/RabbitMQ 连接 |
| **验收标准** | `curl /actuator/health` 返回各组件状态；Docker Compose 加 healthcheck |

#### P2-4 Docker 健康检查

| 项 | 内容 |
|---|------|
| **涉及文件** | `docker-compose.yml`, `Dockerfile` |
| **核心思路** | `Dockerfile` 加 `HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health`；`docker-compose.yml` 各服务加 `healthcheck` 配置；Spring Boot 服务加 `depends_on.condition: service_healthy` |
| **验收标准** | `docker ps` 显示 healthy 状态；基础设施服务先于应用启动 |

#### P2-5 异常分级处理

| 项 | 内容 |
|---|------|
| **涉及文件** | `GlobalExceptionHandler.java`, `BusinessException.java`, `ErrorCode.java` |
| **核心思路** | ErrorCode 增加 HTTP 状态码映射（`TASK_NOT_FOUND -> 404`、`LLM_API_FAILED -> 502`、`QUOTA_INSUFFICIENT -> 429`）；GlobalExceptionHandler 根据 ErrorCode 映射 HTTP status；业务异常 log.warn，系统异常 log.error；ErrorCode 补齐 1007 缺口 |
| **验收标准** | 不同 ErrorCode 返回正确的 HTTP 状态码；日志级别按异常类型区分 |

#### P2-6 结构化日志配置

| 项 | 内容 |
|---|------|
| **涉及文件** | `application.yml`, `application-dev.yml`（或新建 `application-prod.yml`） |
| **核心思路** | prod 环境配置 JSON 格式日志（Logback JSON encoder）；dev 保持可读格式；日志字段包含 timestamp、level、logger、traceId、userId、taskId、message |
| **验收标准** | prod 日志为 JSON 格式，可被 ELK/Datadog 解析 |

#### P2-7 消除静默异常

| 项 | 内容 |
|---|------|
| **涉及文件** | `ContractServiceImpl.java:244`, `SseServiceImpl.java:54`, `AgentServiceImpl.java` 多处 |
| **核心思路** | 将 `catch (Exception ignored) {}` 改为 `catch (Exception e) { log.warn("...", e) }`；LLM 解析失败增加 metric 计数器 |
| **验收标准** | 代码中无 `catch (Exception ignored)` 或空 catch 块 |

---

### Phase 3: 工程化与代码质量 ✅

> 目标: 代码可维护、可扩展、风格统一

#### P3-1 枚举替代字符串字面量

| 项 | 内容 |
|---|------|
| **涉及文件** | `TaskStatus.java`, `RiskLevel.java`（已定义未使用）；`ReviewTask.java`, `RiskItem.java`, `ContractServiceImpl.java`, `AgentOrchestratorImpl.java`, `ReviewMessageListener.java`, `ReviewStateMachineImpl.java` 等 |
| **核心思路** | entity 中 status 字段类型从 String 改为枚举（MyBatis-Plus 自动处理）；所有字符串字面量 `"PENDING"` `"HIGH"` 等替换为枚举引用；新增 `ContractType`、`UserStance` 枚举 |
| **验收标准** | 编译通过；`grep -r '"PENDING"\|"FAILED"\|"HIGH"\|"MEDIUM"\|"LOW"' src/main/java` 返回零结果 |

#### P3-2 类型安全 DTO 替代 Map

| 项 | 内容 |
|---|------|
| **涉及文件** | `AgentServiceImpl.java`, `AgentOrchestratorImpl.java`, `ContractServiceImpl.java` |
| **核心思路** | 新建 `ClassifyResult`, `ScanResult`, `SummarizeResult` DTO；AgentService 接口返回类型从 `Map<String, String>` / `Map<String, Object>` 改为具体 DTO；消除 `@SuppressWarnings("unchecked")` |
| **验收标准** | 编译通过且无 unchecked 警告；LLM 输出解析使用 `ObjectMapper.readValue(json, ClassifyResult.class)` |

#### P3-3 死代码清理

| 项 | 内容 |
|---|------|
| **涉及文件** | `LLMReviewService.java`（删除）, `WebMvcConfig.java`（删除）, `QuotaRollbackHandler.java`（删除或合并到 ReviewResultHandler）, `ContractReviewApplication.java`（移除重复 `@EnableAsync`） |
| **核心思路** | `LLMReviewService` 从未被调用，直接删除；`WebMvcConfig` 为空类，删除；`QuotaRollbackHandler` 逻辑合并到 P1-5 创建的 `ReviewResultHandler`；移除 `@EnableAsync` 重复声明 |
| **验收标准** | `mvn compile` 通过；`grep -r "LLMReviewService\|WebMvcConfig\|QuotaRollbackHandler" src/` 返回零结果 |

#### P3-4 统一 requestId 返回

| 项 | 内容 |
|---|------|
| **涉及文件** | `R.java`, `MdcFilter.java`（P2-1 创建） |
| **核心思路** | `R.ok()` 也设置 requestId（从 MDC 中读取 traceId）；响应体格式统一为 `{"code":0,"message":"success","data":...,"timestamp":"...","requestId":"..."}` |
| **验收标准** | 成功响应也包含 requestId 字段 |

#### P3-5 OpenAPI 文档

| 项 | 内容 |
|---|------|
| **涉及文件** | `pom.xml`, `SecurityConfig.java`（放行 swagger 路径）, 各 Controller |
| **核心思路** | 引入 `springdoc-openapi-starter-webmvc-ui`；Controller 方法加 `@Operation`/`@ApiResponse` 注解；`/swagger-ui.html` 仅 dev 环境可用 |
| **验收标准** | dev 环境可访问 Swagger UI；所有接口有文档说明 |

#### P3-6 JSON 构建修复

| 项 | 内容 |
|---|------|
| **涉及文件** | `OperationLogAspect.java` |
| **核心思路** | 第43行 `String.format` 拼 JSON 改为使用注入的 `ObjectMapper`；task ID 提取逻辑从"找第一个Long参数"改为注解属性指定 |
| **验收标准** | method 名含特殊字符时 JSON 仍然合法；审计日志中 taskId 正确 |

#### P3-7 前端安全与质量

| 项 | 内容 |
|---|------|
| **涉及文件** | `web/src/stores/auth.js`, `web/vite.config.js`, `web/package.json`, 新建 `.eslintrc.js`, `.prettierrc` |
| **核心思路** | 1) token 存储：评估 httpOnly cookie 方案（需后端 Set-Cookie 配合）；如短期无法完成，先加 XSS 防护（CSP header、输入转义）；2) 配置 ESLint + Prettier，CI 中 `npm run lint` 改为 `continue-on-error: false`；3) 界面风格保持不变 |
| **验收标准** | `npm run lint` 零 error；CI lint 失败时构建失败；无 XSS 注入点 |

#### P3-8 Application CI 配置

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `src/main/resources/application-ci.yml` |
| **核心思路** | CI profile 配置使用服务容器地址（`${MYSQL_HOST:localhost}`），与 `ci.yml` workflow 中定义的容器对齐；禁用不必要的日志 |
| **验收标准** | `SPRING_PROFILES_ACTIVE=ci mvn test` 可在 CI 中正常运行 |

---


### Phase 4: 功能完善 🟡

> 已完成: P4-1 LLM 404 修复 / P4-3 多格式合同 / P4-4 重试 UI / P4-5 历史页进度 / P4-6 个人中心 / P4-7 额度显示 / P4-8 法律法规库 / P4-9 管理后台主体（含 P4-10 前端对齐）/ P4-11 测试验证
> 待办（来自 TODO.md）:
> - 个人中心细节优化（弹窗改密码、额度数字化、额度使用详情、时区统一）
> - 系统日志可视化（backend / frontend / docker log 一站式查看）
> - 管理员新增用户、重置密码为 reviewuser
> - 法规录入支持 PDF / Word / TXT / 直接输入
> - 门户页面（AI 对话式，可附加审查报告与法规）

> 目标: 整合 TODO.md 中的功能需求

#### P4-1 LLM API 404 修复

| 项 | 内容 |
|---|------|
| **涉及文件** | `application-dev.yml`, `SpringAiConfig.java` |
| **核心思路** | 排查 Spring AI `ChatClient` 的模型端点配置；确认 LLM API base URL 和模型名称正确；增加连接测试（启动时验证） |
| **验收标准** | submit 后审查流程正常执行，不返回 404 |

#### P4-2 Embedding 模型方案

| 项 | 内容 |
|---|------|
| **涉及文件** | `RagServiceImpl.java`, `pom.xml`, `application.yml` |
| **核心思路** | 短期：保持纯 LLM fallback（已可用）；中期评估：通义千问 text-embedding-v3 / 智谱 embedding API / 本地 text2vec-base-chinese；Spring AI 的 Chroma VectorStore 接口抽象已就绪，切换成本低 |
| **验收标准** | RAG 检索在无 embedding 服务时自动 fallback 到 LLM，不报错 |

#### P4-3 合同文本多格式支持

| 项 | 内容 |
|---|------|
| **涉及文件** | `FileUtil.java`, `ContractController.java`, `web/src/views/Upload.vue` |
| **核心思路** | 后端：FileUtil 新增 Word(.docx) 解析（Apache POI）和纯文本(.txt) 直读；前端：上传组件支持 docx/txt 格式；新增"粘贴文本"输入方式（textarea + submit） |
| **验收标准** | 支持 PDF/Word/TXT 三种格式上传；粘贴文本可直接提交审查 |

#### P4-4 重试机制 UI 改进

| 项 | 内容 |
|---|------|
| **涉及文件** | `web/src/views/History.vue`, `web/src/components/SseProgress.vue` |
| **核心思路** | 失败任务显示"重试"按钮（已有后端接口 `POST /retry`）；点击后跳转到进度页 |
| **验收标准** | FAILED 状态任务可一键重试 |

#### P4-5 历史页进度体验优化

| 项 | 内容 |
|---|------|
| **涉及文件** | `web/src/views/History.vue`, 后端 `ContractController.java`（progress 端点） |
| **核心思路** | 未完成任务点击查看进度时：显示合同预览文本（已有 `/text` 端点）+ 当前状态 + 进度条 + 实时 SSE；而非仅显示"任务未完成" |
| **验收标准** | 未完成任务可看到合同文本和当前审查进度 |

#### P4-6 个人中心

加一项，个人中心可以自定义配置用户自己的api和key。

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `UserController.java`（或在 `AuthController` 扩展），`UserService`，`web/src/views/Profile.vue` |
| **核心思路** | 后端：GET /user/profile（用户名、剩余额度、注册时间）、PUT /user/profile（修改用户名）、POST /user/password（修改密码）；前端：新增个人中心页面 |
| **验收标准** | 用户可查看/编辑个人信息、修改密码、查看剩余额度 |

#### P4-7 额度显示修复

| 项 | 内容 |
|---|------|
| **涉及文件** | `ContractController.java`, `AuthController.java`（login 返回额度）, `web/src/stores/auth.js` |
| **核心思路** | login 响应中返回 reviewQuota；前端 store 保存额度；每次审查后更新显示 |
| **验收标准** | 登录后显示正确剩余额度；审查后额度实时更新 |

#### P4-8 法律法规知识库

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `LawController.java`, `LawService`, `LawMapper`, `Law` entity, `init.sql`（加表）, `web/src/views/LawLibrary.vue` |
| **核心思路** | 后端：法条 CRUD API；前端：法条管理页面（列表/新增/编辑/删除）；与 Chroma 向量库联动（CRUD 后自动 re-index） |
| **验收标准** | 用户可管理法条；新增法条后可被 RAG 检索到 |

#### P4-9 管理后台（独立模块）



| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `admin/` 模块或 `admin/` 前端目录，`SecurityConfig.java`（RBAC），`User` entity 加 role 字段 |
| **核心思路** | 引入 RBAC 角色模型（USER/ADMIN）；管理后台前端独立 SPA 或路由模块；功能：用户管理、任务管理、系统监控；参考 RuoYi 但不直接引入（过重） |
| **验收标准** | ADMIN 角色可访问管理后台；USER 角色被拒绝；CRUD 操作正常 |

加：管理员后台要有一个系统监控，把异常信息可视化，增强可读性。参考Ruoyi框架。

#### P4-10 前端对齐

确保前端功能与后端功能对齐。后端的功能前端都要有显示。

#### P4-11 测试验证


测试阶段4的所有模块，确保测试通过。

---



### Phase 5: 生产化部署 ⚪

> 目标: 可上线运行

#### P5-1 生产配置

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `application-prod.yml`, `docker-compose.prod.yml` |
| **核心思路** | prod profile: 关闭 debug 日志、开启 JSON 日志、配置生产密钥、限制文件上传大小；docker-compose.prod.yml: 去掉开发工具、加 restart policy、加健康检查 |
| **验收标准** | `SPRING_PROFILES_ACTIVE=prod` 可启动；所有配置通过环境变量注入 |

#### P5-2 CORS 生产域名

| 项 | 内容 |
|---|------|
| **涉及文件** | `application-prod.yml`, `SecurityConfig.java` |
| **核心思路** | prod CORS 仅允许实际部署域名；配置 `cors.allowed-origins=https://your-domain.com` |
| **验收标准** | 非白名单域名收到 CORS 拒绝 |

#### P5-3 前端构建集成

| 项 | 内容 |
|---|------|
| **涉及文件** | `Dockerfile`, `.dockerignore` |
| **核心思路** | 多阶段构建：Node 构建前端 -> Nginx 托管静态文件 + 后端 API 反向代理；或前端独立部署，后端纯 API 服务 |
| **验收标准** | `docker build` 一次完成前后端构建；访问容器 IP 直接可用 |

#### P5-4 日志持久化

| 项 | 内容 |
|---|------|
| **涉及文件** | `docker-compose.prod.yml`, `logback-spring.xml` |
| **核心思路** | 日志输出到 Docker volume；配置日志轮转（max-size、max-file）；可选：接入 ELK 或 Loki |
| **验收标准** | 服务重启后历史日志可查；单文件不超 100MB |

#### P5-5 数据备份

| 项 | 内容 |
|---|------|
| **涉及文件** | 新建 `scripts/backup.sh`, `docker-compose.prod.yml` |
| **核心思路** | MySQL 定时全量备份 + binlog 增量备份；MinIO 定期快照；备份脚本 cron 执行 |
| **验收标准** | 可从备份恢复数据；备份文件不超过保留天数 |

---

## 5. 文件变更总览

### Phase 1（安全与可靠性）

| 操作 | 文件 |
|------|------|
| 修改 | `application.yml`（密钥外部化） |
| 修改 | `application-dev.yml`（密钥外部化、CORS配置） |
| 修改 | `docker-compose.yml`（env_file、健康检查） |
| 新建 | `.env.example` |
| 修改 | `.gitignore`（加入 .env） |
| 修改 | `SecurityConfig.java`（CORS收紧） |
| 修改 | `JwtAuthenticationFilter.java`（移除URL传参） |
| 修改 | `AuthRequest.java`（输入校验） |
| 修改 | `ContractController.java`（参数校验、SSE鉴权） |
| 新建 | `ReviewResultHandler.java`（事务修复） |
| 修改 | `ReviewMessageListener.java`（手动ack、状态重置、调用ReviewResultHandler） |
| 修改 | `ContractServiceImpl.java`（额度扣减原子化） |
| 修改 | `SseServiceImpl.java`（超时+清理） |
| 修改 | `UserContext.java`（异步传递） |
| 修改 | `AgentOrchestratorImpl.java`（UserContext设置） |

### Phase 2（可观测性）

| 操作 | 文件 |
|------|------|
| 新建 | `MdcFilter.java` |
| 新建 | `AccessLogFilter.java` |
| 修改 | `pom.xml`（actuator依赖） |
| 修改 | `application.yml`（actuator配置、日志格式） |
| 新建 | `docker-compose.yml` 各服务 healthcheck |
| 修改 | `Dockerfile`（HEALTHCHECK） |
| 修改 | `GlobalExceptionHandler.java`（异常分级） |
| 修改 | `BusinessException.java`（HTTP状态码映射） |
| 修改 | `ErrorCode.java`（补齐、HTTP映射） |
| 修改 | `ContractServiceImpl.java`, `SseServiceImpl.java`, `AgentServiceImpl.java`（消除空catch） |

### Phase 3（工程化）

| 操作 | 文件 |
|------|------|
| 修改 | `TaskStatus.java`, `RiskLevel.java`（启用枚举） |
| 修改 | 所有使用字符串状态/等级的 entity 和 service |
| 新建 | `ClassifyResult.java`, `ScanResult.java`, `SummarizeResult.java` |
| 修改 | `AgentServiceImpl.java`（DTO返回） |
| 修改 | `AgentOrchestratorImpl.java`（DTO使用） |
| 删除 | `LLMReviewService.java`, `WebMvcConfig.java` |
| 删除或合并 | `QuotaRollbackHandler.java` |
| 修改 | `ContractReviewApplication.java`（去重复注解） |
| 修改 | `R.java`（requestId全量返回） |
| 修改 | `pom.xml`（springdoc依赖） |
| 修改 | `SecurityConfig.java`（swagger放行） |
| 各Controller | 加OpenAPI注解 |
| 修改 | `OperationLogAspect.java`（ObjectMapper） |
| 前端新建 | `.eslintrc.js`, `.prettierrc` |
| 修改 | `web/package.json`（lint脚本） |
| 修改 | `.github/workflows/ci.yml`（lint fail on error） |
| 新建 | `application-ci.yml` |

### Phase 4（功能）

| 操作 | 文件 |
|------|------|
| 修改 | `FileUtil.java`（Word/TXT解析） |
| 修改 | `web/src/views/Upload.vue`（多格式+粘贴） |
| 修改 | `web/src/views/History.vue`（重试按钮、进度体验） |
| 新建 | `web/src/views/Profile.vue` |
| 新建/修改 | `UserController.java`, `UserService` |
| 新建 | `LawController.java`, `LawService`, `Law` entity |
| 修改 | `init.sql`（law表） |
| 新建 | `web/src/views/LawLibrary.vue` |
| 新建 | 管理后台模块（Phase 4 后期） |

---

## 6. 风险与权衡

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| MQ 改手动 ack 增加复杂度 | 中 | ReviewResultHandler 封装事务逻辑，减少 listener 复杂度 |
| 枚举改造涉及面广 | 中 | 分批改，先改 entity 字段，再改 service 引用，最后清理字面量 |
| SSE 鉴权可能影响前端 | 低 | 前端已传递 token，SSE 端点从 SecurityContext 获取 userId |
| 前端 token 存储改造风险 | 中 | 如 httpOnly cookie 改动过大，先加 CSP header + XSS 防护，后续再改 |
| 管理后台范围蔓延 | 高 | 严格限定在 Phase 4 后期，核心流程稳定后再做 |

---

## 附录: TODO.md 条目映射

| TODO 条目 | 映射任务 | 阶段 |
|-----------|----------|------|
| LLM API 404 | P4-1 | Phase 4 |
| 合同文本支持word/txt/粘贴 | P4-3 | Phase 4 |
| 完善重试机制 | P4-4 | Phase 4 |
| 历史页进度体验 | P4-5 | Phase 4 |
| 剩余额度没扣减 | P4-7 + P1-7 | Phase 1+4 |
| 没有个人中心 | P4-6 | Phase 4 |
| 管理后台 | P4-9 | Phase 4（后期） |
| embedding模型替代 | P4-2 | Phase 4 |
| 法律法规库CRUD | P4-8 | Phase 4 |
| 硬编码密钥 | P1-1 | Phase 1 |
| CORS全开 | P1-2 | Phase 1 |
| JWT URL传参 | P1-3 | Phase 1 |
| 前端token localStorage | P3-7 | Phase 3 |
| 输入长度无校验 | P1-4 | Phase 1 |
| RiskItem N+1插入 | P1-7（批量） | Phase 1 |
| SSE连接无清理 | P1-8 | Phase 1 |
| 缺Actuator | P2-3 | Phase 2 |
| Docker无HEALTHCHECK | P2-4 | Phase 2 |
| 无集成测试 | （后续专项） | -- |
| CI lint可选 | P3-7 | Phase 3 |
| 空类未清理 | P3-3 | Phase 3 |
| 异常被吞 | P2-7 | Phase 2 |
| JSON字符串拼接 | P3-6 | Phase 3 |
| 前端无ESLint | P3-7 | Phase 3 |
| 无数据库迁移工具 | （确认暂不引入） | -- |
| 无障碍/P3优化 | （不在本次范围） | -- |

---

## 版本历史

### V1.1 · 2026-09-01

- 在 ai/auto/redesign 分支分批提交 14 次本地改动
- 新增 OperationLogDto / SystemMonitorDto
- AdminController 与 AdminService 增加 /monitor、/operations 接口
- AdminServiceImpl 实现 JVM 监控与操作日志查询
- AuthResponse 与 AuthServiceImpl 增加 role 字段返回
- application-example.yml 增加可选的 Ollama embedding 配置
- db/init.sql 将 summary / content 升级为 MEDIUMTEXT
- 前端 admin.js 增加 getSystemMonitor、getOperationLogs
- 前端 interceptor.js 兼容 refresh token 响应结构
- 前端 router 增加 /403、404 通配、/monitor 路由及 ADMIN 守卫
- 新增 Forbidden.vue / NotFound.vue / Monitor.vue 三个页面
- Layout.vue ADMIN 角色新增系统监控导航
- Login.vue / Register.vue 增加淡入动画
- Upload.vue 预览区增加滑入过渡
- .gitignore 忽略 CLAUDE.md 与 local/ 目录
- 计划合并到 develop（--no-ff 产生独立合并提交）后创建 work 分支

### V1.0 · 2026-08-17

- 初版重设计方案，覆盖 Phase 1-5
- Phase 1 安全加固 / Phase 2 可观测性 / Phase 3 工程化 / Phase 4 功能 / Phase 5 生产化部署
