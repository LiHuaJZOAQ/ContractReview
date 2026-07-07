# 智能合同风险审查系统 — 验收检查清单

## Phase 0：环境搭建

### 开发环境

- [ ] JDK 17 安装并配置 `JAVA_HOME`
- [ ] Maven 安装并配置 `MAVEN_HOME`，阿里云镜像
- [ ] IntelliJ IDEA 项目导入可运行
- [ ] Docker Desktop 安装并运行
- [ ] MySQL 8.0 容器启动，端口 3306 可连接
- [ ] Redis 容器启动，端口 6379 可连接
- [ ] MinIO 容器启动，端口 9000/9001 可访问控制台
- [ ] Spring Boot 3 空项目启动无报错
- [ ] application-dev.yml / application-prod.yml 多环境配置

### 项目结构

- [ ] controller / service / mapper / config / model 包划分
- [ ] MyBatis-Plus 配置（分页插件、乐观锁插件）
- [ ] 统一响应体 `R<T>`（code / message / data / timestamp）
- [ ] 全局异常处理器 `@RestControllerAdvice`

---

## Phase 1：MVP 核心链路

### 用户模块

- [ ] **user 表建表**
  - [ ] 字段：id, username, password_hash, review_quota(DEFAULT 10), version, created_at, updated_at
  - [ ] username 唯一索引
- [ ] **注册接口 `POST /api/v1/auth/register`**
  - [ ] 密码 BCrypt 加密存储
  - [ ] 用户名不可重复
  - [ ] 注册成功赠送 10 次审查配额
- [ ] **登录接口 `POST /api/v1/auth/login`**
  - [ ] 校验用户名 + 密码
  - [ ] 返回 JWT Token（2h 过期）
  - [ ] 错误密码返回 401
- [ ] **JWT 拦截器**
  - [ ] Token 缺失返回 401
  - [ ] Token 过期返回 401
  - [ ] Token 被篡改返回 401
  - [ ] 放行 `/auth/**` 路径
  - [ ] 从 Token 提取 userId 注入请求上下文

### 文件上传与解析

- [ ] **MinIO 配置**
  - [ ] Bucket 自动创建
  - [ ] 文件上传工具类可用
- [ ] **文件格式校验**
  - [ ] 仅允许 PDF（Phase 1）
  - [ ] 文件大小 ≤ 20MB
  - [ ] 超限返回错误码 1001(格式) / 1002(大小)
- [ ] **PDFBox 文本提取**
  - [ ] PDF 文本正确提取
  - [ ] 空 PDF / 扫描件 PDF 返回合理错误
- [ ] **review_task 表建表**
  - [ ] 字段完整（id, user_id, file_name, file_size, preview_text, file_url, status, progress, error_msg, total_chunks, reviewed_chunks, created_at, completed_at, updated_at）
  - [ ] status 枚举：PENDING / PARSING / RETRIEVING / REVIEWING / SUMMARIZING / SUCCESS / FAILED
  - [ ] 索引：user_id, status, created_at
- [ ] **上传接口 `POST /api/v1/contract/upload`**
  - [ ] 解析 → 脱敏 → 存储 → 返回 `{taskId, previewText}`
  - [ ] 创建的任务状态为 PENDING
  - [ ] 此时不扣减配额

### 隐私脱敏

- [ ] **正则脱敏引擎**
  - [ ] 姓名脱敏（2-4 个中文字符替换为 `***`）
  - [ ] 身份证号脱敏（18 位数字 + X/x）
  - [ ] 手机号脱敏（11 位数字，1 开头）
  - [ ] 银行卡号脱敏（16-19 位数字）
  - [ ] 边界情况：空字符串不脱敏
  - [ ] 边界情况：无敏感信息原样返回
  - [ ] 边界情况：全敏感信息全部替换
- [ ] **脱敏集成到上传流程**
  - [ ] 上传接口返回的 previewText 已脱敏
  - [ ] 原始文本不持久化

### LLM 审查

- [ ] **Spring AI 配置**
  - [ ] ChatClient Bean 可注入
  - [ ] API Key 配置在 application.yml
