# 基于 Multi-Agent 与 RAG 的智能合同风险审查系统 — 需求分析文档

## 修订记录

| 版本 | 日期 | 作者 | 修订内容 |
|------|------|------|----------|
| V1.0 | 2026-07-05 | AI + 人工审查 | 初始版本 |
| V1.1 | 2026-07-05 | AI + 人工审查 | 术语补充、架构描述修正、F1.2/F1.3 优先级调整、SSE 事件统一、状态机回退路径补充 |
| V1.2 | 2026-07-05 | AI + 人工审查 | 上传/提交流程拆分、状态机补齐 RETRIEVING/SUMMARIZING、SSE 事件类型区分、数据模型补充索引 |
| V1.3 | 2026-07-05 | AI + 人工审查 | F1.4 优先级修正、工作流图补充 RAG 链路、超时熔断与临时文件清理、SSE 重连说明、并发控制 |
| V1.4 | 2026-07-05 | AI + 人工审查 | 补充 F3.x 编号、统一 taskId 术语、review_task 增加 completed_at、progress 取值范围统一 |
| V1.5 | 2026-07-06 | AI + 人工审查 | 脱敏正则增强（姓名上下文限定、身份证日期校验、银行卡前缀匹配）；review_task 增加 preview_text；技术选型收敛（Spring AI、Chroma、MinIO）；Agent 数据契约补充；状态机 REVIEWING 重试路径；配额与审计日志方案补充；文档压缩去冗余 |
| V1.6 | 2026-07-06 | AI + 人工审查 | 新增第 12 章技术实施分级指南：Phase 0-3 分阶段实施路径、各模块分级方案、Phase 1 快速启动清单、技术学习优先级 |
| V1.7 | 2026-07-06 | AI + 人工审查 | 技术错误修复：姓名正则拆分避免 lookbehind 长度不一致、补充 Token 刷新端点、Redis DECR 负数防护说明、SSE 推送触发机制补充、preview_text 清理时机明确、Agent B 并发与 MQ prefetch 关系补充、flk.npc.gov.cn 无 API 修正 |
| V1.8 | 2026-07-06 | AI + 人工审查 | preview_text 改为 MEDIUMTEXT；补充 Refresh Token 存储方案（Redis，30 天过期）；提交流程同步 Lua 原子判断；register 接口补充 refreshToken 返回；验收标准 B4 改为命中率 ≥ 90% |
| V1.9 | 2026-07-08 | AI + 人工审查 | 新增 F1.6 可选脱敏开关：上传接口增加 `desensitize` 参数，允许用户选择是否执行脱敏 |

---

## 1. 文档概述

### 1.1 目的

本文档明确「基于 Multi-Agent 与 RAG 的智能合同风险审查系统」的业务背景、功能需求、非功能性需求、系统架构及技术选型，为设计、开发、测试与验收提供统一依据。

### 1.2 适用范围

面向产品经理、架构师、前后端开发工程师、测试工程师及运维人员。

### 1.3 术语表

| 术语 | 说明 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| Multi-Agent | 多智能体协作架构，各 Agent 分工处理不同子任务 |
| Agent | 智能体，独立完成特定子任务的 LLM 调用单元 |
| SSE | Server-Sent Events，服务端推送技术 |
| LLM | Large Language Model，大语言模型 |
| Chunking | 文本分块，将长文本按语义切分为更小的段落 |
| Map-Reduce | 先分片处理再汇总合并的并行计算模式 |
| Embedding | 向量化，将文本转换为语义向量表示的技术 |
| 乐观锁 | 通过版本号机制实现并发控制，避免数据竞争 |
| 脱敏 | 对敏感信息（姓名、身份证号等）进行替换或遮蔽处理 |

---

## 2. 项目背景与业务痛点

### 2.1 业务背景

普通 C 端用户（应届毕业生租房、求职者签劳动合同、自由职业者签外包协议）面对冗长法律文本时缺乏风险识别能力，极易陷入「霸王条款」，而传统法律咨询成本高、效率低。

### 2.2 业务痛点

| 痛点 | 描述 |
|------|------|
| 专业知识门槛高 | 普通用户无法理解法律条款的潜在风险 |
| 传统咨询成本高 | 律师咨询按小时计费，C 端用户难以负担 |
| 效率低下 | 人工审查一份合同通常需要数小时甚至数天 |
| 隐私顾虑 | 用户不愿将含有个人敏感信息的合同提交给不可信的第三方 |

### 2.3 项目目标

利用 LLM 自然语言理解能力，结合 RAG 引入权威法律法规，打造低成本、高可用、注重隐私保护的 C 端智能法务助理，帮助用户一键识别合同风险并获取修改建议。

---

## 3. 用户角色与配额规则

### 3.1 角色定义

