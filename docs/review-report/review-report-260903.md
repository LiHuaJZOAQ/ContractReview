# 项目下一步改进方向（探索报告 + 分批方案）

> 来源：2026-09-03 全面代码探索后产出。共发现 15 个待办点，按价值/工作量比排序。
> 总工作量约 7-9 小时，分 4 个核心批次 + 1 个可选部署批。

---

## 项目当前状态（探索结论）

### 后端规模
- 5 个 Controller（Admin/Auth/Contract/Law/User）
- 11 个 Service（5 接口 + 6 辅助）
- 8 个 Mapper，6 张业务表
- 18 个测试文件

### 前端规模
- 12 个 Vue 页面 + 2 个组件（SseProgress/ThemeToggle）
- 测试覆盖：auth/contract/interceptor/router/store/History/Login/Report/Upload/SseProgress
- **缺口**：Profile/AdminDashboard/Monitor/LawLibrary/Register 视图无测试

### 已完成模块
JWT + refresh 旋转 + 重用检测、RateLimitFilter、OperationLogAspect、5 状态机、Agent A/B/C pipeline、RAG 双层（Chroma+LLM fallback）、异步任务（@Async + Semaphore(10)）、Logback 分环境（dev 一次启动一个文件 / prod 按天滚动）、Dockerfile 多阶段 + docker-compose 5 中间件、SSE 改用 JSON Lines + ReadableStream

### TODO/FIXME 注释扫描
`grep -rn "TODO\|FIXME\|XXX\|HACK"` 在 `src/main` 和 `web/src` 中**无任何结果**——开发期遗留标记已清理完毕。

---

## 待办清单（按价值/工作量比排序）

### P0：安全漏洞（2 项，必须修）

| # | 问题 | 位置 | 工作量 |
|---|------|------|--------|
| 1 | **AuthServiceImpl.refresh 反逻辑**：`setIfAbsent` 返回 false 表示 reuse 已发生，但代码继续签发新 token，等于放行重放攻击 | `src/main/java/com/contractreview/service/impl/AuthServiceImpl.java:80` | 1h |
| 2 | **LawController 越权写入**：POST/PUT/DELETE /api/v1/law 仅 `authenticated()`，普通用户能增删法条库 | `src/main/java/com/contractreview/controller/LawController.java:38-71` | 1h |

### P1：重要 Bug（3 项）

| # | 问题 | 位置 | 工作量 |
|---|------|------|--------|
| 3 | **RateLimitFilter 不排除 SSE**：SSE 长连接 3 秒一次心跳，30/分钟限流会审查中途 429 误杀 | `src/main/java/com/contractreview/security/RateLimitFilter.java:60` | 30min |
| 4 | **MinIO 上传在事务内**：`@Transactional` 内同步 putObject，20MB 文件长时间占 DB 连接 | `src/main/java/com/contractreview/service/impl/ContractServiceImpl.java:82` | 1h |
| 5 | **MinIO bucket init 失败仅 warn**：启动时 MinIO 未就绪 → 上传 500 | `src/main/java/com/contractreview/service/impl/ContractServiceImpl.java:71` | 1h |

### P2：性能优化（3 项）

| # | 问题 | 位置 | 工作量 |
|---|------|------|--------|
| 6 | **RAG 缓存 hash 碰撞**：`String.hashCode()` 32 位 + LLM 截断 1000 字符前会碰撞 | `src/main/java/com/contractreview/service/impl/RagServiceImpl.java:53` | 30min |
| 7 | **Agent B 并行 join 串行等**：`futures.stream().flatMap(f -> f.get())` 是并行启动后串行等 | `src/main/java/com/contractreview/service/impl/AgentOrchestratorImpl.java:109-122` | 30min |
| 8 | **SSE emitter 内存泄漏**：客户端断网无 onError 回调（TCP keep-alive 超时），需 `@Scheduled` 清理 | `src/main/java/com/contractreview/service/impl/SseServiceImpl.java:19` | 1h |

### P3：部署运维（4 项）

| # | 问题 | 位置 | 工作量 |
|---|------|------|--------|
| 9 | **Dockerfile 无 curl**：healthcheck 失败 | `Dockerfile:29` | 15min |
| 10 | **docker-compose 无 depends_on: service_healthy**：Spring 启动会因中间件未就绪失败 | `docker-compose.yml:14-83` | 15min |
| 11 | **Actuator 缺 prometheus 端点**：无 micrometer-registry-prometheus 依赖 | `application.yml:24` | 1h |
| 12 | **优雅停机**：缺 `server.shutdown=graceful`，SIGTERM 时正在执行的 Agent 审查会被截断 | `application.yml` | 15min |

### P4：测试覆盖（3 类）

| # | 缺口 | 工作量 |
|---|------|--------|
| 13 | 5 个 Controller 全无 MockMvc 测试 | 4h |
| 14 | Profile/AdminDashboard/Monitor/LawLibrary/Register 视图无测试 | 3h |
| 15 | RAG 双层降级场景无测试 + AgentService JSON 提取边界无测试 | 2h |

