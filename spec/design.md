# 基于 Multi-Agent 与 RAG 的智能合同风险审查系统 — 设计文档

## Overview

### 背景

普通 C 端用户（应届毕业生租房、求职者签劳动合同、自由职业者签外包协议）面对冗长法律文本时缺乏风险识别能力，极易陷入「霸王条款」，而传统法律咨询成本高、效率低。本项目旨在利用 LLM 的自然语言理解能力，结合 RAG 引入权威法律法规，打造低成本、高可用、注重隐私保护的 C 端智能法务助理。

### 目标

构建一套基于 Multi-Agent 协作架构与 RAG 检索增强生成的智能合同风险审查系统，实现以下核心目标：

- **一键审查**：用户上传合同文件，系统自动解析、脱敏、审查并生成结构化风险报告
- **隐私保护**：上传后默认即时脱敏敏感信息，原始文本解析后即丢弃，仅持久化脱敏后内容（可通过 `desensitize=false` 关闭脱敏）
- **实时反馈**：通过 SSE 推送审查进度，让用户实时了解任务执行状态
- **高性价比**：利用 LLM 替代传统律师咨询，大幅降低 C 端用户的法律服务门槛

### 范围

本文档覆盖系统的架构设计、组件划分、接口契约、数据模型、正确性保证、错误处理与测试策略。系统范围涵盖以下核心模块：

- 文档解析与隐私脱敏
- 基于 RAG 的法条检索与比对
- Multi-Agent 风险审查工作流
- 异步任务调度与实时反馈

技术栈限定为 Spring Boot 3 + MyBatis-Plus + Spring AI + Spring Security + MySQL + Redis + RabbitMQ + Chroma + MinIO + Apache PDFBox / Apache POI，前端使用 Vue 3 + Element Plus + Axios。

**安全基线**：全链路 HTTPS 传输加密，JWT 鉴权（Access Token 2h + Refresh Token 30d Redis 存储），bcrypt 密码哈希。

---

## Architecture

### 总体架构风格

前后端分离的**模块化单体架构**，各模块通过 Spring IoC 容器进行依赖注入与解耦，长耗时任务通过消息队列异步处理。

### 架构分层

系统自顶向下分为五层：

```
┌──────────────────────────────────────────────────────┐
│                    前端展示层                         │
│              Vue 3 + Element Plus + Axios             │
│         文件上传 · SSE 进度 · 风险报告可视化          │
└────────────────────────┬─────────────────────────────┘
                         │ HTTP / SSE
┌────────────────────────┴─────────────────────────────┐
│              后端网关与业务层                          │
│            Spring Boot 3 + Spring Security            │
│       鉴权 · 限流 · 路由 · 业务编排 · 状态机          │
└────────────────────────┬─────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────┐
│              AI 核心引擎层                             │
│                 Spring AI                             │
│      Prompt 管理 · LLM 调用 · Agent 编排              │
└────────────────────────┬─────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────┐
│           异步与消息中间件层                            │
│                   RabbitMQ                            │
│           长耗时任务异步解耦与削峰填谷                  │
└────────────────────────┬─────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────┐
│                  数据存储层                            │
│     MySQL 8.0 · Redis · Chroma · MinIO               │
│  结构化数据  缓存/会话  向量检索  文件/报告存储        │
└──────────────────────────────────────────────────────┘
```

### 后端 MVC 三层架构

在后端网关与业务层内部，采用经典的 **Controller → Service → Mapper** 三层架构，各层职责明确、单向依赖：

```
┌─────────────────────────────────────────────────┐
│                Controller 层                      │
│    接收 HTTP 请求、参数校验、调用 Service          │
│   @RestController, @RequestMapping                │
│   AuthController, ContractController              │
├─────────────────────────────────────────────────┤
│                    ↓ 调用                         │
│─────────────────────────────────────────────────│
│                Service 层                         │
│       业务逻辑编排、事务管理、状态机调度            │
│   @Service, @Transactional                       │
│   AuthService, ContractService,                   │
│   RagService, AgentOrchestrator                  │
├─────────────────────────────────────────────────┤
│                    ↓ 调用                         │
│─────────────────────────────────────────────────│
│                Mapper 层 (Repository)             │
│    数据库 CRUD 操作、MyBatis-Plus BaseMapper     │
│   @Mapper, BaseMapper<T>                         │
│   UserMapper, ReviewTaskMapper,                   │
│   RiskItemMapper, ReviewReportMapper              │
├─────────────────────────────────────────────────┤
│                    ↓ 操作                         │
│─────────────────────────────────────────────────│
│                数据库 (MySQL)                      │
└─────────────────────────────────────────────────┘
```

**数据库交互技术**：使用 **MyBatis-Plus** 作为 ORM 框架，提供 `BaseMapper<T>` 基础 CRUD 能力，配合 MyBatis-Plus 的 `QueryWrapper` / `LambdaQueryWrapper` 实现条件查询、分页、乐观锁等功能，减少手写 SQL 的工作量。

| 持久化组件 | 表名 | Mapper 接口 | 说明 |
|-----------|------|-------------|------|
| UserMapper | `user` | `BaseMapper<User>` | 用户表基础 CRUD |
| ReviewTaskMapper | `review_task` | `BaseMapper<ReviewTask>` | 审查任务状态更新、条件查询 |
| RiskItemMapper | `risk_item` | `BaseMapper<RiskItem>` | 风险项批量插入、按 task 查询 |
| ReviewReportMapper | `review_report` | `BaseMapper<ReviewReport>` | 报告写入与唯一索引查询 |

### 模块划分

