# 基于 Multi-Agent 与 RAG 的智能合同风险审查系统 — 需求分析文档

## 修订记录

| 版本 | 日期 | 作者 | 修订内容 |
|------|------|------|----------|
| V1.0 | 2026-07-05 | — | 初始版本 |
| V1.1 | 2026-07-05 | — | 全面审查修复：术语补充、架构描述修正、Chroma 统一、F1.2/F1.3 优先级调整、SSE 事件统一、工作流图修正、状态机回退路径、下载端点与错误码补充 |
| V1.2 | 2026-07-05 | — | Spec 审查修复：上传/提交流程拆分、状态机补齐 RETRIEVING/SUMMARIZING 状态、SSE 事件类型区分、姓名脱敏正则增强、数据模型补充索引、阈值与密码算法明确 |
| V1.3 | 2026-07-05 | — | 完整需求修复：F1.4 优先级修正、工作流图补充 RAG 链路、存储策略与配额扣减明确、超时熔断与临时文件清理、错误码 1007 修正、SSE 重连说明、Map-Reduce 与 Agent 映射、并发控制 |
| V1.4 | 2026-07-05 | — | Spec 审查修复：补充 F3.x 编号、统一 fileId/taskId 术语、上传生命周期说明、review_task 增加 completed_at、余弦相似度阈值明确、下载接口说明修正、progress 取值范围统一、SSE 示例补充 error 事件 |

---

## 1. 文档概述

### 1.1 目的

本文档旨在明确「基于 Multi-Agent 与 RAG 的智能合同风险审查系统」的业务背景、功能需求、非功能性需求、系统架构及技术选型，为后续设计、开发、测试与验收提供统一依据。

### 1.2 适用范围

本文档面向项目全体干系人，包括产品经理、架构师、后端开发工程师、前端开发工程师、测试工程师及运维人员。

### 1.3 术语表

| 术语 | 说明 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| Multi-Agent | 多智能体协作架构，各 Agent 分工处理不同子任务 |
| Agent | 智能体，独立完成特定子任务的 LLM 调用单元 |
| SSE | Server-Sent Events，服务端推送技术 |
| LLM | Large Language Model，大语言模型 |
| DLX | Dead Letter Exchange，RabbitMQ 死信交换机 |
| Chunking | 文本分块，将长文本按语义切分为更小的段落 |
| Map-Reduce | 先分片处理再汇总合并的并行计算模式 |
| Embedding | 向量化，将文本转换为语义向量表示的技术 |
| 乐观锁 | 通过版本号机制实现并发控制，避免数据竞争 |
| 脱敏 | 对敏感信息（姓名、身份证号等）进行替换或遮蔽处理 |

---

## 2. 项目背景与业务痛点

### 2.1 业务背景

在现实生活中，普通 C 端用户（如应届毕业生租房、求职者签劳动合同、自由职业者签外包协议）在面对冗长、专业的法律文本时，往往缺乏风险识别能力，极易陷入「霸王条款」或法律陷阱。而传统的法律服务咨询成本高、效率低。

### 2.2 业务痛点

| 痛点 | 描述 |
|------|------|
| 专业知识门槛高 | 普通用户无法理解法律条款的潜在风险 |
| 传统咨询成本高 | 律师咨询按小时计费，C 端用户难以负担 |
| 效率低下 | 人工审查一份合同通常需要数小时甚至数天 |
| 隐私顾虑 | 用户不愿将含有个人敏感信息的合同随意提交给不可信的第三方 |

### 2.3 项目目标

利用 LLM 的自然语言理解能力，结合 RAG 技术引入权威法律法规，打造一个低成本、高可用、注重隐私保护的 C 端智能法务助理，帮助用户一键识别合同风险并获取修改建议。

---

## 3. 用户角色

| 角色 | 描述 | 核心需求 |
|------|------|----------|
| C 端普通用户 | 应届毕业生、求职者、自由职业者等 | 上传合同、获取风险报告、了解修改建议 |
| 系统管理员 | 平台运维人员 | 监控系统运行状态、管理用户配额、查看日志 |