- [ ] **审查 Prompt 模板**
  - [ ] 输出结构化 JSON（风险项列表）
  - [ ] 包含风险等级、类型、描述、修改建议
- [ ] **提交接口 `POST /api/v1/contract/{taskId}/submit`**
  - [ ] Lua 原子扣减配额（余额不足返回 1003）
  - [ ] 任务状态变更为 PARSING
  - [ ] 对非 PENDING 状态的任务拒绝操作（返回 1005）
- [ ] **单次 LLM 审查 Service**
  - [ ] 调用 LLM 返回审查结果
  - [ ] 结果 JSON 解析
  - [ ] LLM 调用失败可重试
- [ ] **risk_item 表建表**
  - [ ] 字段：id, task_id, clause_index, clause_content, risk_level, risk_type, description, suggestion, related_laws, created_at
  - [ ] 索引：task_id, (task_id, risk_level)
- [ ] **review_report 表建表**
  - [ ] 字段：id, task_id(UNIQUE), summary, risk_count_high, risk_count_medium, risk_count_low, report_json, pdf_url, created_at
  - [ ] task_id 唯一索引

### 查询与报告

- [ ] **任务状态查询 `GET /api/v1/contract/{taskId}/status`**
  - [ ] 返回 taskId + status + progress
  - [ ] 不存在的 taskId 返回 1004
- [ ] **审查报告查询 `GET /api/v1/contract/{taskId}/report`**
  - [ ] 返回结构化报告（summary + risks[] + riskCount）
  - [ ] 任务未完成返回 1005
- [ ] **历史记录 `GET /api/v1/contract/history`**
  - [ ] 分页返回（page / size 参数）
  - [ ] 按 created_at 降序
  - [ ] 只返回当前用户的任务

### 异步化与状态机

- [ ] **@Async 异步执行**
  - [ ] 提交后立即返回，审查后台执行
  - [ ] 异步线程池配置
- [ ] **简化状态机**
  - [ ] PENDING → PROCESSING → SUCCESS / FAILED
  - [ ] 状态变更持久化到 DB
- [ ] **前端轮询**
  - [ ] 前端可轮询 `GET /status` 获取进度

### 联调完善

- [ ] **全局异常处理**
  - [ ] 统一响应格式 `{code, message, timestamp, requestId}`
  - [ ] 业务错误码表（1001-1009）
- [ ] **异常场景**
  - [ ] LLM 调用失败 → 任务标记 FAILED
  - [ ] PDF 解析失败 → 任务标记 FAILED
  - [ ] 配额不足 → 返回 1003
- [ ] **全链路集成测试**
  - [ ] 注册 → 登录 → 上传 → 提交 → 轮询 → 报告
- [ ] **单元测试覆盖率 ≥ 60%**

### Phase 1 里程碑

- [ ] **M1 — MVP 全链路可运行**：上传 → 脱敏 → LLM 审查 → 报告可演示
- [ ] Phase 1 快速启动清单 15 天任务全部完成

---

## Phase 2：功能增强

### RAG 法条检索

- [ ] **Chroma 向量数据库**
  - [ ] Docker 启动 Chroma 容器
  - [ ] Spring AI Chroma 集成配置
- [ ] **法条向量化入库**
  - [ ] 《民法典》《劳动合同法》等核心法条文本准备
  - [ ] Embedding 向量化存储
  - [ ] 入库脚本可重复执行（幂等）
- [ ] **语义分块**
  - [ ] 按条款/段落分块
  - [ ] 保留上下文信息
- [ ] **混合检索 Service**
  - [ ] 本地 Chroma 余弦检索（阈值 0.75 可配置）
  - [ ] 未命中时网络搜索 flk.npc.gov.cn 兜底
  - [ ] 网络结果缓存 TTL 7d

### Multi-Agent 编排

- [ ] **Agent A：分类定调**
  - [ ] 输出合同类型（租房/劳动合同/外包协议 等）
  - [ ] 输出用户立场（承租方/出租方/劳动者 等）
  - [ ] 输出审查策略