| 模块 | 职责 | 所属层级 |
|------|------|----------|
| **用户鉴权模块** | 注册、登录、JWT Token 签发与验证、Refresh Token 刷新 | 网关与业务层 |
| **文件上传与解析模块** | PDF/Word 文件格式校验、文本提取、MinIO 存储 | 网关与业务层 |
| **隐私脱敏模块** | 正则 + NLP 实体识别敏感信息、替换为 `***` | 网关与业务层 |
| **RAG 检索模块** | 文本语义分块、向量化、混合检索（本地 Chroma + 网络兜底） | AI 引擎层 |
| **Agent 编排模块** | Agent A/B/C 的工作流调度、Map-Reduce 并发控制 | AI 引擎层 |
| **任务调度与状态机模块** | 异步任务队列、状态转换、超时熔断、重试机制 | 异步与消息层 |
| **SSE 推送模块** | 基于 SseEmitter 的实时进度推送 | 网关与业务层 |
| **报告生成模块** | 审查结果持久化、报告结构化输出 | 网关与业务层 |
| **配额与限流模块** | Redis 配额扣减、滑动窗口限流、Lua 原子操作 | 网关与业务层 |

### 可扩展性设计

| 维度 | 扩展方式 | 实施阶段 |
|------|----------|----------|
| **向量数据库** | Chroma 适配层接口，支持切换为 Pinecone / Milvus 等其他向量库 | Phase 2+ |
| **LLM 供应商** | Spring AI 原生支持多 Provider（OpenAI / 通义千问 / 本地模型），通过配置切换 | Phase 1+ |
| **Agent 数量** | Agent 编排器支持动态增减 Agent，新增 Agent 注册到编排器即可参与工作流 | Phase 2+ |
| **存储层** | MinIO 适配 S3 接口，可切换为阿里云 OSS / AWS S3 等 | Phase 3 |

### 项目目录结构

```
ContractReview/
├── src/main/java/com/contractreview/
│   ├── ContractReviewApplication.java          # 启动类
│   │
│   ├── config/                                 # 配置类
│   │   ├── WebMvcConfig.java                   #   MVC 配置（拦截器、跨域）
│   │   ├── MyBatisPlusConfig.java              #   分页插件、乐观锁插件
│   │   ├── RedisConfig.java                    #   RedisTemplate 配置
│   │   ├── RabbitMqConfig.java                 #   队列、交换机、DLX 定义
│   │   ├── MinIoConfig.java                    #   MinIO 客户端配置
│   │   ├── AsyncConfig.java                    #   异步线程池配置
│   │   └── SpringAiConfig.java                 #   ChatClient Bean 配置
│   │
│   ├── controller/                             # REST 控制器层
│   │   ├── AuthController.java                 #   注册 / 登录 / 刷新
│   │   ├── ContractController.java             #   上传 / 提交 / 状态 / 报告 / SSE / 重试
│   │   └── ...                                 #   其余控制器省略
│   │
│   ├── service/                                # 业务逻辑层
│   │   ├── AuthService.java                    #   鉴权业务接口
│   │   ├── ContractService.java                #   合同业务编排接口
│   │   ├── FileParser.java                     #   文件解析（PDFBox / POI）
│   │   ├── DesensitizationService.java         #   隐私脱敏
│   │   ├── RagService.java                     #   RAG 检索（Chroma + 网络兜底）
│   │   ├── AgentOrchestrator.java              #   Agent 工作流编排
│   │   ├── TaskStateMachine.java               #   任务状态机
│   │   ├── SseService.java                     #   SSE 推送
│   │   ├── ReportService.java                  #   报告生成
│   │   └── impl/                               #   实现类
│   │       ├── AuthServiceImpl.java
│   │       ├── ContractServiceImpl.java
│   │       ├── FileParserImpl.java
│   │       ├── DesensitizationServiceImpl.java
│   │       ├── RagServiceImpl.java
│   │       ├── AgentOrchestratorImpl.java
│   │       ├── TaskStateMachineImpl.java
│   │       ├── SseServiceImpl.java
│   │       └── ReportServiceImpl.java
│   │
│   ├── mapper/                                 # MyBatis-Plus 数据访问层
│   │   ├── UserMapper.java
│   │   ├── ReviewTaskMapper.java
│   │   ├── RiskItemMapper.java
│   │   ├── ReviewReportMapper.java
│   │   └── OperationLogMapper.java
│   │
│   ├── domain/                                 # 领域模型
│   │   ├── entity/                             #   数据库实体
│   │   │   ├── User.java
│   │   │   ├── ReviewTask.java
│   │   │   ├── RiskItem.java
│   │   │   ├── ReviewReport.java
│   │   │   └── OperationLog.java
│   │   ├── dto/                                #   请求/响应 DTO
│   │   │   ├── AuthRequest.java
│   │   │   ├── AuthResponse.java
│   │   │   ├── UploadResponse.java
│   │   │   ├── SubmitRequest.java
│   │   │   ├── StatusResponse.java
│   │   │   └── ReportResponse.java
│   │   └── enums/                              #   枚举
│   │       ├── TaskStatus.java
│   │       ├── RiskLevel.java
│   │       └── ErrorCode.java                  #   业务错误码
│   │
│   ├── common/                                 # 通用组件
│   │   ├── R.java                              #   统一响应体
│   │   └── BusinessException.java              #   业务异常
│   │
│   ├── exception/                              # 异常处理
│   │   └── GlobalExceptionHandler.java         #   全局异常处理器
│   │
│   ├── security/                               # 安全与鉴权
│   │   ├── JwtUtils.java                       #   JWT 签发与校验
│   │   ├── JwtInterceptor.java                 #   Token 拦截器
│   │   └── UserContext.java                    #   请求上下文（ThreadLocal）
│   │
│   ├── ai/                                     # AI 引擎
│   │   ├── agent/                              #   Multi-Agent 实现
│   │   │   ├── ContractClassifierAgent.java    #   合同分类与定调
│   │   │   ├── RiskScannerAgent.java           #   条款级风险扫描
│   │   │   └── ReportSummarizerAgent.java      #   审查报告汇总
│   │   └── rag/                                #   RAG 检索
│   │       ├── ChunkingService.java            #    语义分块
│   │       ├── EmbeddingService.java           #    向量化
│   │       └── RetrievalService.java           #    混合检索（Chroma + 网络）
│   │
│   ├── async/                                  # 异步任务
│   │   ├── ReviewTaskConsumer.java             #   MQ 消费者
│   │   └── QuotaRollbackHandler.java           #   配额回滚处理器
│   │
│   └── util/                                   # 工具类
│       ├── DesensitizationUtil.java            #   正则脱敏工具
│       └── FileUtil.java                       #   文件校验工具
│
├── src/main/resources/                         # 资源文件
│   ├── application.yml                         #   主配置
│   ├── application-dev.yml                     #   开发环境配置
│   ├── application-prod.yml                    #   生产环境配置
│   ├── mapper/                                 #   MyBatis XML 映射文件
│   └── db/                                     #   数据库脚本
│       └── init.sql                            #   建表 DDL
│
├── src/test/java/com/contractreview/           # 测试
│   ├── controller/                             #   控制器测试
│   ├── service/                                #   服务测试
│   └── util/                                   #   工具测试
│
├── web/                                        # 前端项目（Vue 3）
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── vite.config.js
│
├── pom.xml                                     # Maven 构建
└── README.md                                   # 项目说明
```