| 角色 | 描述 | 核心需求 |
|------|------|----------|
| C 端普通用户 | 应届毕业生、求职者、自由职业者等 | 上传合同、获取风险报告、了解修改建议 |
| 系统管理员 | 平台运维人员 | 监控系统运行状态、管理用户配额、查看日志 |

> 注：V1.0 仅面向 C 端普通用户，管理后台为非 MVP 功能。

### 3.2 配额规则

| 规则 | 说明 |
|------|------|
| 初始配额 | 新用户注册后赠送 10 次免费审查额度 |
| 扣减时机 | 用户调用 `POST /submit` 确认提交时扣减 1 次 |
| 回滚机制 | 任务最终失败时自动回滚配额（Redis INCR + DB 乐观锁） |
| 负数防护 | Redis DECR 前通过 Lua 脚本原子判断余额 > 0 再扣减，避免竞态条件；余额不足返回 1003 错误码 |
| 充值方式 | V1.0 暂不支持充值，预留扩展接口 |

---

## 4. 系统整体架构

### 4.1 架构风格

前后端分离的模块化单体架构。

### 4.2 架构分层

```
┌─────────────────────────────────────────────┐
│              前端展示层 (Vue 3)               │
│      文件上传 · SSE 进度 · 风险报告可视化      │
└──────────────────────┬──────────────────────┘
                       │ HTTP / SSE
┌──────────────────────┴──────────────────────┐
│       后端网关与业务层 (Spring Boot 3)        │
│   鉴权 · 限流 · 路由 · 业务编排 · 状态机      │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────┴──────────────────────┐
│          AI 核心引擎层 (Spring AI)            │
│   Prompt 管理 · LLM API 调用 · Agent 编排    │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────┴──────────────────────┐
│          异步与消息中间件 (RabbitMQ)          │
│          长耗时任务异步解耦与削峰             │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────┴──────────────────────┐
│              数据存储层                       │
│   MySQL · Redis · Chroma · MinIO            │
└─────────────────────────────────────────────┘
```

---

## 5. 功能需求

### 5.1 文档解析与隐私脱敏模块

#### 5.1.1 功能描述

支持 PDF / Word 格式合同上传，提取纯文本并在送入 LLM 前自动脱敏。上传后先返回脱敏预览，用户确认后再正式提交审查。

#### 5.1.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F1.1 | PDF 文件解析 | P0 | 使用 Apache PDFBox 提取 PDF 文本 |
| F1.2 | Word 文件解析 | P1 | 使用 Apache POI 提取 .doc / .docx 文本 |
| F1.3 | 敏感信息脱敏 | P0 | 正则脱敏（P0）；NLP 实体识别增强（P1），将姓名、身份证号、手机号、银行卡号替换为 `***` |
| F1.4 | 脱敏结果预览与确认提交（可选脱敏） | P0 | 上传后返回脱敏文本供预览，用户通过确认接口正式提交审查任务。上传时可通过 `desensitize=false` 参数选择不脱敏，此时预览文本为原文 |
| F1.5 | 文件格式校验 | P0 | 仅允许 PDF / Word，文件大小 ≤ 20MB |
| F1.6 | 可选脱敏开关 | P0 | 上传接口增加 `desensitize` 参数（布尔值，默认 `true`），用户可关闭脱敏。关闭后 preview_text 直接存储原文，LLM 审查也基于原文。关闭脱敏时，用户需自行承担隐私风险 |

#### 5.1.3 脱敏规则

| 敏感类型 | 识别方式（P0） | 识别方式（P1 增强） | 替换格式 |
|----------|-----------------|---------------------|----------|
| 姓名 | 含称呼：`[\u4e00-\u9fa5]{2,4}(?:先生\|女士\|同志)`；合同主体后（2字主体）：`(?:甲方\|乙方\|丙方)[，:]?\s*[\u4e00-\u9fa5]{2,4}`；合同主体后（3字主体）：`(?:承租人\|出租人\|用人单位\|劳动者)[，:]?\s*[\u4e00-\u9fa5]{2,4}` | NLP 实体识别 | `***` |
| 身份证号 | `[1-9]\d{5}(19\|20)\d{2}(0[1-9]\|1[0-2])(0[1-9]\|[12]\d\|3[01])\d{3}[\dXx]` | NLP 实体识别 | `***` |
| 手机号 | `1[3-9]\d{9}` | NLP 实体识别 | `***` |
| 银行卡号 | `(?<=账号\|卡号\|户名)[：:\s]*\d{16,19}` | NLP 实体识别 | `***` |

> 注：P0 阶段以正则为主，存在少量误匹配/漏匹配属正常范围；P1 阶段引入 NLP 实体识别后将显著提升准确率。

### 5.2 基于 RAG 的法条检索与比对模块

#### 5.2.1 功能描述

将合同文本语义分块，从向量数据库检索最相关的《民法典》《劳动合同法》等法条，为 AI 审查提供事实依据。