> 注：V1.0 仅面向 C 端普通用户，管理后台为非 MVP 功能。

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
│         AI 核心引擎层 (Spring AI)             │
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
│  MySQL · Redis · Chroma · MinIO/OSS        │
└─────────────────────────────────────────────┘
```

---

## 5. 功能需求

### 5.1 文档解析与隐私脱敏模块

#### 5.1.1 功能描述

支持 PDF / Word 格式合同文件上传，提取纯文本内容，并在送入大模型前对敏感信息进行自动脱敏。

#### 5.1.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F1.1 | PDF 文件解析 | P0 | 使用 Apache PDFBox 提取 PDF 文本内容；上传后先返回脱敏预览（taskId），用户确认后再提交审查 |
| F1.2 | Word 文件解析 | P1 | 使用 Apache POI 提取 .doc / .docx 文本内容 |
| F1.3 | 敏感信息脱敏 | P0 | 通过正则表达式进行脱敏（P0）；NLP 实体识别作为增强手段（P1），将姓名、身份证号、手机号、银行卡号替换为 `***` |
| F1.4 | 脱敏结果预览与确认提交 | P0 | 上传后返回脱敏文本供用户预览确认，用户通过确认接口再正式提交审查任务 |
| F1.5 | 文件格式校验 | P0 | 仅允许 PDF / Word，限制文件大小 ≤ 20MB；性能目标 120s 适用于典型合同（≤ 20 页），超长文档耗时可能相应增加 |

#### 5.1.3 脱敏规则

| 敏感类型 | 识别方式（P0） | 识别方式（P1 增强） | 替换格式 |
|----------|-----------------|---------------------|----------|
| 姓名 | 正则 `[\u4e00-\u9fa5]{2,4}(?:先生\|女士\|同志)`（含称呼）；`[\u4e00-\u9fa5]{2,4}(?=[，。；、：\s]|$)`（独立姓名） | NLP 实体识别 | `***` |
| 身份证号 | 正则 `\d{17}[\dXx]` | NLP 实体识别 | `***` |
| 手机号 | 正则 `1[3-9]\d{9}` | NLP 实体识别 | `***` |
| 银行卡号 | 正则 `\d{16,19}` | NLP 实体识别 | `***` |

### 5.2 基于 RAG 的法条检索与比对模块

#### 5.2.1 功能描述

将合同文本按语义分块，对每个条款提取关键词，从向量数据库中检索最相关的《民法典》《劳动合同法》等法条，为后续 AI 审查提供事实依据。

#### 5.2.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F2.1 | 合同文本语义分块 | P0 | 按条款/段落对文本进行 Chunking，保留上下文关联 |
| F2.2 | 法条向量化入库 | P0 | 将权威法律法规文本向量化后存入 Chroma |
| F2.3 | 相似法条检索（混合检索） | P0 | 先查本地向量库，若余弦相似度低于阈值（默认 0.75，可配置）则 fallback 到网络搜索 |
| F2.4 | Prompt 组装 | P0 | 将「合同条款 + 检索法条」组装为结构化 Prompt |
| F2.5 | 法条版本管理 | P1 | 支持法律法规的更新与版本追溯 |
| F2.6 | 网络法条搜索（兜底） | P1 | 本地库未命中时，调用搜索引擎检索法律条文；命中结果缓存回本地库（TTL 默认 7 天，可配置） |

#### 5.2.3 混合 RAG 流程

```
合同文本 → 语义分块 → 生成向量 → 查询本地向量库
                                      │
                             ┌────────┴────────┐
                             │ 相似度 ≥ 阈值？  │
                             └────────┬────────┘
                                ┌─────┴─────┐
                                │           │
                             是 │           │ 否
                                │           ▼
                                │    网络搜索法条
                                │           │
                                │     ┌─────┴─────┐
                                │     │ 是否命中？  │
                                │     └─────┬─────┘
                                │      是   │   否
                                │       │   │
                                │       ▼   ▼
                                │  缓存回本地库  返回空
                                │           │
                                └─────┬─────┘
                                      ▼
                             组装 Prompt → LLM 审查