### 核心交互架构图

```
用户                 前端              后端 API            MQ           AI 引擎          数据层
 │                    │                 │                 │              │               │
 │  上传合同文件       │                 │                 │              │               │
 ├───────────────────►│                 │                 │              │               │
 │                    │ POST /upload    │                 │              │               │
 │                    ├────────────────►│ 解析+可选脱敏+存储│              │               │
 │                    │                 ├───────────────────────────────►│               │
 │                    │                 │◄───────────────────────────────┤               │
 │ 返回预览+taskId    │                 │                 │              │               │
 │◄───────────────────┤                 │                 │              │               │
 │                    │                 │                 │              │               │
 │ 确认提交审查       │                 │                 │              │               │
 ├───────────────────►│ POST /submit    │                 │              │               │
 │                    ├────────────────►│ 扣减配额+投递MQ  │              │               │
 │                    │                 ├───────────────►│              │               │
 │                    │                 │  任务消息        │ 消费         │               │
 │                    │                 │◄───────────────┤              │               │
 │                    │                 │                 │ PARSING      │               │
 │                    │ SSE /progress   │                 ├─────────────►│               │
 │◄═══════════════════╪═════════════════╪═════════════════╪══════════════╪══════════════►│
 │                    │  ═══ 实时进度 ═══╝               │              │               │
 │                    │                 │                 │ RETRIEVING   │               │
 │                    │                 │                 ├─────────────►│ Chroma 检索    │
 │                    │                 │                 │ REVIEWING    │               │
 │                    │                 │                 ├─────────────►│ Agent B 扫描   │
 │                    │                 │                 │ SUMMARIZING  │               │
 │                    │                 │                 ├─────────────►│ Agent C 汇总   │
 │                    │                 │ 完成事件         │              │               │
 │                    │                 │◄───────────────┤              │               │
 │ 查看报告           │                 │                 │              │               │
 ├───────────────────►│ GET /report     │                 │              │               │
 │                    ├────────────────►│                                  │               │
 │◄───────────────────┤ 返回结构化报告   │                                 │               │
```

---

## Components and Interfaces

### 组件定义与职责

#### 1. AuthController — 用户鉴权网关

| 属性 | 说明 |
|------|------|
| **职责** | 处理注册、登录、Token 刷新请求 |
| **依赖** | AuthService, JwtUtils |
| **线程模型** | 同步 HTTP 请求-响应 |

#### 2. ContractController — 合同业务网关

| 属性 | 说明 |
|------|------|
| **职责** | 处理上传、提交、状态查询、报告获取、SSE 连接、重试等请求 |
| **依赖** | ContractService, SseService, FileParser |
| **线程模型** | 上传/提交为同步；SSE 为长连接异步推送 |

#### 3. FileParser — 文件解析器

| 属性 | 说明 |
|------|------|
| **职责** | 校验文件格式与大小，使用 PDFBox/POI 提取文本 |
| **接口** | `ParseResult parse(InputStream input, String fileName)` |
| **输出** | `ParseResult{ String fullText, String fileName, long fileSize }` |

#### 4. DesensitizationEngine — 脱敏引擎

| 属性 | 说明 |
|------|------|
| **职责** | 对提取的纯文本进行敏感信息识别与替换 |
| **接口** | `String desensitize(String rawText)` |
| **实现** | P0 使用正则（姓名、身份证、手机号、银行卡），P1 引入 NLP NER |

#### 5. RagService — RAG 检索服务

| 属性 | 说明 |
|------|------|
| **职责** | 对合同文本分块→向量化→检索相关法条；本地不足时走网络兜底 |
| **接口** | `List<LawEntry> retrieve(String chunkContent)` |
| **内部流程** | 语义分块 → Embedding → Chroma 余弦检索（阈值 0.75）→ 未命中则爬取 flk.npc.gov.cn → 缓存回本地库 |

#### 6. AgentOrchestrator — Agent 编排器