#### 5.2.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F2.1 | 合同文本语义分块 | P0 | 按条款/段落 Chunking，保留上下文关联 |
| F2.2 | 法条向量化入库 | P0 | 权威法律法规文本向量化后存入 Chroma |
| F2.3 | 相似法条检索（混合检索） | P0 | 本地向量库检索，余弦相似度低于阈值（默认 0.75，可配置）则 fallback 到网络搜索 |
| F2.4 | Prompt 组装 | P0 | 将「合同条款 + 检索法条」组装为结构化 Prompt |
| F2.5 | 法条版本管理 | P1 | 支持法律法规更新与版本追溯 |
| F2.6 | 网络法条搜索（兜底） | P1 | 本地库未命中时，通过爬取国家法律法规数据库（flk.npc.gov.cn）网页解析获取法条文本（该网站无公开 API，需自行 HTML 解析）；命中结果缓存回本地库（TTL 默认 7 天，可配置） |

#### 5.2.3 混合 RAG 流程

```
合同文本 → 语义分块 → 生成向量 → 查询本地向量库
                                      │
                              相似度 ≥ 阈值？
                            ┌──────┴──────┐
                         是 │             │ 否
                            │        网络搜索法条
                            │        ┌───┴───┐
                            │     命中│       │未命中
                            │        ▼       ▼
                            │   缓存回本地库  返回空
                            └──────┬─────────┘
                                   ▼
                          组装 Prompt → LLM 审查
```

### 5.3 Multi-Agent 风险审查工作流

#### 5.3.1 功能描述

多个 LLM Agent 分工协作，完成合同分类 → 逐条风险扫描 → 报告生成的全流程。

#### 5.3.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F3.1 | 合同分类与定调 | P0 | Agent A 识别合同类型、用户立场，确定审查侧重点 |
| F3.2 | 逐条风险扫描 | P0 | Agent B 对每个 Chunk 结合法条进行风险扫描，评定风险等级 |
| F3.3 | 报告生成 | P0 | Agent C 汇总所有风险点，生成结构化审查报告 |
| F3.4 | 工作流编排 | P0 | 按 Agent A → 分块 → RAG → Agent B → Agent C 顺序编排，Agent B 并发受信号量控制（最大 10 路）。MQ 消费者配置 prefetch=10，单个消费线程内通过 `Semaphore(10)` 控制 Agent B 并发；若 MQ 连接数不足，可多消费线程共享同一 `Semaphore` 实例 |

#### 5.3.3 Agent 设计与数据契约

```
Agent A  输入:  { fullText: string }
         输出:  { contractType: string, userStance: string, reviewStrategy: string }

Agent B  输入:  { chunkContent: string, relatedLaws: string[], strategy: string }
         输出:  { risks: [{ clauseIndex: int, riskLevel: "HIGH"|"MEDIUM"|"LOW",
                            riskType: string, description: string,
                            suggestion: string, relatedLaws: string[] }] }

Agent C  输入:  { risks: [{ ...AgentB.risks[0] }], contractType: string }
         输出:  { summary: string, riskCount: {high:int, medium:int, low:int},
                  risks: [{ ...AgentB.risks[0] }] }
```

| Agent | 职责 | 核心能力 |
|-------|------|----------|
| Agent A (分类与定调) | 识别合同类型（租赁/劳动/外包等），确认用户立场（租客/房东等），确定审查侧重点 | 合同类型分类、立场判断 |
| Agent B (风险扫描) | 逐条分析条款，识别违约金过高、免责条款不合理、管辖法院不利等风险 | 条款风险识别与等级评定 |
| Agent C (报告生成) | 汇总所有风险点，生成结构化报告 | 风险汇总、报告生成 |

#### 5.3.4 风险等级定义

| 等级 | 含义 | 示例 |
|------|------|------|
| 高 | 条款明显违法或严重损害用户核心权益 | 约定「工伤概不负责」 |
| 中 | 条款可能存在不公平，需进一步协商 | 约定「争议须在甲方所在地法院管辖」 |
| 低 | 条款表述不规范但风险可控 | 缺少争议解决方式的约定 |

### 5.4 异步任务调度与实时反馈模块

#### 5.4.1 功能描述

审查任务为长耗时操作，通过异步机制避免 HTTP 超时，SSE 实时推送进度。

#### 5.4.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F4.1 | 异步任务提交 | P0 | 用户确认脱敏预览后提交，立即返回 taskId |
| F4.2 | 消息队列投递 | P0 | 将任务投递至 RabbitMQ（死信队列 DLX 处理消费失败） |
| F4.3 | SSE 实时进度推送 | P0 | 推送各阶段进度：parsing → retrieving → reviewing → summarizing |
| F4.4 | 任务状态查询 | P0 | 根据 taskId 查询当前状态 |
| F4.5 | 历史报告查看 | P1 | 用户可查看过往审查报告 |
| F4.6 | 失败手动重试 | P1 | 任务失败后用户可一键重试 |