```

### 5.3 Multi-Agent 风险审查工作流

#### 5.3.1 功能描述

通过多个 LLM Agent 的分工协作，完成从合同分类到风险扫描再到报告生成的全流程。

#### 5.3.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F3.1 | 合同分类与定调 | P0 | Agent A 识别合同类型、用户立场，确定审查侧重点 |
| F3.2 | 逐条风险扫描 | P0 | Agent B 对每个 Chunk 结合 RAG 法条进行风险扫描，评定风险等级 |
| F3.3 | 报告生成 | P0 | Agent C 汇总所有风险点，生成结构化 JSON 审查报告 |
| F3.4 | 工作流编排 | P0 | 按 Agent A → 分块 → RAG → Agent B → Agent C 顺序编排，Agent B 并发受信号量控制（默认最大 10 路） |

#### 5.3.3 Agent 设计

| Agent | 职责 | 输入 | 输出 |
|-------|------|------|------|
| Agent A (分类与定调) | 识别合同类型（租赁/劳动/外包等），确认用户立场（租客/房东等），确定审查侧重点 | 合同全文文本 | 合同类型 + 用户立场 + 审查策略 |
| Agent B (风险扫描) | 逐条分析条款，识别违约金过高、免责条款不合理、管辖法院不利等风险，评定风险等级 | 单个条款 + 相关法条 | 风险点列表（含等级） |
| Agent C (报告生成) | 汇总所有风险点，生成结构化报告 | 全部风险点 | JSON 格式的审查报告 |

#### 5.3.4 风险等级定义

| 等级 | 含义 | 示例 |
|------|------|------|
| 高 | 条款明显违法或严重损害用户核心权益 | 约定「工伤概不负责」 |
| 中 | 条款可能存在不公平，需进一步协商 | 约定「争议须在甲方所在地法院管辖」 |
| 低 | 条款表述不规范但风险可控 | 缺少争议解决方式的约定 |

#### 5.3.5 工作流编排

```
Agent A (分类定调)
    │
    ▼
合同分块 ──→ 对每个 Chunk ──→ RAG 检索法条 ──→ Agent B (风险扫描)
                                                      │
                                                      ▼
                                                Agent C (报告生成) ──→ 结构化 JSON 报告