- [ ] **Agent B：风险扫描**
  - [ ] 基于 Chunk 逐条扫描
  - [ ] Semaphore(10) 并发控制
  - [ ] 输出风险点（等级、类型、描述、建议）
  - [ ] 关联对应法条
- [ ] **Agent C：报告汇总**
  - [ ] 汇总风险统计
  - [ ] 生成审查总结
- [ ] **Agent 编排器**
  - [ ] A → 分块 → RAG → B(并发) → C 自动流转
  - [ ] 异常时中断并回滚状态

### 异步任务与 SSE

- [ ] **RabbitMQ**
  - [ ] 容器启动
  - [ ] 队列配置：contract.review.queue
  - [ ] 死信队列：contract.review.dlx
  - [ ] prefetch = 10
- [ ] **SSE 推送**
  - [ ] SseEmitter 连接管理（ConcurrentHashMap）
  - [ ] 推送事件类型：progress / complete / error
  - [ ] SSE 端到端延迟 ≤ 3s
  - [ ] 连接断开指数退避重连机制
- [ ] **完整状态机**
  - [ ] PENDING → PARSING → RETRIEVING → REVIEWING → SUMMARIZING → SUCCESS / FAILED
  - [ ] 非法状态转换拒绝
  - [ ] 超时熔断（各阶段阈值）
  - [ ] 指数退避重试最多 3 次
- [ ] **MQ 消费者**
  - [ ] 消费消息 → 驱动状态机 → SSE 推送进度
  - [ ] 成功 ACK / 失败 NACK 入 DLX
- [ ] **手动重试 `POST /api/v1/contract/{taskId}/retry`**
  - [ ] 仅 FAILED 状态可重试
  - [ ] 重试后重新投递 MQ

### Phase 2 里程碑

- [ ] **M2 — 具备完整 RAG + Multi-Agent + 异步 SSE 能力**

---

## Phase 3：架构完善

### 安全加固

- [ ] **Spring Security 完整鉴权链**
  - [ ] SecurityFilterChain 配置
  - [ ] 权限路径匹配
  - [ ] CSRF 配置
- [ ] **Token 刷新**
  - [ ] `POST /auth/refresh` 接口
  - [ ] Refresh Token 30d Redis 存储
  - [ ] Token 自动续期
- [ ] **Redis 滑动窗口限流**
  - [ ] Lua 脚本原子实现
  - [ ] 超限返回错误码 1008
- [ ] **操作审计**
  - [ ] AOP 切面记录关键操作（注册/上传/提交/查看报告/重试）
  - [ ] operation_log 表写入
  - [ ] IP 地址记录

### 前端

- [ ] **Vue 3 + Element Plus 项目**
  - [ ] Axios 配置（拦截器、Token 携带）
  - [ ] 路由配置
  - [ ] Vuex / Pinia 状态管理
- [ ] **注册/登录页面**
  - [ ] 表单校验
  - [ ] Token 持久化存储
- [ ] **文件上传页面**
  - [ ] 拖拽上传
  - [ ] 脱敏预览文本展示
  - [ ] 确认提交按钮
- [ ] **SSE 进度条**
  - [ ] EventSource 连接
  - [ ] 阶段文字提示
  - [ ] 进度百分比展示
- [ ] **审查报告页面**
  - [ ] 风险等级标签（HIGH 红色 / MEDIUM 黄色 / LOW 蓝色）
  - [ ] 风险分类
  - [ ] 修改建议展示
  - [ ] 关联法条链接
- [ ] **历史记录页面**
  - [ ] 分页列表
  - [ ] 任务状态筛选

### 功能完善

- [ ] **Word 解析**
  - [ ] Apache POI 解析 .docx
  - [ ] Word 文件上传支持
- [ ] **NLP 脱敏增强**
  - [ ] HanLP / LLM NER 实体识别
  - [ ] 脱敏准确率 ≥ 90%
- [ ] **法条版本管理**
  - [ ] 法条数据库版本号
  - [ ] 增量更新脚本
