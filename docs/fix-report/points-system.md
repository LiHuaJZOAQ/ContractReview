# 积分制改造（Points System Refactor，2026-09-04）

## 业务动机

原系统中"剩余额度"与"总额"是双字段设计，但 `quotaTotal` 仅是 `DEFAULT_QUOTA = 10` 的硬编码值，**没有任何业务节点写入**（无充值/无购买流程），属虚假概念。前端 `剩余/10 次` 的展示无业务意义。

实际业务形态是"预付费赠送制"：注册时给积分，无总额概念。改造为单配额积分制。

## 改造内容

### 概念
- 唯一字段：`user.review_quota`，语义为"积分"
- 默认值：100（原为 10）
- 默认值可通过管理面板调整，**仅影响新注册用户**
- 现有用户的积分调整保留（`PUT /admin/users/{id}/quota` 改为"设置积分"）

### 数据层
- 新表 `system_config`（key-value 形式）：
  ```sql
  config_key VARCHAR(100) PRIMARY KEY
  config_value VARCHAR(500)
  description VARCHAR(255)
  created_at / updated_at
  ```
- 初始数据：插入 `('default_quota', '100', '新用户注册默认积分')`

### 新增后端
- 实体 `SystemConfig`（`src/main/java/com/contractreview/domain/entity/SystemConfig.java`）
- Mapper `SystemConfigMapper`（`mapper/SystemConfigMapper.java`）
- Service `SystemConfigService` + 实现（`service/SystemConfigService.java`、`service/impl/SystemConfigServiceImpl.java`）
  - `getInt(key, defaultValue)` / `getString(key, defaultValue)` / `set(key, value)`
  - 内存缓存 `ConcurrentHashMap`，写后清缓存
- 端点：
  - `GET /api/v1/admin/quota-default` → `{"quota": 100}`
  - `PUT /api/v1/admin/quota-default` body: `{"quota": 200}`，范围 `0 ~ 100000`

### 修改后端
- `UserProfileResponse` 删除 `quotaTotal` 字段
- `AuthServiceImpl` 注入 `SystemConfigService`，`register()` 改读 `default_quota`，删常量 `DEFAULT_QUOTA`
- `UserServiceImpl` 删 `DEFAULT_QUOTA`，`getProfile()` 构造参数同步
- `AdminService` 加 `getDefaultQuota()` / `setDefaultQuota(int)`
- `AdminController` 加端点；`resetUserQuota` 改"设置积分"（去 `getOrDefault("quota", 10)` 兜底，强制 body 传值）

### 修改前端
- `web/src/stores/auth.js` 删除 `quotaTotal` ref + localStorage 读写
- `web/src/views/Layout.vue` 展示从"剩余 X/Y 次"改为"积分: X"
- `web/src/views/Profile.vue` 同样改"积分"标签
- `web/src/views/AdminDashboard.vue`：
  - 用户管理表格上方加"新用户默认积分"区块，显示当前值 + 修改按钮
  - 用户列表列加"积分"单位
  - "重置额度"按钮文案改"设置积分"
- `web/src/api/admin.js` 加 `getDefaultQuota()` / `setDefaultQuota(quota)`

### 测试
- `AuthServiceImplTest` 增加 `SystemConfigService` mock，构造注入；`setUp()` 中 stub `getInt("default_quota", _)` 返回 100

## DB 迁移

`init.sql` 已加新表 + 初始 INSERT（**新部署自动生效**，MySQL 首次启动时执行 `docker-entrypoint-initdb.d/`）。
**已运行过本系统的 DB 需手动迁移**（以下脚本可直接执行，不会影响现有数据）：

### 1. 通用迁移脚本（无论有没有旧数据都可执行）

```sql
-- 1) 新增 system_config 表
CREATE TABLE IF NOT EXISTS `system_config` (
    `config_key`   VARCHAR(100) NOT NULL,
    `config_value` VARCHAR(500) NOT NULL,
    `description`  VARCHAR(255) NULL,
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) 插入默认积分（如已存在则跳过）
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
    ('default_quota', '100', '新用户注册默认积分');
```

### 2. 已有数据场景的额外检查

```sql
-- 检查是否已有 system_config
SELECT * FROM system_config WHERE config_key = 'default_quota';
-- 期望返回一行 config_value='100'；若无则重复执行 1) 中的 INSERT

-- 现有用户的积分仍是旧值（10），是否需要批量改？
-- 建议：根据业务决定
--   - 保留现状：现有用户继续按 10 积分，新用户按 100
--   - 一刀切：UPDATE user SET review_quota = 100 WHERE review_quota = 10
--   - 按角色：admin 保留，user 调整
```

### 3. 回滚 SQL（必要时）

```sql
DELETE FROM `system_config` WHERE `config_key` = 'default_quota';
DROP TABLE IF EXISTS `system_config`;
-- user 表 review_quota 字段无 schema 变化，无需回滚
```

### 4. 不重建数据卷的方案

`docker-compose.yml` 的 `mysql-data` 卷保留即不会丢数据。仅需执行上面的 1+2 SQL。**不建议** `docker compose down -v`（会清掉所有业务表数据），除非在开发环境且确需重置。

## 风险与注意点

| 项 | 说明 |
|---|---|
| 现有用户的积分 | 不动存量；只影响新注册用户 |
| 前端 localStorage 残留 `quotaTotal` | 已被 store 忽略；`logout()` 时清理；无需迁移 |
| 缓存一致性 | `SystemConfigService.set()` 写后立即清缓存，最坏情况读一次旧值但下次走 DB |
| 并发改默认值 | 概率极低，未做锁 |

## 验证

```bash
# 1. 先执行 DB 迁移（见上方"DB 迁移"章节）
# 2. 重新编译后端
mvn compile -q
# 3. 跑注册相关测试
mvn test -Dtest=AuthServiceImplTest
# 4. 启动后端，注册新用户 → 查 DB user.review_quota = 100（不是 10）
# 5. PUT /api/v1/admin/quota-default {quota: 200} → 再注册新用户 → 验证 = 200
# 6. GET /api/v1/user/profile → 响应无 quotaTotal 字段
# 7. 前端 Layout 头部显示"积分: 100"，无 "/10"
# 8. AdminDashboard 顶部"默认积分"控件读写正常
```

## 不在本次范围

- 充值/购买流程
- 积分消费记录表
- 注册页面前置展示（注册前的积分提示）