```

### 5.4 异步任务调度与实时反馈模块

#### 5.4.1 功能描述

审查任务为长耗时操作，需通过异步机制避免 HTTP 超时，并通过 SSE (Server-Sent Events) 实时推送进度。

#### 5.4.2 详细功能点

| 编号 | 功能点 | 优先级 | 说明 |
|------|--------|--------|------|
| F4.1 | 异步任务提交 | P0 | 用户确认脱敏预览后提交，立即返回 taskId |
| F4.2 | 消息队列投递 | P0 | 将任务投递至 RabbitMQ |
| F4.3 | SSE 实时进度推送 | P0 | 推送「正在解析文档→正在检索法条→正在审查第 N 条…」 |
| F4.4 | 任务状态查询 | P0 | 根据 taskId 查询当前状态 |
| F4.5 | 历史报告查看 | P1 | 用户可查看过往审查报告 |
| F4.6 | 失败手动重试 | P1 | 任务失败后用户可一键重试 |

#### 5.4.3 进度事件定义

SSE 使用三种事件类型区分不同类别的推送：

| 事件类型 | 适用场景 | 说明 |
|----------|----------|------|
| `progress` | 正常进度推送 | parsing → retrieving → reviewing → summarizing |
| `complete` | 审查完成 | 携带 reportId |
| `error` | 审查失败 | 携带错误信息 |

阶段标识通过 `status` 字段传递，各阶段定义如下：

| 事件类型 | status 值 | 含义 | 示例 Payload |
|----------|-----------|------|-------------|
| `progress` | `parsing` | 正在解析文档 | `{"status":"parsing","progress":10,"message":"正在解析文档..."}` |
| `progress` | `retrieving` | 正在检索法条 | `{"status":"retrieving","progress":30,"message":"正在检索相关法条..."}` |
| `progress` | `reviewing` | 正在审查条款 | `{"status":"reviewing","progress":50,"message":"正在审查第 3/10 条..."}` |
| `progress` | `summarizing` | 正在生成报告 | `{"status":"summarizing","progress":90,"message":"正在生成审查报告..."}` |
| `complete` | `completed` | 审查完成 | `{"status":"completed","progress":100,"message":"审查完成","reportId":"xxx"}` |
| `error` | `failed` | 审查失败 | `{"status":"failed","progress":-1,"message":"审查失败: xxx"}` |

> 注：客户端断开后重连时，应调用 `GET /status` 获取当前任务状态以恢复进度，SSE 通道无需支持历史事件重放。

---

## 6. 非功能性需求

### 6.1 性能需求

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 平均审查耗时 | ≤ 120s / 份 | 对 20 页以内的合同 |
| 并发支持 | ≥ 100 用户同时提交 | 通过 RabbitMQ 削峰填谷 |
| 实时进度延迟 | ≤ 3s | SSE 推送延迟 |

### 6.2 安全需求

| 需求 | 说明 |
|------|------|
| 隐私脱敏 | 敏感信息在上传后立即脱敏，原始文本解析后即丢弃，仅持久化脱敏后的文件 |
| 传输加密 | 全链路 HTTPS |
| 鉴权与防刷 | JWT 鉴权（Token 有效期 2 小时，支持刷新） + Redis 滑动窗口限流 + 用户次数资产扣减 |
| API 防滥用 | 限制单 IP / 单用户 QPS，超过阈值返回 429 |
| 操作审计 | 记录用户上传、确认提交、查看报告等关键操作日志，留存 ≥ 90 天 |
| 密码安全 | 密码使用 bcrypt 算法哈希存储，禁止明文存储 |
| 临时文件清理 | 上传后未确认提交的临时数据保留 1 小时后自动清理（Redis TTL + 定时任务兜底） |

### 6.3 可用性需求

| 指标 | 目标值 |
|------|--------|
| 系统可用性 | ≥ 99.5% |
| 任务失败自动重试 | 指数退避重试 3 次 |
| 数据持久化 | MySQL 主从备份，每日自动备份 |

### 6.4 可扩展性需求

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
| review_quota | INT | 剩余审查次数 |
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
| file_url | VARCHAR(1024) | 脱敏后文件存储路径（原始文件不入库，解析脱敏后即丢弃） |
| contract_type | VARCHAR(50) | 合同类型（租赁/劳动/外包/其他），Agent A 执行后回填 |
| user_stance | VARCHAR(50) | 用户立场，Agent A 执行后回填 |
| status | ENUM | PENDING/PARSING/RETRIEVING/REVIEWING/SUMMARIZING/SUCCESS/FAILED |
| progress | INT | 进度百分比 0-100，失败时为 -1 |
| error_msg | TEXT | 失败原因 |
| total_chunks | INT | 总条款数 |
| reviewed_chunks | INT | 已审查条款数 |
| created_at | DATETIME | 创建时间 |
| completed_at | DATETIME NULL | 完成时间（任务到达 SUCCESS/FAILED 时回填） |
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
| related_laws | TEXT | 相关法条（JSON 数组） |
| created_at | DATETIME | 创建时间 |

#### 7.2.4 审查报告表 (review_report)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| task_id | BIGINT FK | 关联任务 ID |
| summary | TEXT | 审查总结 |
| risk_count_high | INT | 高风险数量 |
| risk_count_medium | INT | 中风险数量 |
| risk_count_low | INT | 低风险数量 |
| report_json | JSON | 完整的报告 JSON |
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
| review_task | status | INDEX | 加速按状态筛选任务 |
| review_task | created_at | INDEX | 支持按时间排序 |
| risk_item | task_id | INDEX | 加速按任务查询风险项 |
| risk_item | (task_id, risk_level) | COMPOSITE INDEX | 联合查询按等级过滤 |
| review_report | task_id | UNIQUE INDEX | 任务与报告一对一关联 |

---

## 8. 接口设计

### 8.1 RESTful API

| 方法 | 路径 | 说明 | 请求体 / 参数 | 响应 |
|------|------|------|---------------|------|
| POST | `/api/v1/auth/register` | 用户注册 | `{username, password}` | `{userId, token}` |
| POST | `/api/v1/auth/login` | 用户登录 | `{username, password}` | `{token}` |
| POST | `/api/v1/contract/upload` | 上传合同文件（解析脱敏后返回预览） | MultipartFile | `{taskId, previewText}` |
| POST | `/api/v1/contract/{taskId}/submit` | 确认预览后提交审查 | — | `{taskId}` |
| GET | `/api/v1/contract/{taskId}/status` | 查询任务状态 | — | `{taskId, status, progress}` |
| GET | `/api/v1/contract/{taskId}/report` | 获取审查报告 | — | `{taskId, summary, risks[]}` |
| GET | `/api/v1/contract/history` | 获取历史记录（按创建时间降序） | `?page=1&size=10` | `{tasks[], total, page, size}` |
| POST | `/api/v1/contract/{taskId}/retry` | 手动重试失败任务 | — | `{taskId}` |
| GET | `/api/v1/contract/{taskId}/progress` | SSE 进度推送 | — | SSE Event Stream |
| GET | `/api/v1/contract/{taskId}/file` | 下载脱敏后的合同文件 | — | 文件流 |
| GET | `/api/v1/contract/{taskId}/report/pdf` | 下载 PDF 报告（P1） | — | 文件流 |

> **上传 / 提交流程说明**：
> 1. `POST /upload` 解析并脱敏文件后，**立即创建一条状态为 `PENDING` 的审查任务**，返回 `taskId` 及脱敏预览文本。此时**暂不扣减**用户审查次数。
> 2. `POST /{taskId}/submit` 确认脱敏结果无误后提交审查，系统执行：**扣减配额**（Redis DECR 预扣）→ **投递 MQ** → 消费者消费并变更状态为 `PARSING`。
> 3. 若用户未调用 submit，后台定时任务将在 1 小时后自动清理该 PENDING 任务及临时数据。

### 8.2 SSE 事件流

```
GET /api/v1/contract/{taskId}/progress
Accept: text/event-stream

