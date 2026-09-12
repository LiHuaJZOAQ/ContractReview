# 配额修复记录（2026-09-04）

修复：登录/刷新不再返回配额（`reviewQuota` / `quotaTotal`），统一由 `GET /user/profile` 提供。

修改文件（6 个）：
- `src/main/java/com/contractreview/domain/dto/AuthResponse.java`
- `src/main/java/com/contractreview/service/impl/AuthServiceImpl.java`
- `web/src/stores/auth.js`
- `web/src/views/Layout.vue`
- `web/src/views/Upload.vue`
- `README.md`（仅接口描述修正，不记录修复历史）

刷新机制：
- `Layout.vue` 监听 `visibilitychange`（页面可见时刷新配额）
- `Upload.vue` 提交成功后调用 `auth.fetchProfile()` 同步真实配额，避免本地缓存与实际不一致

后续修复内容记录位置：`spec/` 目录（如本文件）；修复说明**不写入 README**（README 只保留功能描述和接口列表，不记录修复历史）。
