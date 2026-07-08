# MVP 全链路集成测试报告

## 1. 测试概述

- **测试日期**：2026-07-08
- **测试环境**：Windows 11, JDK 23, Spring Boot 3.2.5, MySQL 8.0 (Docker), Redis 7 (Docker), MinIO (Docker)
- **测试范围**：Phase 1 MVP 全链路（用户模块 → 上传解析 → 脱敏 → LLM 审查 → 报告查询）
- **测试方法**：`Invoke-RestMethod` (PowerShell) + `curl.exe` 手动模拟全流程

## 2. 测试流程与结果

| 步骤 | 接口 | 请求 | 预期结果 | 实际结果 | 状态 |
|------|------|------|----------|----------|:----:|
| 1 | `POST /api/v1/auth/register` | `{"username":"testuser","password":"test123"}` | 201, 返回 userId + token | `code:0`, userId:1, token 签发 | ✅ |
| 2 | `POST /api/v1/auth/login` | `{"username":"testuser","password":"test123"}` | 200, 返回 JWT | `code:0`, JWT 正常返回 | ✅ |
| 3 | `POST /api/v1/auth/refresh` | `{"refreshToken":"..."}` | 200, 新 token | 未测 | ⬜ |
| 4 | `POST /api/v1/contract/upload` | multipart/form-data, PDF 文件 | 200, 返回 taskId + 脱敏预览 | `taskId:4`, 预览文本正确脱敏 | ✅ |
| 5 | `POST /api/v1/contract/{taskId}/submit` | — | 200, 异步开始审查 | `code:0` | ✅ |
| 6 | `GET /api/v1/contract/{taskId}/status` | — | 轮询返回 PROCESSING → SUCCESS | `SUCCESS`, progress:100 | ✅ |
| 7 | `GET /api/v1/contract/{taskId}/report` | — | 200, 结构化报告 | 4 个高风险项 + 法条引用 | ✅ |
| 8 | `GET /api/v1/contract/history` | `?page=1&size=10` | 200, 分页历史 | 4 条记录，按时间降序 | ✅ |

### 2.1 脱敏验证

上传合同文本中含以下敏感信息，脱敏引擎全部正确替换为 `***`：

| 原文 | 脱敏后 | 规则 |
|------|--------|------|
| 张三先生 | `***` | 姓名（含称谓） |
| 110101199001011234 | `***` | 身份证号 |
| 13800138000 | `***` | 手机号 |
| 李四女士 | `***` | 姓名（含称谓） |

### 2.2 LLM 审查结果摘要

审查模型 **big-pickle** 返回了 4 个高风险项：

| # | 风险类型 | 风险等级 | 描述 | 建议 |
|---|----------|----------|------|------|
| 1 | 其他 | HIGH | 合同当事人信息不明确，签约主体无法确定 | 补充双方真实全称、身份证号、地址等 |
| 2 | 其他 | HIGH | 标的物描述缺失，合同无法履行 | 详细列明品名、规格、数量、质量等 |
| 3 | 其他 | HIGH | 付款条件不明确（"***"未定义） | 明确付款前提、金额、方式及违约责任 |
| 4 | 其他 | HIGH | 缺少违约责任、争议解决等必要通用条款 | 增加违约、争议、不可抗力等条款 |

法条引用示例：《中华人民共和国民法典》第 470 条、第 577 条、第 598 条、第 626 条等。

### 2.3 错误码验证

| 场景 | 期望错误码 | 实际结果 |
|------|-----------|:--------:|
| 未登录请求受保护接口 | 401 | ✅ `未登录或Token缺失` |
| 不存在的 taskId | 1004 | ✅ |
| 非 PENDING 状态提交 | 1005 | ✅ `当前任务状态不允许此操作` |
| 任务未完成查报告 | 1005 | ✅ `任务尚未完成` |
| LLM 调用失败 | FAILED + 配额回滚 | ✅ `error_msg` 记录异常 |

## 3. 测试中发现的问题与修复

### 3.1 Maven 编译 Lombok 未处理

- **现象**：`mvn compile` 报 `找不到符号: 变量 log`、`找不到符号: 方法 setXxx`
- **原因**：Maven 默认未启用 Lombok 注解处理器，IDEA 依赖自带插件所以正常
- **修复**：在 `pom.xml` 的 `maven-compiler-plugin` 中添加 `annotationProcessorPaths`，引用 `${lombok.version}` 属性
- **结果**：`mvn compile` 编译通过

### 3.2 Spring AI HTTP 客户端超时

- **现象**：LLM 调用报告 `I/O error on POST request ... : timeout`
- **原因**：Spring Boot 默认的 `RestClient` 使用 JDK `HttpURLConnection`，与 Cloudflare 的 SSL 重协商不兼容；默认读超时过短
- **修复**：添加 `httpclient5` 依赖，配置 `RestClient.Builder` 使用 Apache HttpClient 5，设置 2 分钟连接和响应超时
- **结果**：修复后 LLM 调用成功，输出 13500+ tokens

### 3.3 PowerShell curl.exe JSON 传参

- **现象**：`curl.exe -d '{"key":"value"}'` 导致服务器端 JSON 解析失败
- **原因**：PowerShell 对单引号和双引号的处理与 bash 不同
- **规避**：使用 `Invoke-RestMethod` 或 `curl.exe` 配合变量传参：

```powershell
$body = '{"username":"test","password":"test"}'
Invoke-RestMethod -Uri "..." -Method Post -ContentType "application/json" -Body $body
```

## 4. 测试数据

- 测试 PDF 文件：`src/test/resources/test-contract.pdf`（15KB，包含合同文本和敏感信息）
- 测试用户：`testuser / test123`
- 最大任务 ID：4
- LLM 输入 token 数：344
- LLM 输出 token 数：1021 ~ 13500

## 5. 结论

Phase 1 MVP **全链路通过验证**：

- ✅ 用户注册 / 登录 / JWT 鉴权
- ✅ PDF 上传 / 解析 / MinIO 存储
- ✅ 隐私脱敏（4 类正则）
- ✅ 异步 LLM 审查（@Async + 状态轮询）
- ✅ 状态机流转（PENDING → SUCCESS）
- ✅ 异常处理（配额回滚、错误码、FAILED 标记）
- ✅ 报告查询 / 历史记录

**待完成**：
- ⬜ 单元测试覆盖 ≥ 60%
- ⬜ 全自动化集成测试脚本
- ⬜ 前端轮询 / SSE 进度展示
- ⬜ Phase 2（RAG + Multi-Agent + MQ）