- [ ] **MinIO 生命周期管理**
  - [ ] 1h 未提交的 PENDING 任务清理
  - [ ] 24h 后 preview_text 置空
- [ ] **MySQL 索引优化**
  - [ ] 慢查询优化
  - [ ] 主从备份配置
  - [ ] 每日自动备份脚本

### 测试与质量

- [ ] **JMeter 压力测试**
  - [ ] 并发 ≥ 100 用户
  - [ ] 审查耗时 ≤ 120s (P95)
  - [ ] SSE 延迟 ≤ 3s (P99)
  - [ ] REST API 响应 ≤ 500ms (P95)
  - [ ] 错误率 ≤ 0.1%
  - [ ] 资源使用率 CPU ≤ 70%、内存 ≤ 80%
- [ ] **JaCoCo 覆盖率 ≥ 80%**
- [ ] **SpotBugs 静态分析通过**
- [ ] **CI/CD 流水线**
  - [ ] GitHub Actions / GitLab CI 配置
  - [ ] 自动构建 + 单元测试
  - [ ] 代码质量门禁

### Phase 3 里程碑

- [ ] **M3 — 系统完整交付，生产就绪**
- [ ] 全链路 HTTPS
- [ ] 系统可用性 ≥ 99.5%
- [ ] 所有验收标准（A1-A6, B1-B4）通过

---

## 全量验收标准

### 功能验收（A1-A6）

| ID | 标准 | 验证方法 | 结果 |
|----|------|----------|:----:|
| A1 | 支持 PDF/Word 上传 | 上传 3 种格式文件（.pdf, .doc, .docx） | ☐ |
| A2 | 敏感信息脱敏 | 上传含姓名/身份证/手机号/银行卡号的合同，预览文本已替换 | ☐ |
| A3 | 审查结果结构化展示 | 报告包含风险等级、类型、描述、建议、法条引用 | ☐ |
| A4 | 实时进度反馈 | SSE 推送各阶段事件，前端显示进度百分比 | ☐ |
| A5 | 历史记录可查 | 历史列表分页，按时间排序 | ☐ |
| A6 | 失败任务可重试 | FAILED 任务调用 retry 后重新执行 | ☐ |

### 非功能验收（B1-B4）

| ID | 标准 | 目标值 | 验证方法 | 结果 |
|----|------|--------|----------|:----:|
| B1 | 合同审查耗时 | 20 页以内 ≤ 120s | JMeter 混合场景 | ☐ |
| B2 | 并发支持 | ≥ 100 用户同时提交，系统正常 | JMeter 阶梯加压 | ☐ |
| B3 | 接口限流 | 超限请求返回 429 | 模拟超限请求 | ☐ |
| B4 | 隐私脱敏 | P0 正则脱敏命中率 ≥ 90%（基于测试集验证） | JUnit ParameterizedTest | ☐ |

### 安全验收（C1-C4）

| ID | 标准 | 验证方法 | 结果 |
|----|------|----------|:----:|
| C1 | 未登录不可访问受保护接口 | 不带 Token 请求受保护接口返回 401 | ☐ |
| C2 | Token 过期退出的正确行为 | 过期 Token 请求返回 401，前端跳转登录页 | ☐ |
| C3 | 配额不可超扣 | 用尽配额后再提交返回 1003，余额不为负 | ☐ |
| C4 | 隐私数据不泄露 | 原始文本不上传 MinIO，不持久化 | ☐ |

---

## 代码质量门禁

- [ ] 单元测试覆盖率 ≥ 80%（Phase 3）/ ≥ 60%（Phase 1）
- [ ] SpotBugs / Checkstyle 无 Critical 级别问题
- [ ] API 统一响应格式（R<T>）
- [ ] 日志分级（ERROR / WARN / INFO / DEBUG）
- [ ] 敏感信息不出现在日志中
- [ ] SQL 使用参数绑定，无拼接
- [ ] 跨域配置正确
- [ ] 配置文件无硬编码（使用 @ConfigurationProperties）