#### 5.4.3 SSE 事件定义

SSE 使用三种事件类型，通过 `status` 字段标识阶段：

| 事件类型 | status 值 | 含义 | 示例 Payload |
|----------|-----------|------|-------------|
| `progress` | `parsing` | 正在解析文档 | `{"status":"parsing","progress":10,"message":"正在解析文档..."}` |
| `progress` | `retrieving` | 正在检索法条 | `{"status":"retrieving","progress":30,"message":"正在检索相关法条..."}` |
| `progress` | `reviewing` | 正在审查条款 | `{"status":"reviewing","progress":50,"message":"正在审查第 3/10 条..."}` |
| `progress` | `summarizing` | 正在生成报告 | `{"status":"summarizing","progress":90,"message":"正在生成审查报告..."}` |
| `complete` | `completed` | 审查完成 | `{"status":"completed","progress":100,"message":"审查完成","reportId":"xxx"}` |
| `error` | `failed` | 审查失败 | `{"status":"failed","progress":-1,"message":"审查失败: xxx"}` |

**SSE 连接生命周期**：
- 客户端通过 `GET /progress` 建立 SSE 连接
- 任务完成（`completed`）或失败（`failed`）后服务端关闭连接
- 客户端断开后应以指数退避策略重连（初始 1s，最大 30s，最多 5 次），最终降级为轮询 `GET /status`
- SSE 通道不支持历史事件重放，重连后通过 `GET /status` 恢复当前状态

**SSE 进度推送触发机制**：
- MQ 消费者在每个阶段（PARSING/RETRIEVING/REVIEWING/SUMMARIZING）开始和完成时，更新 `review_task.status` 和 `progress` 至 DB，并通过 `SseEmitter` 实例直接推送事件
- 同一 taskId 的 SSE 连接由 MQ 消费者进程持有（通过 `ConcurrentHashMap<taskId, SseEmitter>` 管理），状态变更后立即推送，不经过额外的消息链路，确保端到端延迟 ≤ 3s

---

## 6. 非功能性需求

### 6.1 性能需求

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 平均审查耗时 | ≤ 120s / 份 | 20 页以内的合同，超长文档耗时相应增加 |
| 并发支持 | ≥ 100 用户同时提交 | 通过 RabbitMQ 削峰填谷 |
| 实时进度延迟 | ≤ 3s | SSE 推送延迟 |

### 6.2 安全需求

| 需求 | 说明 |
|------|------|
| 隐私脱敏 | 上传后默认立即脱敏，原始文本解析后即丢弃，仅持久化脱敏后的文件。用户可通过 `desensitize=false` 选择不脱敏，此时原文作为 preview_text 存储，用户自行承担隐私风险 |
| 传输加密 | 全链路 HTTPS |
| 鉴权与防刷 | JWT 鉴权（Access Token 有效期 2 小时，Refresh Token 有效期 30 天，Redis 存储）+ Redis 滑动窗口限流 + 用户配额扣减 |
| API 防滥用 | 限制单 IP / 单用户 QPS，超过阈值返回 429 |
| 密码安全 | bcrypt 算法哈希存储，禁止明文存储 |
| 临时文件清理 | 上传后未确认提交的临时数据保留 1 小时后自动清理（Redis TTL + 定时任务兜底） |

### 6.3 可用性需求

| 指标 | 目标值 |
|------|--------|
| 系统可用性 | ≥ 99.5% |
| 任务失败自动重试 | 指数退避重试 3 次 |
| 数据持久化 | MySQL 主从备份，每日自动备份 |

### 6.4 操作审计

| 需求 | 说明 |
|------|------|
| 审计范围 | 用户注册、上传、确认提交、查看报告、重试等关键操作 |
| 存储方式 | 输出至结构化日志文件，预留 `operation_log` 表设计（管理后台配套） |
| 留存周期 | ≥ 90 天 |
| MVP 范围 | V1.0 仅做日志输出，查询接口随管理后台（P1）开发 |

### 6.5 可扩展性需求

- 向量数据库支持切换（Chroma 适配，预留切换接口）
- LLM 供应商支持切换（OpenAI / 阿里云通义千问 / 本地模型）
- Agent 数量可动态增减

---

## 7. 数据模型设计

### 7.1 核心实体关系

```
User (1) ──→ (N) ReviewTask
ReviewTask (1) ──→ (N) RiskItem
ReviewTask (1) ──→ (1) ReviewReport
```

### 7.2 表结构设计