| 属性 | 说明 |
|------|------|
| **职责** | 编排 Agent A→B→C 的工作流，控制并发与超时 |
| **依赖** | Spring AI ChatClient, Semaphore(10) |
| **工作流** | Agent A（分类定调）→ 语义分块 → RAG 检索 → Agent B（并发扫描，最大 10 路）→ Agent C（汇总报告）|

#### 7. TaskStateMachine — 任务状态机

| 属性 | 说明 |
|------|------|
| **职责** | 管理审查任务的状态转换与生命周期 |
| **状态集** | PENDING → PARSING → RETRIEVING → REVIEWING → SUMMARIZING → SUCCESS / FAILED |
| **超时阈值** | PARSING ≤ 30s, RETRIEVING ≤ 30s, REVIEWING ≤ 120s, SUMMARIZING ≤ 30s, 总计 ≤ 5min |

**状态转换矩阵：**

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

> 注：PARSING 阶段失败不重试（解析逻辑确定性高，重试无意义），直接标记 FAILED。
> 状态到达 SUCCESS 或 FAILED 后，后台定时任务在 24 小时内将 `preview_text` 置空以释放存储。

#### 8. SseService — SSE 推送服务

| 属性 | 说明 |
|------|------|
| **职责** | 维护 SSE 连接映射，推送阶段进度事件 |
| **存储** | `ConcurrentHashMap<taskId, SseEmitter>` |
| **接口** | `void send(String taskId, SseEvent event)`, `SseEmitter connect(String taskId)` |

### 组件间接口/契约定义

#### RESTful API 一览

| 方法 | 路径 | 说明 | 请求体 / 参数 | 响应 |
|------|------|------|---------------|------|
| POST | `/api/v1/auth/register` | 用户注册 | `{username, password}` | `{userId, token, refreshToken}` |
| POST | `/api/v1/auth/login` | 用户登录 | `{username, password}` | `{token, refreshToken}` |
| POST | `/api/v1/auth/refresh` | Token 刷新 | `{refreshToken}` | `{token, refreshToken}` |
| POST | `/api/v1/contract/upload` | 上传合同文件（可选择是否脱敏，默认脱敏后返回预览） | MultipartFile + `desensitize`(boolean, query param, default true) | `{taskId, previewText}` |
| POST | `/api/v1/contract/{taskId}/submit` | 确认预览后提交审查 | — | `{taskId}` |
| GET | `/api/v1/contract/{taskId}/status` | 查询任务状态 | — | `{taskId, status, progress}` |
| GET | `/api/v1/contract/{taskId}/report` | 获取审查报告 | — | `{taskId, summary, risks[]}` |
| GET | `/api/v1/contract/history` | 获取历史记录（按创建时间降序） | `?page=1&size=10` | `{tasks[], total, page, size}` |
| POST | `/api/v1/contract/{taskId}/retry` | 手动重试失败任务 | — | `{taskId}` |
| GET | `/api/v1/contract/{taskId}/progress` | SSE 进度推送 | — | SSE Event Stream |
| GET | `/api/v1/contract/{taskId}/file` | 下载脱敏后的合同文件 | — | 文件流 |
| GET | `/api/v1/contract/{taskId}/report/pdf` | 下载 PDF 报告（P1） | — | 文件流 |

> **上传 / 提交流程**：
> 1. `POST /upload` 解析后根据 `desensitize` 参数（默认 `true`）决定是否脱敏，创建状态为 `PENDING` 的审查任务（脱敏或原始文本持久化至 `preview_text`），返回 `taskId` 及预览文本。此时暂不扣减配额。
> 2. `POST /{taskId}/submit` 确认后提交审查：通过 Lua 脚本原子扣减配额（判断余额 > 0 再 DECR，余额不足返回 1003）→ 投递 MQ → 消费者消费并变更状态为 `PARSING`。
> 3. 若用户未调用 submit，后台定时任务 1 小时后自动清理该 PENDING 任务及临时数据。

#### Agent 数据契约

```
Agent A:
  Input:  { fullText: String }
  Output: { contractType: String, userStance: String, reviewStrategy: String }

Agent B:
  Input:  { chunkContent: String, relatedLaws: String[], strategy: String }
  Output: { risks: [{ clauseIndex: Int, riskLevel: "HIGH"|"MEDIUM"|"LOW",
                      riskType: String, description: String,
                      suggestion: String, relatedLaws: String[] }] }

Agent C:
  Input:  { risks: AgentB.risks[], contractType: String }
  Output: { summary: String, riskCount: { high: Int, medium: Int, low: Int },
            risks: AgentB.risks[] }
```

#### 风险等级定义

| 等级 | 含义 | 示例 |
|------|------|------|
| **HIGH** (高) | 条款明显违法或严重损害用户核心权益 | 约定「工伤概不负责」 |
| **MEDIUM** (中) | 条款可能存在不公平，需进一步协商 | 约定「争议须在甲方所在地法院管辖」 |
| **LOW** (低) | 条款表述不规范但风险可控 | 缺少争议解决方式的约定 |

#### SSE 事件契约

```
事件类型: progress / complete / error
data 结构:
  - progress: { status: String, progress: Int, message: String }
  - complete: { status: "completed", progress: 100, message: String, reportId: String }
  - error:    { status: "failed", progress: -1, message: String }
```

> SSE 推送端到端延迟 ≤ 3s，MQ 消费者在阶段变更后通过 `SseEmitter` 直接推送，不经过额外消息链路。

#### MQ 消息契约

```
队列: contract.review.queue
死信队列: contract.review.dlx
消息体: { taskId: String, userId: Long, retryCount: Int }
消费逻辑: 读取消息 → 执行状态机流转 → 成功 ACK / 失败 NACK 入 DLX
prefetch: 10 (配合 Semaphore(10) 控制 Agent B 并发)
```