---

## 分批实施方案

按用户偏好"分批不要太碎"，每批 1-2 小时工作量。

### 批次 1：安全加固（P0，2h）

**目标**：堵住 AuthService 重放攻击和 LawController 越权写入两个安全漏洞。

**改动文件**：
- `src/main/java/com/contractreview/service/impl/AuthServiceImpl.java:80-89` -- refresh 方法反逻辑修正
- `src/main/java/com/contractreview/controller/LawController.java:38-71` -- POST/PUT/DELETE 加管理员校验

**改动要点**：
1. AuthServiceImpl 第 80 行：修正 `firstReuse == false` 分支——Redis 中已有 reuse 标记说明确实被重用过，此时**拒绝签发**并吊销该用户所有 refresh token（扫描 `refresh:reuse:{userId}*` 批量 delete）。在正常签发前用 `setIfAbsent` 设置新的 reuse key 短 TTL 作并发去重。
2. LawController 在 write 方法（createLaw/updateLaw/deleteLaw/reindexLaw/toggleLaw）上加 `@PreAuthorize("hasRole('ADMIN')")`。Read 方法（getLaw/listLaws）保持 `authenticated()`。确认 SecurityConfig 未把 `/api/v1/law/**` 误放到白名单。

**验证**：
- 手动：用同一 refresh token 连续调两次 `/auth/refresh`，第二次应返回 401 且该用户后续所有 refresh 均失效
- 手动：以非 ADMIN 用户 POST/PUT/DELETE `/api/v1/law/*`，应返回 403
- 自动化：编写 `AuthServiceTest.testRefreshReuseAttack` 和 `LawControllerSecurityTest`

**风险**：低。变更只影响安全分支，不影响正常登录/刷新流程。

---

### 批次 2：SSE 与上传事务修复（P1，1.5h）

**目标**：修复 RateLimitFilter 对 SSE 长连接的误限流，以及 ContractServiceImpl 在事务内同步上传 MinIO 的连接池占用问题。

**改动文件**：
- `src/main/java/com/contractreview/security/RateLimitFilter.java:58-61` -- `shouldNotFilter` 增加 SSE 排除规则
- `src/main/java/com/contractreview/service/impl/ContractServiceImpl.java:75-95` -- 拆分 MinIO 上传出事务

**改动要点**：
1. RateLimitFilter 第 59 行 `shouldNotFilter` 增加正则匹配：`path.matches("^/api/v1/contract/\\d+/progress$")` 返回 true。SSE 心跳约 3 秒一次，正常审查任务 60-120 秒，不限流不会造成滥用。
2. ContractServiceImpl 拆为两层：`uploadTransactional`（事务内，只做 PDF 解析 + 写 ReviewTask，fileUrl 暂存 `pending:{taskId}`）+ `uploadMinioAsync`（事务提交后通过 `TransactionSynchronizationManager.registerSynchronization` 在 afterCommit 钩子中异步上传 MinIO，更新 task.fileUrl）。MinIO 失败时 task status 标为 UPLOAD_FAILED，可重试。也可引入 `@Async("minioExecutor")` + `CompletableFuture` 回调。

**验证**：
- SSE：上传合同后保持 DevTools Network 面板观察 progress 连接，确认不被 429 中断
- 上传：上传 20MB+ PDF，观察 HikariCP 连接池使用（actuator/metrics），上传期间不应占满连接；MinIO 失败时 task 应为 UPLOAD_FAILED 状态

**风险**：中。事务拆分后需处理"MinIO 成功但 DB 更新失败"场景，建议 async 回调加 try-catch 标记 task 失败，不让用户看到"上传成功但无文件"。

---

### 批次 3：MinIO 启动鲁棒性 + SSE 内存防护（P1+P2，1.5h）

**目标**：MinIO 启动初始化改为阻塞重试（避免运行时 500），SseService 加上定时清理防止客户端断网后 emitter 长期残留。

**改动文件**：
- `src/main/java/com/contractreview/service/impl/ContractServiceImpl.java:62-73` -- MinIO bucket init 改造（或新增 `MinioHealthChecker`）
- `src/main/java/com/contractreview/service/impl/SseServiceImpl.java:19` -- 增加 `@Scheduled` 清理任务

**改动要点**：
1. MinIO 初始化：将 `@PostConstruct` 中 bucket 检查改为独立的 `ApplicationRunner`（或 `CommandLineRunner`），在 try/catch 中重试 3 次、间隔 2 秒。若仍失败则 ERROR 阻断应用启动（throw `IllegalStateException`），由 docker-compose restart 策略兜底。
2. SseServiceImpl 在 `emitters` 之上增加 `@Scheduled(fixedRate = 60000)` 方法 `cleanupStaleEmitters`：用 `Map<Long, Instant> lastActiveAt` 记录每个 emitter 最近 send 时间，超过 10 分钟无活动就 remove。或者更简单：尝试 send 心跳 comment，IOException 则 remove。