#### 7.2.1 用户表 (user)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| username | VARCHAR(50) | 用户名 |
| password_hash | VARCHAR(256) | 密码哈希（bcrypt） |
| review_quota | INT | 剩余审查次数，初始值 10 |
| version | INT | 乐观锁版本号 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### 7.2.2 审查任务表 (review_task)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 用户 ID |
| file_name | VARCHAR(255) | 原始文件名 |
| file_size | BIGINT | 文件大小（字节） |
| preview_text | MEDIUMTEXT | 脱敏后预览文本（上传时写入，confirm 后保留，任务完成后 24h 内清理） |
| file_url | VARCHAR(1024) | 脱敏后文件存储路径（原始文件解析脱敏后即丢弃） |
| contract_type | VARCHAR(50) | 合同类型（租赁/劳动/外包/其他），Agent A 执行后回填 |
| user_stance | VARCHAR(50) | 用户立场，Agent A 执行后回填 |
| status | ENUM | PENDING / PARSING / RETRIEVING / REVIEWING / SUMMARIZING / SUCCESS / FAILED |
| progress | INT | 进度百分比 0-100，失败时为 -1 |
| error_msg | TEXT | 失败原因 |
| total_chunks | INT | 总条款数 |
| reviewed_chunks | INT | 已审查条款数 |
| created_at | DATETIME | 创建时间 |
| completed_at | DATETIME NULL | 完成时间（SUCCESS/FAILED 时回填） |
| updated_at | DATETIME | 更新时间 |

#### 7.2.3 风险项表 (risk_item)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| task_id | BIGINT FK | 关联任务 ID |
| clause_index | INT | 条款序号 |
| clause_content | TEXT | 条款原文 |
| risk_level | ENUM(HIGH/MEDIUM/LOW) | 风险等级 |
| risk_type | VARCHAR(100) | 风险类型（违约金/免责/管辖/其他） |
| description | TEXT | 风险描述 |
| suggestion | TEXT | 修改建议 |
| related_laws | JSON | 相关法条（字符串数组） |
| created_at | DATETIME | 创建时间 |

#### 7.2.4 审查报告表 (review_report)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| task_id | BIGINT FK | 关联任务 ID |
| summary | TEXT | 审查总结 |
| risk_count_high | INT | 高风险数量（冗余字段，用于快速查询/排序） |
| risk_count_medium | INT | 中风险数量（冗余字段） |
| risk_count_low | INT | 低风险数量（冗余字段） |
| report_json | JSON | 完整报告（结构与 Agent C 输出一致，riskCount 字段与上述冗余字段同步写入） |
| pdf_url | VARCHAR(1024) | PDF 报告下载地址 |
| created_at | DATETIME | 创建时间 |

> `report_json` 结构示例：
> ```json
> {
>   "taskId": "123",
>   "summary": "本合同存在 3 项高风险、2 项中风险...",
>   "riskCount": { "high": 3, "medium": 2, "low": 1 },
>   "risks": [
>     {
>       "clauseIndex": 5,
>       "clauseContent": "条款原文...",
>       "riskLevel": "HIGH",
>       "riskType": "违约金",
>       "description": "风险描述",
>       "suggestion": "修改建议",
>       "relatedLaws": ["《民法典》第XXX条..."]
>     }
>   ],
>   "generatedAt": "2026-07-05T12:00:00Z"
> }
> ```

#### 7.2.5 索引设计

| 表名 | 索引字段 | 索引类型 | 说明 |
|------|----------|----------|------|
| user | username | UNIQUE | 用户名唯一索引 |
| review_task | user_id | INDEX | 加速按用户查询任务 |
| review_task | status | INDEX | 加速按状态筛选 |
| review_task | created_at | INDEX | 支持按时间排序 |
| risk_item | task_id | INDEX | 加速按任务查询风险项 |
| risk_item | (task_id, risk_level) | COMPOSITE INDEX | 联合查询按等级过滤 |
| review_report | task_id | UNIQUE INDEX | 任务与报告一对一关联 |

---

## 8. 接口设计

### 8.1 RESTful API

| 方法 | 路径 | 说明 | 请求体 / 参数 | 响应 |
|------|------|------|---------------|------|
| POST | `/api/v1/auth/register` | 用户注册 | `{username, password}` | `{userId, token, refreshToken}` |
| POST | `/api/v1/auth/login` | 用户登录 | `{username, password}` | `{token, refreshToken}` |
| POST | `/api/v1/auth/refresh` | Token 刷新 | `{refreshToken}` | `{token, refreshToken}` |
| POST | `/api/v1/contract/upload` | 上传合同文件（解析脱敏后返回预览） | MultipartFile + `?desensitize=true`（可选，默认 true） | `{taskId, previewText}` |
| POST | `/api/v1/contract/{taskId}/submit` | 确认预览后提交审查 | — | `{taskId}` |
| GET | `/api/v1/contract/{taskId}/status` | 查询任务状态 | — | `{taskId, status, progress}` |
| GET | `/api/v1/contract/{taskId}/report` | 获取审查报告 | — | `{taskId, summary, risks[]}` |
| GET | `/api/v1/contract/history` | 获取历史记录（按创建时间降序） | `?page=1&size=10` | `{tasks[], total, page, size}` |
| POST | `/api/v1/contract/{taskId}/retry` | 手动重试失败任务 | — | `{taskId}` |
| GET | `/api/v1/contract/{taskId}/progress` | SSE 进度推送 | — | SSE Event Stream |
| GET | `/api/v1/contract/{taskId}/file` | 下载脱敏后的合同文件 | — | 文件流 |
| GET | `/api/v1/contract/{taskId}/report/pdf` | 下载 PDF 报告（P1） | — | 文件流 |