**重试计数更新机制**：
- 初始消息 `retryCount = 0`
- 消费失败 NACK 时，RabbitMQ 自动将消息路由到 DLX
- DLX 消费者检查 `retryCount < maxRetryCount`（默认 3，可配置）
  - 若未超限：`retryCount++`，重新投递到主队列
  - 若超限：标记任务 FAILED，不再重试
- `retryCount` 在消息重新投递时递增，存储在消息属性中

### 交互流程说明

#### 核心审查流程（正常路径）

```
1. 用户 POST /upload → 服务端解析 PDF → 可选脱敏（根据 desensitize 参数）→ 存储文本 → 返回 taskId + previewText
2. 用户 POST /{taskId}/submit → 服务端 Lua 原子扣减配额 → 投递 MQ
3. MQ 消费者消费消息 → 状态 PENDING → PARSING
4. 解析阶段 → 状态 PARSING → RETRIEVING（SSE 推送 parsing 进度）
5. RAG 检索阶段 → 语义分块 + 向量检索法条（SSE 推送 retrieving 进度）
6. 风险扫描阶段 → Agent A 分类定调 → Agent B 逐块并发扫描（SSE 推送 reviewing 进度）
7. 汇总阶段 → Agent C 汇总生成报告 → 持久化至 DB（SSE 推送 summarizing 进度）
8. 状态 → SUCCESS（SSE 推送 complete 事件）
```

#### 配额扣减与回滚

**扣减 Lua 脚本**（调用 `POST /submit` 时原子执行）：

```lua
-- KEYS[1] = user:quota:{userId}
-- ARGV[1] = 1 (扣减数量)
-- 原子操作：先判断余额 > 0 再扣减，避免竞态条件
local quota = redis.call('GET', KEYS[1])
if not quota or tonumber(quota) <= 0 then
    return -1  -- 余额不足
end
-- 使用 DECRBY 原子扣减
local newQuota = redis.call('DECRBY', KEYS[1], ARGV[1])
return newQuota
```

**回滚机制**（任务最终失败时触发）：

```
任务进入 FAILED 状态
  → Redis INCR user:quota:{userId}        -- 回滚配额
  → DB 乐观锁 UPDATE user.review_quota    -- 同步 DB（防止 Redis 丢失）
       SET review_quota = review_quota + 1, version = version + 1
       WHERE id = ? AND version = ?
```

> 注：由于采用 DECR 前判断方案，正常情况下不会出现负数配额；回滚机制仅用于任务失败时恢复配额。

---

## Data Models

### 核心实体关系

```
User (1) ──→ (N) ReviewTask
ReviewTask (1) ──→ (N) RiskItem
ReviewTask (1) ──→ (1) ReviewReport
```

### 数据模型定义

#### 用户 (User)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 用户名 |
| password_hash | VARCHAR(256) | NOT NULL | bcrypt 哈希 |
| review_quota | INT | NOT NULL, DEFAULT 10 | 剩余审查次数 |
| version | INT | NOT NULL, DEFAULT 0 | 乐观锁版本号 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

#### 审查任务 (ReviewTask)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | FK → user.id, NOT NULL | 用户 ID |
| file_name | VARCHAR(255) | NOT NULL | 原始文件名 |
| file_size | BIGINT | NOT NULL | 文件大小（字节） |
| preview_text | MEDIUMTEXT | NULLABLE | 脱敏后预览文本 |
| file_url | VARCHAR(1024) | NULLABLE | 脱敏后文件 MinIO 路径 |
| contract_type | VARCHAR(50) | NULLABLE | 合同类型（Agent A 回填） |
| user_stance | VARCHAR(50) | NULLABLE | 用户立场（Agent A 回填） |
| status | ENUM('PENDING','PARSING','RETRIEVING','REVIEWING','SUMMARIZING','SUCCESS','FAILED')| NOT NULL, DEFAULT 'PENDING' | 任务状态 |
| progress | INT | NOT NULL, DEFAULT 0 | 进度百分比 0-100，失败时为 -1 |
| error_msg | TEXT | NULLABLE | 失败原因 |
| total_chunks | INT | NULLABLE | 总条款数 |
| reviewed_chunks | INT | NULLABLE, DEFAULT 0 | 已审查条款数 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| completed_at | DATETIME | NULLABLE | 完成时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

#### 风险项 (RiskItem)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| task_id | BIGINT | FK → review_task.id, NOT NULL | 关联任务 ID |
| clause_index | INT | NOT NULL | 条款序号 |
| clause_content | TEXT | NOT NULL | 条款原文 |
| risk_level | ENUM('HIGH','MEDIUM','LOW') | NOT NULL | 风险等级 |
| risk_type | VARCHAR(100) | NOT NULL | 风险类型 |
| description | TEXT | NOT NULL | 风险描述 |
| suggestion | TEXT | NOT NULL | 修改建议 |
| related_laws | JSON | NULLABLE | 相关法条数组 |
| created_at | DATETIME | NOT NULL | 创建时间 |

#### 审查报告 (ReviewReport)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| task_id | BIGINT | FK → review_task.id, UNIQUE, NOT NULL | 关联任务 ID |
| summary | TEXT | NOT NULL | 审查总结 |
| risk_count_high | INT | NOT NULL, DEFAULT 0 | 高风险数量 |
| risk_count_medium | INT | NOT NULL, DEFAULT 0 | 中风险数量 |
| risk_count_low | INT | NOT NULL, DEFAULT 0 | 低风险数量 |
| report_json | JSON | NOT NULL | 完整报告（含风险项详情） |
| pdf_url | VARCHAR(1024) | NULLABLE | PDF 报告下载地址 |
| created_at | DATETIME | NOT NULL | 创建时间 |

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

#### 操作审计日志表 (operation_log)

