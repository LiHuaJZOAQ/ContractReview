<p align="center">
  <h1 align="center">ContractReview</h1>
  <p align="center">智能合同风险审查系统 — 上传合同，一键识别风险</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen" alt="Spring Boot 3.2"/>
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License MIT"/>
  <img src="https://img.shields.io/badge/coverage-%E2%89%A560%25-yellow" alt="Coverage ≥60%"/>
  <img src="https://img.shields.io/badge/phase-MVP-orange" alt="Phase MVP"/>
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

ContractReview 是一款面向 C 端用户的智能合同风险审查工具。用户上传合同文件后，系统自动提取文本、脱敏隐私信息，调用 LLM 进行风险审查，最终输出结构化风险报告。

**典型用户场景**：应届毕业生租房合同审查、求职者劳动合同审查、自由职业者外包协议审查。

## 功能特性

- **PDF 解析** — 基于 Apache PDFBox 提取合同文本
- **隐私脱敏** — 自动识别并替换姓名、身份证号、手机号、银行卡号（支持 `?desensitize=false` 跳过脱敏）
- **LLM 风险审查** — 基于 Spring AI 调用大模型，输出结构化风险报告
- **JWT 鉴权** — 注册/登录/Token 刷新，拦截器统一校验
- **异步审查** — `@Async` 异步执行，前端轮询获取进度
- **配额管理** — 注册赠送 10 次，Redis 计数，失败自动回滚
- **任务重试** — 失败任务支持一键重试

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2, MyBatis-Plus, Spring AI |
| 存储 | MySQL 8.0, Redis, MinIO |
| 安全 | JJWT 0.12, BCrypt |
| 解析 | Apache PDFBox 3.0 |
| 构建 | Maven |
| 测试 | JUnit 5, Mockito |

## 项目结构

```
src/main/java/com/contractreview/
├── config/              # 多环境配置、MyBatis-Plus 分页/乐观锁
├── controller/          # REST API 入口
│   ├── AuthController
│   └── ContractController
├── service/             # 业务逻辑
│   ├── AuthService
│   ├── ContractService
│   └── impl/
├── mapper/              # MyBatis-Plus Mapper 接口
├── domain/
│   ├── entity/          # 数据表实体
│   ├── dto/             # 请求/响应 DTO
│   └── enums/           # 错误码枚举
├── common/              # 统一响应体 R<T>
├── exception/           # 全局异常处理器
├── security/            # JWT 工具类 + 拦截器
├── async/               # 异步配额回滚处理器
└── util/                # 脱敏工具、文件工具
```

## 前置要求

- JDK 17+
- Docker Desktop（运行 MySQL / Redis / MinIO）
- Maven 3.8+
- LLM API Key（OpenAI / 通义千问等）

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/your-username/contract-review.git
cd contract-review
```

### 2. 启动基础设施

```bash
docker compose up -d
```

### 3. 配置环境

```bash
cp src/main/resources/application-example.yml src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，填入你的 LLM API Key 和其他配置。

### 4. 运行

```bash
mvn spring-boot:run
```

服务启动后访问 `http://localhost:8080`。

### 5. 验证

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
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/refresh` | 刷新 Token |
| POST | `/contract/upload` | 上传合同（`?desensitize=false` 跳过脱敏） |
| POST | `/contract/{taskId}/submit` | 确认提交审查 |
| GET | `/contract/{taskId}/status` | 查询任务状态 |
| GET | `/contract/{taskId}/report` | 获取审查报告 |
| GET | `/contract/history` | 历史记录（分页） |
| POST | `/contract/{taskId}/retry` | 重试失败任务 |

**统一响应格式**：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1717200000000,
  "requestId": "uuid"
}
```

**错误码**：1001（格式不支持）、1002（文件过大）、1003（配额不足）、1004（任务不存在）、1005（状态非法）、1006（LLM 失败）、1008（限流）、1009（超时）。

## 测试

```bash
# 运行全部测试
mvn test

# 运行指定测试类
mvn test -Dtest=DesensitizationUtilTest
```

当前共 **52 个测试用例**，覆盖：

| 测试类 | 用例数 | 范围 |
|--------|--------|------|
| DesensitizationUtilTest | 17 | 脱敏正则边界场景 |
| JwtUtilsTest | 5 | Token 签发/验证/篡改 |
| AuthServiceImplTest | 7 | 注册/登录/刷新 |
| ContractServiceImplTest | 23 | 上传/提交/状态/报告/历史/重试/异步审查 |

## 开发路线

```
Phase 0: 环境搭建（JDK + Docker + Maven）          ✅ 完成
Phase 1: MVP（上传 → 脱敏 → LLM 审查 → 报告）       ✅ 完成
Phase 2: RAG + Multi-Agent + MQ/SSE               ⏳
Phase 3: 前端 + 安全加固 + CI/CD                    ⏳
```

