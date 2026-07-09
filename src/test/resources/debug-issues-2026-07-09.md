# 联调问题记录 2026-07-09

## 1. Windows PowerShell curl.exe JSON 传参异常

**现象**：登录/注册接口返回 500，报 `HttpMessageNotReadableException: JSON parse error: Unexpected character ('u')`。

**原因**：PowerShell 中用单引号传 JSON 时，`curl.exe` 收到的 `'{"k":"v"}'` 被 PowerShell 解析为 `{k:v}`（外层双引号丢失），Jackson 解析失败。

**修复**：用 `'{\"k\":\"v\"}'`（反斜杠转义双引号）。

```powershell
# 错误写法
curl.exe -d '{"username":"test","password":"test"}'

# 正确写法（Windows PowerShell）
curl.exe -d '{\"username\":\"test\",\"password\":\"test\"}'
```

---

## 2. Redis Lua 脚本 ARGV 序列化问题

**现象**：上传接口返回 500，Redis 报 `ERR user_script:6: attempt to perform arithmetic on local 'window' (a nil value)`。

**原因**：`RateLimitFilter` 注入 `RedisTemplate<String, Object>`，其 `valueSerializer` 是 `GenericJackson2JsonRedisSerializer`。执行 Lua 脚本时 ARGV 参数经 Jackson 序列化——字符串 `"60"` 变成 `"\"60\""`（JSON 字符串带引号），Lua 的 `tonumber('"60"')` 返回 nil。

**修复**：`RateLimitFilter` 改用 `StringRedisTemplate`（`StringRedisSerializer` 直接传裸字符串，Lua 可正常解析）。

**文件**：`src/main/java/com/contractreview/security/RateLimitFilter.java`

```java
// 改前
private final RedisTemplate<String, Object> redisTemplate;

// 改后
private final StringRedisTemplate redisTemplate;
```

---

## 3. RabbitMQ 缺少 JSON 消息转换器

**现象**：提交审查接口返回 500，报 `SimpleMessageConverter only supports String, byte[] and Serializable payloads, received: ReviewMessage`。

**原因**：`RabbitTemplate.convertAndSend()` 默认用 `SimpleMessageConverter`，只支持 String/byte[]/Serializable，无法序列化 `ReviewMessage` POJO。

**修复**：添加 `Jackson2JsonMessageConverter` Bean。

**文件**：`src/main/java/com/contractreview/config/RabbitMqConfig.java`

```java
@EnableRabbit
public class RabbitMqConfig {
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    // ...
}
```

---

## 4. operation_log 表缺失

**现象**：提交审查接口返回 500（被 `@AuditLog` 的 catch 吞掉，但仍导致事务回滚）。

**原因**：`@AuditLog` 切面尝试写入 `operation_log` 表，但该表在 MySQL 容器首次启动后才被加入 `init.sql`，因此未创建。

**修复**：手动执行 `init.sql` 灌入 MySQL。

```bash
Get-Content src/main/resources/db/init.sql | docker exec -i contract-mysql mysql -uroot -proot123 contract_review
```

**文件**：`src/main/resources/db/init.sql`（已包含 `CREATE TABLE IF NOT EXISTS operation_log`）

---

## 5. 端口残留导致全接口 500

**现象**：所有 API 返回 500（Spring Boot 默认错误页面，非自定义 R 格式）。

**原因**：旧的 Java 进程（Maven `spring-boot:run` 的 fork 进程）未完全退出，与新进程竞争端口 8080，导致应用上下文异常。

**修复**：强制杀掉所有 Java 进程后重启。

```powershell
Get-Process -Name "java" | Stop-Process -Force
```

---

## 6. Chroma Embedding 404（已知限制，未修复）

**现象**：启动日志 WARN `Chroma not available yet, will retry on next startup: 404`。

**原因**：配置的 AI 供应商（opencode）的 `/zen/v1/embeddings` 返回 404（SPA 页面），没有 embedding 模型。`LawSeedRunner` 已 catch 异常。

**影响**：RAG 相似度检索不可用，Agent B 无法获取相关法律条文。核心审查流程（无 RAG 增强）不受影响。