预留表结构，V1.0 仅做日志输出（配合管理后台 P1 开发查询接口）：

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | FK → user.id, NOT NULL | 操作用户 ID |
| action | VARCHAR(50) | NOT NULL | 操作类型（REGISTER / UPLOAD / SUBMIT / VIEW_REPORT / RETRY） |
| task_id | BIGINT | NULLABLE | 关联任务 ID（可选） |
| detail | JSON | NULLABLE | 操作详情 |
| ip_address | VARCHAR(45) | NULLABLE | 客户端 IP |
| created_at | DATETIME | NOT NULL | 操作时间 |

> MVP 范围：V1.0 仅通过 AOP 切面输出结构化日志至文件，查询接口随管理后台（P1）开发。

### 索引设计

| 表名 | 索引字段 | 索引类型 | 说明 |
|------|----------|----------|------|
| user | username | UNIQUE | 用户名唯一索引 |
| review_task | user_id | INDEX | 加速按用户查询任务 |
| review_task | status | INDEX | 加速按状态筛选 |
| review_task | created_at | INDEX | 支持按时间排序 |
| risk_item | task_id | INDEX | 加速按任务查询风险项 |
| risk_item | (task_id, risk_level) | COMPOSITE | 联合查询按等级过滤 |
| review_report | task_id | UNIQUE | 任务与报告一对一关联 |

### 数据流描述

#### 上传阶段数据流

```
用户上传文件
  → MultipartFile + desensitize(boolean, default true) → Controller
  → FileParser.parse() → 校验格式/大小 → 提取纯文本
  → 若 desensitize=true: DesensitizationEngine.desensitize() → 敏感信息替换为 ***
  → 若 desensitize=false: 跳过脱敏，保留原始文本
  → 处理后文本上传 MinIO → 获取 file_url
  → INSERT review_task (status=PENDING, preview_text=处理后文本, file_url)
  → 返回 { taskId, previewText }
```

#### 审查阶段数据流

```
用户确认提交 (POST /submit)
  → Lua 脚本原子扣减 Redis 配额
  → 投递消息至 RabbitMQ (taskId, userId)
  → MQ 消费者消费
    → UPDATE review_task.status = PARSING
    → 解析完成 → UPDATE status = RETRIEVING
    → RAG 检索:
        → 语义分块 Chunking
        → 各 Chunk Embedding → Chroma 检索
        → 未命中 → 网络爬取法条 → 缓存回 Chroma
    → UPDATE status = REVIEWING
    → Agent 编排:
        → Agent A: 分类定调
        → Agent B: 每个 Chunk + 法条 → 并发扫描风险 (Semaphore 控制最大 10 路)
        → Agent C: 汇总风险 → 生成报告
    → INSERT risk_items (批量)
    → INSERT review_report
    → UPDATE review_task.status = SUCCESS, completed_at = NOW()
  → SSE 推送各阶段进度
```

---

## Correctness Properties

### 不变式 (Invariants)

| 不变式 | 说明 | 违反后果 |
|--------|------|----------|
| `review_quota >= 0` | 用户剩余审查次数永不为负 | Redis Lua 原子判断 + DECR 后负数回滚 |
| `status` 转换严格遵循状态机 | 不允许跳跃或回退到无效状态 | 状态机校验，非法转换抛出 1005 错误 |
| `progress ∈ [0, 100]` (失败时 `-1`) | 进度百分比始终在合法范围内 | 写入前校验，非法值拒绝更新 |
| `report_json` 与 `risk_item` 数据一致 | 报告中的风险计数与实际风险项匹配 | 事务写入，冗余字段在写入时同步维护 |
| `taskId` 全局唯一 | 每个审查任务有唯一标识 | 数据库自增主键 + UUID 前缀 |

### 并发控制

| 场景 | 控制策略 | 技术实现 |
|------|----------|----------|
| 配额扣减 | 原子操作 + 负数防护 | Redis Lua 脚本，先判断余额 > 0 再 DECR |
| DB 更新乐观锁 | 版本号机制 | `UPDATE ... SET version = version + 1 WHERE version = ?` |
| Agent B 并发数 | 信号量控制 | `Semaphore(10)` 限制最大同时调用数 |
| MQ 消费 | prefetch 限制 | connection factory 设置 prefetch = 10 |

### 边界情况处理

| 边界场景 | 处理策略 |
|----------|----------|
| 空文件或全空白 PDF | 解析后文本为空 → 返回 400 + 错误码 1001 |
| 文件刚好 20MB | 允许上传，在边界值处进行 `<=` 比较 |
| 超长合同（> 50 页） | 按 Chunk 分片处理，Map-Reduce 架构天然支持，仅耗时增加 |
| 无匹配法条 | RAG 本地库未命中 → 网络搜索兜底 → 仍无结果 → 返回空法条列表，Agent B 仅基于通用法律常识审查 |
| 用户重复提交同一 taskId | 状态机判断当前状态，非 PENDING 状态返回 1005 |
| Token 过期 | 前端拦截 401 → 使用 Refresh Token 刷新 → 重放请求 |
| SSE 连接断开 | 客户端指数退避重连（1s → 2s → 4s → 8s → 16s → 30s），最多 5 次 → 降级为轮询 GET /status |
| 并发提交导致配额恰好为 0 | Lua 脚本原子操作确保不会多扣，DECR 后若 < 0 则 INCR 回滚 |
| 上传后未 submit | 定时任务 1h 后清理 PENDING 任务及 MinIO 临时文件 |

---

## Error Handling

### 错误类型分类