event: progress
data: {"status":"parsing","progress":10,"message":"正在解析文档..."}

event: progress
data: {"status":"retrieving","progress":30,"message":"正在检索相关法条..."}

event: progress
data: {"status":"reviewing","progress":50,"message":"正在审查第 3/10 条..."}

event: progress
data: {"status":"summarizing","progress":90,"message":"正在生成审查报告..."}

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
| 1007 | 法条检索无结果 | 204 |
| 1008 | 请求频率超限 | 429 |
| 1009 | 任务执行超时 | 409 |

---

## 9. 技术选型汇总

| 层级 | 技术栈 | 说明 |
|------|--------|------|
| 前端 | Vue 3 + Element Plus + Axios | 用户界面与交互 |
| 后端框架 | Spring Boot 3 + Spring Security | 业务逻辑与鉴权 |
| AI 框架 | Spring AI / LangChain4j | LLM 调用与 Agent 编排 |
| 消息队列 | RabbitMQ | 异步任务解耦 |
| 数据库 | MySQL 8.0 | 持久化存储 |
| 缓存 | Redis | 限流、会话、热点缓存 |
| 向量数据库 | Chroma | 法条向量存储与检索 |
| 对象存储 | MinIO / 阿里云 OSS | 文件与报告存储 |
| 文档解析 | Apache PDFBox / Apache POI | PDF / Word 解析 |

---

## 10. 核心技术难点与应对策略

### 10.1 长文本处理与大模型 Token 限制

**问题：** 合同可能长达数万字，超出 LLM Context Window，全量输入 API 成本极高。

**方案：** Map-Reduce 架构