> **上传 / 提交流程**：
> 1. `POST /upload` 解析文本后，根据 `desensitize` 参数（默认 `true`）决定是否脱敏，创建状态为 `PENDING` 的审查任务（脱敏文本或原文持久化至 `preview_text`），返回 `taskId` 及预览文本。此时暂不扣减配额。
> 2. `POST /{taskId}/submit` 确认后提交审查：通过 Lua 脚本原子扣减配额（判断余额 > 0 再 DECR，余额不足返回 1003）→ 投递 MQ → 消费者消费并变更状态为 `PARSING`。
> 3. 若用户未调用 submit，后台定时任务 1 小时后自动清理该 PENDING 任务及临时数据。

### 8.2 SSE 事件流示例

```
GET /api/v1/contract/{taskId}/progress
Accept: text/event-stream

event: progress
data: {"status":"parsing","progress":10,"message":"正在解析文档..."}

event: progress
data: {"status":"reviewing","progress":50,"message":"正在审查第 3/10 条..."}

event: complete
data: {"status":"completed","progress":100,"message":"审查完成","reportId":"xxx"}

event: error
data: {"status":"failed","progress":-1,"message":"审查失败: LLM API 调用超时"}
```

### 8.3 业务错误码定义

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|-------------|
| 1001 | 文件格式不支持（仅允许 PDF/Word） | 400 |
| 1002 | 文件大小超出限制（≤ 20MB） | 400 |
| 1003 | 审查次数不足 | 403 |
| 1004 | 任务不存在 | 404 |
| 1005 | 任务状态不允许当前操作 | 409 |
| 1006 | LLM API 调用失败 | 502 |
| 1008 | 请求频率超限 | 429 |
| 1009 | 任务执行超时 | 409 |

> 注：错误码 1007 已废弃（原为"法条检索无结果"，因 RAG 兜底机制下不会触发），编号保留空洞。

---

## 9. 技术选型汇总

| 层级 | 技术栈 | 说明 |
|------|--------|------|
| 前端 | Vue 3 + Element Plus + Axios | 用户界面与交互 |
| 后端框架 | Spring Boot 3 + Spring Security | 业务逻辑与鉴权 |
| AI 框架 | Spring AI | LLM 调用与 Agent 编排，与 Spring Boot 生态原生集成 |
| 消息队列 | RabbitMQ | 异步任务解耦，DLX 处理消费失败 |
| 数据库 | MySQL 8.0 | 持久化存储 |
| 缓存 | Redis | 限流、会话、热点缓存 |
| 向量数据库 | Chroma | 法条向量存储与检索，Docker 一键部署，零配置开箱即用 |
| 对象存储 | MinIO | 文件与报告存储，本地部署便于开发调试 |
| 文档解析 | Apache PDFBox / Apache POI | PDF / Word 解析 |

---

## 10. 核心技术难点与应对策略

### 10.1 长文本处理与大模型 Token 限制

**问题：** 合同可能长达数万字，超出 LLM Context Window。

**方案：** Map-Reduce 架构 — Agent A 完成合同分类定调（前置，不在 Map-Reduce 主链路中），随后按条款切分并发调用 Agent B（受信号量控制，默认最大 10 路）提取局部风险点，最终 Agent C 汇总生成报告。

### 10.2 异步任务状态一致性与失败重试

**问题：** MQ 消费者处理长链路任务时，可能因 LLM API 超时、网络抖动导致任务失败、状态卡死。

**方案：**

| 措施 | 技术实现 |
|------|----------|
| 状态机 | 完整状态转换见下表 |
| 自动重试 | Spring Retry + RabbitMQ DLX 指数退避重试（最多 3 次） |
| 超时熔断 | PARSING ≤ 30s、RETRIEVING ≤ 30s、REVIEWING ≤ 120s、SUMMARIZING ≤ 30s；总超时 ≤ 5 分钟 |
| 失败记录 | 标记 FAILED 并记录 error_msg，支持用户手动重试 |

**状态转换说明：**