| 错误类别 | 错误码 | 说明 | 示例 |
|----------|--------|------|------|
| **输入校验** | 1001 | 文件格式不支持 | 上传 .exe 文件 |
| **输入校验** | 1002 | 文件大小超限 | 上传 30MB PDF |
| **业务限制** | 1003 | 审查次数不足 | 用户配额为 0 |
| **业务限制** | 1004 | 任务不存在 | 查询不存在的 taskId |
| **业务限制** | 1005 | 状态不允许操作 | 对已完成的 task 重试 |
| **业务限制** | 1008 | 请求频率超限 | 单 IP 超 QPS |
| **业务限制** | 1009 | 任务执行超时 | 审查总耗时 > 5min |
| **外部依赖** | 1006 | LLM API 调用失败 | OpenAI API 返回 5xx |
| **外部依赖** | — | Chroma 连接失败 | 向量库宕机 |
| **外部依赖** | — | MinIO 存储失败 | 对象存储不可用 |
| **系统内部** | 500 | 未预期的服务端异常 | NullPointerException |

### 错误处理策略

#### 统一响应格式

```json
{
  "code": 1001,
  "message": "文件格式不支持，仅允许 PDF/Word 格式",
  "timestamp": "2026-07-06T10:30:00Z",
  "requestId": "req-xxx"
}
```

#### 各层错误处理

| 层级 | 处理方式 | 说明 |
|------|----------|------|
| **Controller 层** | `@RestControllerAdvice` 全局异常捕获 | 将业务异常转为统一响应格式 |
| **Service 层** | 抛出业务异常 (`BusinessException(code, message)`) | 携带错误码，由上层统一处理 |
| **MQ 消费者** | 捕获异常 → NACK + 判断重试次数 → 入 DLX 或标记 FAILED | 指数退避重试最多 3 次 |
| **AI 引擎层** | 捕获 LLM API 异常 → 判断是否可重试 | 网络抖动重试，语法错误不重试 |

#### 各阶段失败处理

| 阶段 | 失败处理 |
|------|----------|
| PARSING | 直接标记 FAILED（解析逻辑确定性高，重试无意义） |
| RETRIEVING | 重试最多 3 次（网络原因可恢复），超限则 FAILED |
| REVIEWING | 重试最多 3 次（LLM API 抖动可恢复），超限则 FAILED |
| SUMMARIZING | 重试最多 3 次，超限则 FAILED |

### 可用性保证

| 指标 | 目标值 | 实现方式 |
|------|--------|----------|
| 系统可用性 | ≥ 99.5% | 模块化单体 + 依赖中间件高可用部署 |
| 任务失败自动重试 | 指数退避 3 次 | Spring Retry + RabbitMQ DLX |
| 数据持久化 | 每日自动备份 | MySQL 主从备份 + MinIO 定期备份 |
| 实时进度延迟 | ≤ 3s | SSE 通过 SseEmitter 直接推送，不经过额外消息链路 |

### 降级与恢复机制

| 场景 | 降级策略 | 恢复方式 |
|------|----------|----------|
| LLM API 超时 | 自动重试（指数退避），第 4 次标记 FAILED | 用户手动重试 |
| Chroma 向量库不可用 | 跳过本地检索，直接使用网络搜索兜底 | Chroma 恢复后自动生效 |
| Redis 不可用 | 配额校验降级为查 DB（防止误扣），限流降级为不限制 | Redis 恢复后自动切换回 |
| RabbitMQ 不可用 | 任务降级为同步处理（`@Async` + 直接调用） | MQ 恢复后切换回异步模式 |
| MinIO 不可用 | 失败并返回 500，不降级（存储不可降级） | 运维恢复 MinIO 后重试 |
| 网络法条搜索失败 | 返回空法条列表，Agent B 仅基于通用法律知识审查 | 无需恢复，下次重试自动重试 |

---

## Testing Strategy

### 测试层级

#### 1. 单元测试 (Unit Test)

| 模块 | 测试框架 | 关键测试点 |
|------|----------|------------|
| 脱敏引擎 | JUnit 5 + ParameterizedTest | 正则匹配准确率、边界情况（空字符串、无敏感信息、全敏感信息） |
| 状态机 | JUnit 5 | 所有合法状态转换路径、所有非法转换路径 |
| 配额扣减 | JUnit 5 + Mockito | Lua 脚本逻辑、余额不足返回 -1、并发扣减正确性 |
| 文件解析器 | JUnit 5 | PDF 提取、Word 提取、格式校验、大小校验 |
| JWT 工具类 | JUnit 5 | Token 签发、验证、过期、篡改检测 |

**脱敏引擎测试示例**：

```java
@ParameterizedTest
@CsvSource({
    "'甲方：张三，身份证号：110101199001011234', '甲方：***，身份证号：***'",
    "'联系电话：13800138000', '联系电话：***'",
    "'无敏感信息', '无敏感信息'",
    "'', ''",
    "'账号：6222021234567890123', '账号：***'"
})
void testDesensitize(String input, String expected) {
    assertEquals(expected, desensitizationEngine.desensitize(input));
}
```

#### 2. 集成测试 (Integration Test)

| 测试范围 | 工具 | 验证内容 |
|----------|------|----------|
| REST API 全流程 | Spring MockMvc + TestRestTemplate | 注册→登录→上传→提交→SSE→报告查询 |
| MQ 消息流转 | Spring Rabbit Test | 消息投递 + 消费 + 死信队列重试 |
| RAG 检索链路 | Testcontainers (Chroma) | 嵌入 → 存储 → 检索 → 相似度排序 |
| DB 事务 | @SpringBootTest + @Transactional | 配额扣减 + 任务创建的一致性 |
| Redis Lua 脚本 | Embedded Redis + Jedis | 原子扣减、负数防护 |

**API 集成测试示例**：