**验证**：
- MinIO：先停掉 MinIO 容器再启动应用，观察启动日志是否阻塞重试 3 次后报错退出
- SSE 内存：jmap 或 actuator metrics 观察 `emitters.size()`。开启 SSE 后关闭浏览器，等待 10 分钟确认 entry 被自动清除

**风险**：中。SSE 清理任务若误删活跃 emitter 会"前端断流"，建议先仅做日志告警（`emitters.size > 100` 时 WARN），下一轮再强制清理。

---

### 批次 4：性能优化 -- RAG 缓存 + Agent B 并行（P2，1h）

**目标**：RAG 缓存用 SHA-256 避免碰撞，Agent B 多 chunk 并行扫描改用 `CompletableFuture.allOf` 真正释放并行收益。

**改动文件**：
- `src/main/java/com/contractreview/service/impl/RagServiceImpl.java:53` -- 改用 SHA-256
- `src/main/java/com/contractreview/service/impl/AgentOrchestratorImpl.java:109-122` -- 改 `allOf().join()`

**改动要点**：
1. RagServiceImpl 第 53 行将 `chunkContent.hashCode()` 替换为 `MessageDigest.getInstance("SHA-256").digest(chunkContent.getBytes(UTF_8))` 取前 16 字节转 hex（32 字符）。可抽 `HashUtil.sha256Short(content)` 静态工具复用。
2. AgentOrchestratorImpl 第 109-122 行：`futures.stream().flatMap(f -> f.get())` 改为先 `CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join()`，再 `futures.stream().flatMap(f -> f.join().stream())`。所有 future 等 IO 期间真正并行而非按 list 顺序串行 get。

**验证**：
- RAG：单元测试构造两组不同 chunk 但同 `hashCode()` 的输入，确认旧 hash 碰撞在 SHA-256 下不复现
- Agent B：上传含 20+ chunk 的大合同，对比改前改后 Agent B 阶段耗时（应从 sum 降为 max）

**风险**：低。两者都是纯逻辑修改，向后兼容。RAG 缓存 key 变化会导致短时间命中率降为 0，下次自动重填。

---

### 批次 5（可选）：部署运维补齐（P3，1.5h）

**目标**：Dockerfile healthcheck 真正生效，docker-compose 启动顺序可控，Actuator 暴露 prometheus，优雅停机。

**改动文件**：
- `Dockerfile:29` -- 加 `apk add curl` 或换 `wget`
- `docker-compose.yml:14-83` -- 加 `depends_on: condition: service_healthy`
- `application.yml:24` -- 加 `server.shutdown=graceful`
- `pom.xml` + `application.yml` -- 加 micrometer-registry-prometheus 依赖和 endpoint 暴露

**风险**：低。纯配置变更，验证后无回滚风险。

---

## 实施依赖与并行建议

| 批次 | 优先级 | 工作量 | 依赖 | 可并行 |
|------|--------|--------|------|--------|
| 1 | P0 | 2h | 无 | — |
| 2 | P1 | 1.5h | 依赖批次 1 的 SecurityConfig 微调 | 否 |
| 3 | P1+P2 | 1.5h | 独立 | 可与批次 2 并行 |
| 4 | P2 | 1h | 独立 | 可最后做 |
| 5（可选） | P3 | 1.5h | 独立 | 可任意时 |

P4（测试覆盖）建议作为每批次提交时的配套任务，每个批次加 1-2 个核心测试用例。

---

## 不在本批方案中的项

| 项 | 原因 |
|---|---|
| Semaphore 10 个并发 | 硬编码但不影响功能，建议加 `@Value` 配置化即可，单独小批 |
| OperationLogAspect 异常吞噬 | 仅 warn，生产环境可加异步队列，独立批 |
| LawService removeLawFromChroma 1000 条限制 | 仅删除可靠性问题，优先级低 |
| ReviewTask 软删除 / OperationLog 清理 | 数据治理，独立批 |
| Profile/AdminDashboard 等视图测试 | 单独批次 |
| 国际化 i18n | 大改动，不在当前范围 |
| 备份策略 | 运维侧独立批 |

---

## 关键文件清单（按出现频次）

| 路径 | 涉及批次数 |
|------|------------|
| `src/main/java/com/contractreview/service/impl/AuthServiceImpl.java` | 1 |
| `src/main/java/com/contractreview/controller/LawController.java` | 1 |
| `src/main/java/com/contractreview/security/RateLimitFilter.java` | 2 |
| `src/main/java/com/contractreview/service/impl/ContractServiceImpl.java` | 2, 3 |
| `src/main/java/com/contractreview/service/impl/SseServiceImpl.java` | 3 |
| `src/main/java/com/contractreview/service/impl/RagServiceImpl.java` | 4 |
| `src/main/java/com/contractreview/service/impl/AgentOrchestratorImpl.java` | 4 |
| `Dockerfile` | 5 |
| `docker-compose.yml` | 5 |
| `application.yml` | 5 |
| `pom.xml` | 5 |