```
Map 阶段：按段落/条款切分 → 并发调用 LLM（即 Agent B，每个 Chunk 独立审查）→ 提取局部风险点
Reduce 阶段：汇总局部风险点 → 再次调用 LLM（即 Agent C，汇总生成报告）→ 生成总结性审查报告

> 前置的合同分类与定调由 Agent A 完成，不在 Map-Reduce 主链路中。Agent B 的并发数量受信号量控制（默认最大 10 路并发），避免 LLM API 过载。
```

### 10.2 大模型 API 高并发保护与防刷

**问题：** LLM API 按 Token 计费且有限流，恶意刷接口会导致服务不可用或巨额费用。

**方案：**

| 措施 | 技术实现 |
|------|----------|
| 接口限流 | Redis + Lua 脚本实现滑动窗口限流，限制单 IP / 单用户 QPS |
| 次数资产扣减 | 用户提交审查时 Redis DECR 预扣；若任务最终失败则回滚配额（Redis INCR） |
| 数据库一致性 | 数据库乐观锁防止并发超扣 |

### 10.3 异步任务状态一致性与失败重试

**问题：** MQ 消费者处理长链路任务时，可能因 LLM API 超时、网络抖动导致任务失败、状态卡死。

**方案：**

| 措施 | 技术实现 |
|------|----------|
| 状态机 | PENDING → PARSING → RETRIEVING → REVIEWING → SUMMARIZING → SUCCESS / FAILED；各阶段失败可回退重试 |
| 自动重试 | Spring Retry + RabbitMQ DLX 指数退避重试（最多 3 次） |
| 超时熔断 | 各阶段超时阈值：PARSING ≤ 30s、RETRIEVING ≤ 30s、REVIEWING ≤ 120s、SUMMARIZING ≤ 30s；总超时 ≤ 5 分钟，超时后标记 FAILED |
| 失败记录 | 标记 FAILED 并记录 error_msg，支持用户手动重试 |

#### 状态机定义

```
                      ┌──────────────────────┐
                      │        PENDING        │
                      └──────────┬───────────┘
                          │              ↑
                   消费者消费         手动重试
                          │              │
                      ┌───▼───────────┐  │
                      │    PARSING     │──┤
                      └───┬───────────┘  │
                          │ 解析完成      │
                      ┌───▼───────────┐  │
                      │   RETRIEVING   │  │
                      └───┬───────────┘  │
                     ┌────┤              │
                     │    │ 检索完成      │
                     │ ┌──▼───────────┐  │
                     │ │  REVIEWING    │  │
                     │ └──┬───────────┘  │
                     │    │ 审查完成      │
                     │ ┌──▼────────────┐ │
                     │ │  SUMMARIZING   │ │
                     │ └──┬───────────┘ │
                     │    │              │
                     │ ┌──┴─────┐        │
                     │ │        │        │
                     │ │ 正常   │ 失败   │
                     │ │        │        │
                     │ │   ┌────┴───┐    │
                     │ │   │        │    │
                     │ │ ┌──▼──┐ ┌──▼──┐│
                     │ │ │SUCC.│ │FAIL.││
                     │ │ └─────┘ └─────┘│
                     │ │                │
                     └─┴────────────────┘
```

**状态转换说明：**

| 当前状态 | 触发条件 | 下一状态 |
|----------|----------|----------|
| PENDING | 用户确认提交，MQ 消费者消费消息 | PARSING |
| PARSING | 解析完成 | RETRIEVING |
| PARSING | 解析失败 | FAILED |
| RETRIEVING | 检索完成 | REVIEWING |
| RETRIEVING | 检索失败且未超重试次数 | RETRIEVING（回退重试） |
| RETRIEVING | 检索失败且超重试次数 | FAILED（标记失败） |
| REVIEWING | 审查完成 | SUMMARIZING |
| REVIEWING | 审查失败 | FAILED |
| SUMMARIZING | 汇总完成 | SUCCESS |
| SUMMARIZING | 汇总失败 | FAILED |
| FAILED | 用户手动重试 | PENDING（重新排队） |

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
| B4 | 隐私脱敏 | 脱敏后的文本不包含原始敏感信息 |