| 当前状态 | 触发条件 | 下一状态 |
|----------|----------|----------|
| PENDING | MQ 消费者消费消息 | PARSING |
| PARSING | 解析完成 / 解析失败 | RETRIEVING / FAILED |
| RETRIEVING | 检索完成 | REVIEWING |
| RETRIEVING | 检索失败且未超重试次数 | RETRIEVING（回退重试） |
| RETRIEVING | 检索失败且超重试次数（默认 3 次，可配置） | FAILED |
| REVIEWING | 审查完成 | SUMMARIZING |
| REVIEWING | 审查失败且未超重试次数 | REVIEWING（回退重试） |
| REVIEWING | 审查失败且超重试次数（默认 3 次，可配置） | FAILED |
| SUMMARIZING | 汇总完成 / 汇总失败 | SUCCESS / FAILED |
| FAILED | 用户手动重试 | PENDING（重新排队） |

> 注：重试次数默认为 3 次，可通过 `application.yml` 配置 `contract.review.max-retry-count` 参数调整。

> 注：PARSING 阶段失败不重试（解析逻辑确定性高，重试无意义），直接标记 FAILED。
> 状态到达 SUCCESS 或 FAILED 后，后台定时任务在 24 小时内将 `preview_text` 置空以释放存储。

---

## 11. 验收标准

### 11.1 功能验收

| 编号 | 验收项 | 预期结果 |
|------|--------|----------|
| A1 | 上传 PDF 合同（Word 支持为 P1） | 成功解析文本并返回脱敏预览，敏感信息被脱敏 |
| A2 | 确认提交审查 | 确认脱敏预览后提交，立即返回 taskId |
| A3 | 查看实时进度 | SSE 推送各阶段进度事件 |
| A4 | 审查完成 | 生成包含风险项和修改建议的报告 |
| A5 | 查看历史报告 | 可浏览以往所有审查记录 |
| A6 | 失败重试 | 失败任务可一键重新审查 |

### 11.2 非功能验收

| 编号 | 验收项 | 目标值 |
|------|--------|--------|
| B1 | 合同审查耗时 | 20 页以内 ≤ 120s |
| B2 | 并发支持 | ≥ 100 用户同时提交，系统正常 |
| B3 | 接口限流 | 超限请求返回 429 |
| B4 | 隐私脱敏 | P0 正则脱敏命中率 ≥ 90%（基于测试集验证） |

---

## 12. 技术实施分级指南

> 本章节为开发者提供技术落地的分阶段实施路径。所有需求和功能均保留在原文档中，此处仅标注**实施优先级**和**阶段性替代方案**，帮助初学者在有限时间内逐步构建完整系统。

### 12.1 总体分级策略

| 实施级别 | 目标 | 预计周期 | 核心产出 |
|----------|------|----------|----------|
| **Phase 0** | 环境搭建 | 1-2 天 | 开发环境就绪 |
| **Phase 1** | MVP 核心链路 | 10-15 天 | 上传→脱敏→LLM审查→报告，全链路可运行 |
| **Phase 2** | 功能增强 | 8-12 天 | RAG法条检索、Multi-Agent编排、异步任务 |
| **Phase 3** | 架构完善 | 8-12 天 | 前端、安全加固、中间件、运维 |

### 12.2 各模块实施分级

#### A. 鉴权与安全

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | 基础用户注册登录 | 手写 JWT 工具类（JJWT库）+ HandlerInterceptor 拦截器，不用 Spring Security | 能用，但鉴权逻辑较薄 |
| Phase 2 | 完整鉴权链 | 接入 Spring Security，配置 SecurityFilterChain，Token 刷新机制 | 接近完整方案 |
| Phase 3 | 安全加固 | Redis 滑动窗口限流 + Lua 脚本、API 防滥用、操作审计日志 | 达到文档描述的完整安全需求 |

#### B. 文件上传与解析

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | PDF 解析 + MinIO 存储 | PDFBox 提取文本 + MinIO 上传存储 | 仅支持 PDF（P0），无 Word |
| Phase 2 | Word 解析 | 新增 Apache POI 解析 .docx | 支持 Word（P1） |
| Phase 3 | 增强 | 文件预览优化、大文件分片上传 | 完整方案 |

#### C. 隐私脱敏

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | 正则脱敏 | 文档中的姓名/身份证/手机号/银行卡号正则，Java Pattern 直接实现 | 能覆盖主要场景，少量误匹配 |
| Phase 2 | 保持 | 无变化，正则方案已足够 MVP | — |
| Phase 3 | NLP 增强 | 引入 NER 模型（如 HanLP / LLM 辅助识别），提升准确率 | 达到文档 P1 增强目标 |

#### D. AI 审查（LLM 调用）

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | 单次 LLM 调用 | Spring AI ChatClient 直接调用（OpenAI API 或通义千问），Prompt 中放入合同全文，一次返回审查结果 | 无 RAG、无 Agent 编排，但能出结果 |
| Phase 2 | RAG + Multi-Agent | Chroma 向量检索法条 → Spring AI Agent 编排（A→B→C），Map-Reduce 架构 | 接近完整 AI 链路 |
| Phase 3 | 增强 | 并发信号量控制、超时熔断、Token 成本优化 | 达到文档完整目标 |