```java
@Test
void testFullReviewFlow() {
    // 1. 注册
    var registerResp = restTemplate.postForEntity("/api/v1/auth/register",
        new RegisterRequest("testUser", "password123"), AuthResponse.class);
    assertThat(registerResp.getStatusCode()).isEqualTo(200);

    // 2. 上传合同
    var uploadResp = restTemplate.postForEntity("/api/v1/contract/upload",
        createFilePart("test.pdf", "甲方：张三..."), UploadResponse.class);
    assertThat(uploadResp.getBody().previewText()).doesNotContain("张三");

    // 3. 提交审查
    var submitResp = restTemplate.postForEntity(
        "/api/v1/contract/" + uploadResp.getBody().taskId() + "/submit",
        null, SubmitResponse.class);
    assertThat(submitResp.getStatusCode()).isEqualTo(200);

    // 4. 轮询等待完成
    await().atMost(120, SECONDS).until(() -> {
        var statusResp = restTemplate.getForEntity(
            "/api/v1/contract/" + uploadResp.getBody().taskId() + "/status",
            StatusResponse.class);
        return statusResp.getBody().status().equals("SUCCESS");
    });

    // 5. 获取报告
    var reportResp = restTemplate.getForEntity(
        "/api/v1/contract/" + uploadResp.getBody().taskId() + "/report",
        ReportResponse.class);
    assertThat(reportResp.getBody().risks()).isNotEmpty();
}
```

#### 3. 端到端测试 (E2E Test)

| 场景 | 工具 | 验证内容 |
|------|------|----------|
| 用户完整使用路径 | Playwright / Cypress | 用户注册 → 登录 → 上传 → 确认 → 查看进度 → 查看报告 |
| SSE 实时反馈 | Playwright (EventSource) | 各阶段事件按顺序到达 |
| 错误场景 | 手动构造异常文件 | 大文件 → 1002；非 PDF → 1001；Token 过期 → 401 |

### 关键场景测试用例

| 编号 | 场景 | 步骤 | 预期结果 |
|------|------|------|----------|
| TC1 | 正常 PDF 审查 | 上传合法 PDF → 确认提交 → 等待完成 | 成功返回报告，风险等级正确 |
| TC2 | 敏感信息脱敏 | 上传含姓名、身份证的合同 | 预览文本中敏感信息替换为 `***` |
| TC3 | 配额耗尽 | 用尽 10 次配额后再次提交 | 返回 403 + 错误码 1003 |
| TC4 | 非法状态操作 | 对已完成的 task 再次提交 | 返回 409 + 错误码 1005 |
| TC5 | SSE 进度推送 | 连接 SSE → 确认提交 | 按顺序收到 parsing→retrieving→reviewing→summarizing→complete 事件 |
| TC6 | LLM API 超时重试 | 模拟 API 超时 2 次，第 3 次成功 | 任务最终成功，状态机进入 SUCCESS |
| TC7 | 失败后手动重试 | 标记 FAILED → 调用 retry | 新任务进入 PENDING，重新消费 |
| TC8 | 并发提交 10 个任务 | 同一用户连续提交 10 个审查 | 全部成功，配额归零 |
| TC9 | 上传后取消提交 | 上传后 1 小时不提交 | 定时任务清理临时数据 |
| TC10 | 空文件上传 | 上传空 PDF | 返回 400 + 错误码 1001 |

### 性能与压力测试目标

| 指标 | 目标值 | 测试场景 | 施压工具 |
|------|--------|----------|----------|
| **并发用户数** | ≥ 100 同时提交 | 100 个线程同时上传 + 提交合同 | JMeter |
| **审查吞吐量** | ≥ 0.5 份/min (单节点) | 持续加载 30min，观察吞吐量曲线 | JMeter |
| **轻量接口吞吐** | ≥ 200 QPS (status/report/history) | 纯查询接口压力测试 | JMeter |
| **审查耗时 (P95)** | ≤ 120s (20 页合同) | 不同页数合同混合场景 | JMeter |
| **SSE 推送延迟** | ≤ 3s (P99) | 端到端事件时间戳对比 | 自定义脚本 |
| **REST API 响应 (P95)** | ≤ 500ms (登录/上传/状态/报告) | 混合负载场景 | JMeter |
| **CPU 使用率** | ≤ 70% (目标负载下) | 结合 jstack + Prometheus 监控 | JMeter + Grafana |
| **内存使用率** | ≤ 80% (目标负载下) | 堆内存与非堆内存监控 | JMeter + Grafana |
| **错误率** | ≤ 0.1% (所有请求) | 全链路混合负载 | JMeter |
| **脱敏准确率** | ≥ 90% | 基于 SRS 脱敏规则的测试集验证 | JUnit ParameterizedTest |

> 以上为目标值，实际压测结果应在 CI/CD 流程中基线化，每次发版对比回归。

### 测试工具与框架

| 层级 | 工具/框架 | 用途 |
|------|-----------|------|
| 单元测试 | JUnit 5 + Mockito | Service/Utils 层测试 |
| 参数化测试 | JUnit 5 @ParameterizedTest | 脱敏正则、状态机转换 |
| 集成测试 | Spring Boot Test + TestRestTemplate | API 全流程 |
| 嵌入式中间件 | Testcontainers | Chroma / MySQL / Redis 集成 |
| MQ 测试 | Spring Rabbit Test + RabbitMQ Testcontainer | MQ 消息流转 |
| E2E 测试 | Playwright | 前端-后端全链路 |
| 性能测试 | JMeter | 并发 ≥ 100 用户、审查耗时 ≤ 120s |
| 代码质量 | SpotBugs + Checkstyle | 静态分析、编码规范 |
| 覆盖率 | JaCoCo | 单元测试覆盖率 ≥ 80% |
