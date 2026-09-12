<p align="center">
  <h1 align="center">基于 Multi-Agent 与 RAG 的智能合同风险审查系统ContractReview</h1>
  <p align="center">上传合同，一键识别风险</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen" alt="Spring Boot 3.2"/>
  <img src="https://img.shields.io/badge/Vue-3.4-%234FC08D" alt="Vue 3.4"/>
  <img src="https://img.shields.io/badge/license-AGPLv3-blue" alt="License AGPLv3"/>
  <img src="https://img.shields.io/badge/coverage-%E2%89%A980%25-yellow" alt="Coverage ≥80%"/>
  <img src="https://img.shields.io/badge/phase-3-brightgreen" alt="Phase 3"/>
</p>

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [前置要求](#前置要求)
- [快速开始](#快速开始)
- [API 文档](#api-文档)
- [测试](#测试)
- [开发路线](#开发路线)
- [许可证](#许可证)

## 项目简介

ContractReview 是一款面向 C 端用户的智能合同风险审查工具。用户上传合同文件后，系统自动提取文本、脱敏隐私信息，通过 RAG 法条检索和 Multi-Agent 管线调用 LLM 进行深度风险审查，最终输出结构化风险报告。

**典型用户场景**：应届毕业生租房合同审查、求职者劳动合同审查、自由职业者外包协议审查。

## 功能特性

- **合同解析** — 基于 Apache PDFBox（PDF）和 Apache POI（DOCX）提取合同文本
- **隐私脱敏** — 自动识别并替换姓名、身份证号、手机号、银行卡号（支持 `?desensitize=false` 跳过脱敏）
- **RAG 法条检索** — 内置民法典、劳动合同法、知识产权相关法律知识库，向量相似度检索增强审查依据
- **Multi-Agent 审查管线** — Agent A 合同分类、Agent B 分块审查（Semaphore 10 路并发）、Agent C 汇总报告
- **异步状态机** — RabbitMQ + DLX 死信队列驱动，SSE 实时推送进度和中间结果
- **结构化风险报告** — 高危/中危/低危分级，关联法条引用
- **JWT 鉴权 + Token 刷新** — Spring Security 完整鉴权链，自动续期，并发安全
- **Redis 限流 + 积分管理** — 滑动窗口限流，Lua 原子积分扣减，失败自动回滚
- **操作审计** — AOP 注解记录用户操作日志
- **任务重试** — 失败任务支持一键重试
- **Vue 3 前端** — 用户注册登录、文件上传、进度展示、报告查看、历史筛选

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端语言 | Java 17 |
| 后端框架 | Spring Boot 3.2, MyBatis-Plus, Spring AI, Spring Security |
| 消息队列 | RabbitMQ + DLX 死信队列 |
| 向量库 | Chroma |
| 存储 | MySQL 8.0, Redis, MinIO |
| 安全 | JJWT 0.12, BCrypt, Lua 限流脚本 |
| 解析 | Apache PDFBox 3.0, Apache POI (DOCX) |
| 前端 | Vue 3.4, Pinia, Vue Router, Element Plus, Axios |
| 构建 | Maven, Vite |
| 测试 | JUnit 5, Mockito, Vitest, @vue/test-utils, happy-dom |

## 项目结构

```
src/main/java/com/contractreview/
├── config/               # 多环境配置、Security、Redis、RabbitMQ
├── controller/           # Auth / Contract / User / Law / Admin Controller
├── service/
│   ├── AuthService
│   ├── ContractService
│   ├── AgentService
│   ├── RagService
│   ├── SseService
│   ├── ReviewStateMachine
│   └── impl/             # 各接口实现（含 RabbitMQ 监听器 ReviewMessageListenerImpl）
├── mapper/               # MyBatis-Plus Mapper 接口
├── domain/
│   ├── entity/           # 数据表实体
│   ├── dto/              # 请求/响应 DTO
│   └── enums/            # 错误码枚举
├── common/               # 统一响应体 R<T>
├── exception/            # 全局异常处理器
├── security/             # JWT 工具类、过滤器、限流过滤器
├── aop/                  # @AuditLog 操作审计
└── util/                 # 脱敏工具、文件工具、文本分块工具

web/                      # Vue 3 前端
├── src/
│   ├── api/              # Axios 封装 + API 函数
│   ├── components/       # SseProgress, ThemeToggle
│   ├── views/            # Login, Register, Upload, Report, History, Profile, LawLibrary, AdminDashboard, Monitor, Forbidden, NotFound
│   ├── stores/           # Pinia 状态管理
│   └── router/           # Vue Router 路由 + 导航守卫
└── vitest.config.js
```

## 前置要求

- JDK 17+
- Docker Desktop（运行 MySQL / Redis / MinIO / RabbitMQ）
- Maven 3.8+
- LLM API Key（OpenAI / 通义千问等）

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/your-username/contractreview.git
cd contractreview
```

### 2. 启动基础设施

```bash
docker compose up -d
```

### 3. 配置环境

（1）后端Spring Boot配置

```bash
cp src/main/resources/application-example.yml src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，填入你的 LLM API Key 和其他配置。

（2）Docker配置

```bash
cp .env.example .env
```

编辑 `.env`，填入你的 LLM API Key 和其他配置。

### 4. 运行后端

```bash
mvn spring-boot:run
```

服务启动后访问 `http://localhost:8080`。

### 5. 运行前端

```bash
cd web

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建（生产）
# npm run build

# 运行测试
# npm test
```

开发环境下前端默认访问 `http://localhost:5173`。

### CURL验证

```bash
# 注册
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```



## API 文档

所有接口以 `/api/v1` 为前缀。认证接口无需 Token，其余需在 Header 携带 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 用户登录（返回 token / refreshToken）|
| POST | `/auth/refresh` | 刷新 Token（返回新 token，仅更新认证状态）|
| POST | `/contract/upload` | 上传合同（`?desensitize=false` 跳过脱敏） |
| POST | `/contract/paste` | 直接粘贴合同文本审查 |
| POST | `/contract/{taskId}/submit` | 确认提交审查（扣积分）|
| GET | `/contract/{taskId}/status` | 查询任务状态 |
| GET | `/contract/{taskId}/report` | 获取审查报告 |
| GET | `/contract/{taskId}/progress` | SSE 实时进度推送 |
| GET | `/contract/{taskId}/text` | 获取合同预览原文 |
| GET | `/contract/{taskId}/logs` | 获取审查过程日志 |
| GET | `/contract/history` | 历史记录（分页，`?status=` 筛选） |
| POST | `/contract/{taskId}/retry` | 重试失败任务 |
| GET | `/user/profile` | 获取个人资料、积分和API 配置 |
| PUT | `/user/profile` | 更新用户名 |
| POST | `/user/password` | 修改密码 |
| PUT | `/user/api-config` | 设置自定义 LLM API |
| GET/POST/PUT/DELETE | `/law/**` | 法条库 CRUD 与重索引（需 ADMIN）|
| GET | `/admin/stats` | 系统统计（ADMIN）|
| GET | `/admin/monitor` | 系统监控指标（ADMIN）|
| GET | `/admin/operations` | 操作日志（ADMIN）|
| GET | `/admin/users` | 用户列表（ADMIN）|
| PUT | `/admin/users/{id}/role` | 修改用户角色（ADMIN）|
| PUT | `/admin/users/{id}/quota` | 设置用户积分（ADMIN）|
| GET | `/admin/quota-default` | 查询新用户默认积分（ADMIN）|
| PUT | `/admin/quota-default` | 设置新用户默认积分（ADMIN，0 ~ 100000）|
| DELETE | `/admin/users/{id}` | 删除用户（ADMIN）|

**统一响应格式**：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": "2026-09-04T12:00:00Z",
  "requestId": "uuid"
}
```

**错误码**：1001（格式不支持）、1002（文件过大）、1003（积分不足）、1004（任务不存在）、1005（状态非法）、1006（LLM 失败）、1008（限流）、1009（超时）。

## 测试

```bash
# 后端测试
mvn test

# 后端指定测试类
mvn test -Dtest=DesensitizationUtilTest

# 前端测试
cd web && npm test
```

当前共 **18 个测试类**，覆盖：

| 测试类 | 用例数 | 范围 |
|--------|--------|------|
| DesensitizationUtilTest | 6 | 脱敏正则边界场景 |
| JwtUtilsTest | 5 | Token 签发/验证/篡改 |
| AuthServiceImplTest | 7 | 注册/登录/刷新 |
| ContractServiceImplTest | 20 | 上传/提交/状态/报告/历史/重试/异步审查 |
| AgentServiceImplTest | 10 | Agent A/B/C 解析与兜底 |
| RagServiceImplTest | 7 | 向量库查询与降级 |
| ReviewMessageListenerTest | 7 | MQ 消息消费、DLX 重试、状态转换 |
| ReviewStateMachineImplTest | 6 | 状态有效性、重复转换、超时转换 |
| SseServiceImplTest | 8 | SSE 发射、Agent 注册、完成与错误事件 |
| ReviewResultHandlerTest | — | 审查结果落库与异常处理 |
| UserContextTest | — | 用户上下文工具 |
| JwtAuthenticationFilterTest | — | 鉴权过滤器 |
| GlobalExceptionHandlerTest | — | 全局异常处理 |
| AccessLogFilterTest | — | 访问日志过滤器 |
| MdcFilterTest | — | MDC 请求 ID 过滤器 |
| LogTruncatorTest | — | 日志截断防 HTML 污染 |
| ErrorCodeTest | — | 错误码枚举 |
| AuthRequestValidationTest | — | 请求参数校验 |
| 前端测试 10 文件 | 66 | API / Store / Router / Components / Views |

## 开发路线

```
Phase 0: 环境搭建（JDK + Docker + Maven）                  ✅ 完成
Phase 1: MVP（上传 → 脱敏 → LLM 审查 → 报告）               ✅ 完成
Phase 2: RAG + Multi-Agent + MQ/SSE                       ✅ 完成
Phase 3.1: 安全加固（Spring Security + 限流 + 审计）         ✅ 完成
Phase 3.2+: Vue 3 前端 + Word/POI + CI/CD                  ▶ 进行中
```

## 许可证

Copyright (C) 2026 ContractReview

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