#### E. 异步任务与状态机

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | 同步调用 | `@Async` + `CompletableFuture` 异步处理，状态直接写 DB，前端轮询 `GET /status` | 无 MQ、无 SSE，但功能可用 |
| Phase 2 | SSE + 异步 | 新增 SSE 实时进度推送，用 `SseEmitter` 或 Spring AI 的 SSE 支持 | 实时进度体验提升 |
| Phase 3 | MQ + 状态机 | RabbitMQ 投递 + DLX 死信重试 + 状态机完整实现（PARSING→RETRIEVING→REVIEWING→SUMMARIZING） | 达到文档完整架构目标 |

#### F. 前端

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | 无前端 | 用 Apifox / Postman 测接口即可 | 不影响后端核心逻辑 |
| Phase 2 | 极简页面 | Thymeleaf 或简单 HTML + Axios 做 1-2 个页面（上传、查看报告） | 能演示 |
| Phase 3 | Vue 3 完整前端 | Vue 3 + Element Plus + SSE 进度条 + 报告可视化 | 达到文档完整目标 |

#### G. 数据存储

| 级别 | 实施内容 | 技术方案 | 与最终目标的差距 |
|------|----------|----------|------------------|
| Phase 1 | MySQL + 简单表结构 | 用户表、任务表、报告表，MyBatis-Plus CRUD | 基础可用 |
| Phase 2 | Redis 缓存 | 配额管理（Redis DECR/INCR）、会话缓存 | 减少 DB 压力 |
| Phase 3 | 完整存储架构 | 索引优化、主从备份、MinIO 全生命周期、临时文件清理 | 达到文档完整目标 |

### 12.3 Phase 1 快速启动清单

以下是**最短时间内跑通核心链路**的最小实现清单：

```
第 1 天：环境搭建
  ├── JDK 17 + IDEA + Maven 项目脚手架
  ├── Docker 启动 MySQL 8.0 + Redis + MinIO
  └── Spring Boot 3 项目结构搭建（controller/service/mapper/config）

第 2-3 天：用户模块
  ├── 用户表建表 + MyBatis-Plus 配置
  ├── 注册接口（密码 BCrypt 加密存储）
  ├── 登录接口（返回 JWT Token）
  └── JWT 拦截器（校验 Token 有效性）

第 4-5 天：文件上传与解析
  ├── MinIO 配置 + 文件上传工具类
  ├── 文件格式校验（PDF、大小 ≤ 20MB）
  ├── PDFBox 解析 PDF 文本
  └── 上传接口：解析 → 脱敏 → 存储 → 返回预览文本 + taskId

第 6 天：脱敏模块
  ├── 正则脱敏工具类（姓名/身份证/手机号/银行卡号）
  └── 集成到上传流程

第 7-8 天：LLM 审查
  ├── Spring AI 依赖 + LLM API Key 配置
  ├── Prompt 模板设计（合同审查 Prompt）
  ├── 单次调用审查接口（同步，先不做异步）
  └── 审查结果 JSON 解析 + 存储到报告表

第 9-10 天：查询与报告
  ├── 任务状态查询接口
  ├── 报告查询接口
  ├── 历史记录分页查询
  └── 接口联调测试

第 11-12 天：异步化 + 进度
  ├── @Async 异步执行审查任务
  ├── 状态机（简化版：PENDING→PROCESSING→SUCCESS/FAILED）
  └── 前端轮询 /status 获取进度

第 13-15 天：联调与完善
  ├── 全链路联调
  ├── 异常处理（LLM 调用失败、解析失败等）
  ├── 统一响应格式 + 错误码
  └── 代码整理 + README
```

### 12.4 技术学习优先级

| 优先级 | 技术 | 学习资源建议 |
|--------|------|-------------|
| **必须先学** | Spring Boot 3 + MyBatis-Plus | 官方文档 + 跟一个 CRUD 教程 |
| **必须先学** | JWT 鉴权 | 手写一个 JWT 工具类，理解 Header/Payload/Signature |
| **必须先学** | Spring AI 基础调用 | Spring AI 官方 Getting Started，ChatClient 用法 |
| **随后学习** | SSE | Spring Boot SSE 示例，理解 SseEmitter |
| **随后学习** | Redis 基础 | Redis 数据类型 + Jedis/Lettuce 操作 |
| **Phase 2 学** | Chroma + 向量检索 | Docker 起 Chroma + Spring AI Chroma 集成 |
| **Phase 2 学** | RabbitMQ | Docker 起 RabbitMQ + Spring AMQP 入门 |
| **Phase 2 学** | Spring Security | SecurityFilterChain 配置、Filter 链理解 |
| **Phase 3 学** | Vue 3 | 基础语法 + Axios 调接口 |
| **Phase 3 学** | 并发控制 | Semaphore、CompletableFuture |
