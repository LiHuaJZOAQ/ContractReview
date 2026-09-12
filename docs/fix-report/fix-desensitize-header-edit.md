---
name: fix-desensitize-header-edit
description: 脱敏仅处理开头（到第一条第X条），新增 user-editable preview + 查找替换面板，保留原文 raw_text
metadata:
  type: fix-report
  originSessionId: c0ba28bf-8811-45bd-bc11-b260bf549a8e
  modified: 2026-09-07T00:00:00Z
---

# 脱敏范围收敛 + 可编辑预览修复（2026-09-07）

## 问题

`DesensitizationUtil.desensitize()` 对整篇合同文本跑 6 条正则，正文中的长串数字（合同编号、19 位参考号等）被误判为身份证/银行卡，损伤条款内容。用户无法在提交前修正误判，且原文无法恢复。

## 修复内容

### 1.脱敏范围收敛为「头部」

- 新增 `findHeaderEndIndex()`：从文本起点找到第一条 `第X条`（含）的位置；无匹配时 fallback 前 500 字符。
- 新增 `desensitizeHeader()`：仅对 `substring(0, headerEnd)` 执行现有 6 条正则；正文完全不处理。
- 保留原 `desensitize(String)` 不动（向后兼容，测试仍依赖）。

**文件**：`src/main/java/com/contractreview/util/DesensitizationUtil.java`

### 2. 保留原文（raw_text）

- DB：`init.sql` `review_task` 新列 `raw_text MEDIUMTEXT NULL` + `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`
- 实体：`ReviewTask.java` 新字段 `@TableField("raw_text") private String rawText`
- Service：`upload()` / `pasteText()` 在脱敏前保存 `rawText`，在 `previewText` 存脱敏版本

**文件**：`init.sql`, `ReviewTask.java`, `ContractServiceImpl.java`

### 3. 用户可编辑预览 + 查找替换

- 前端：`Upload.vue` `<pre>` → `<el-input type="textarea">`（可编辑）；右上角常驻查找替换面板（查找/替换输入框、命中数、上一处/下一处/替换/全部替换，含 `escapeRegExp` 防止正则注入，零宽匹配防御）
- 保存：`PUT /api/v1/contract/{taskId}/preview` + `UpdatePreviewRequest` DTO（`@NotBlank @Size(max=200000)`）；`ContractService.updatePreviewText()` 仅允许 `PENDING`/`PARSING` 状态修改
- MQ 消费：`ReviewMessageListenerImpl` 仍读 `previewText`，用户保存编辑后自然读到修正版

**文件**：`Upload.vue`, `contract.js`, `ContractController.java`, `UpdatePreviewRequest.java`, `ContractService.java`, `ContractServiceImpl.java`

### 4. 测试

- `DesensitizationUtilTest` +4：`testHeaderOnly` / `testFallbackToCharLimit` / `testIdCardInBodyNotMasked` / `testPhoneInHeaderMasked` + `testFindHeaderEndIndex`
- `ContractServiceImplTest` +5：`testUpdatePreviewTextSuccess` / `ParsingAllowed` / `ReviewingRejected` / `TooLong` / `WrongUser` + `testUploadSuccessWithDesensitize` 补 `rawText` 断言

**验证结果**：`DesensitizationUtilTest` 22/22 通过；`ContractServiceImplTest` 25/25 通过；`mvn compile` 无错误。

## 迁移 / 风险

- **已运行 DB**：直接执行 `ALTER TABLE review_task ADD COLUMN raw_text MEDIUMTEXT NULL` 即可；旧数据 `raw_text IS NULL` 不影响任何读路径（完全依赖 `preview_text`）
- **已提交任务**：状态守卫拒绝修改预览；已完成的任务不会被误操作
- **无原文恢复**：旧任务的 `raw_text` 为 NULL（原始未脱敏文本已丢失）；仅新上传任务完整保存
- **缓存 / 一致性**：无额外缓存；写后立即更新 DB；最坏情况为用户保存后下一次读到旧值（概率极低）

## 不在范围

- 充值/购买流程（积分制已完成，见 `fix-points-system.md`）
- 前端自动保存（需手动点"保存预览"，避免频繁 PUT）
- `Monaco`/`CodeMirror` 富文本编辑器（资源体积考虑，使用轻量 `<el-textarea>` + 自实现查找替换面板）

## 记录位置

修复说明写入 `docs/fix-report/fix-desensitize-header-edit.md`；README 仅保留功能描述与接口列表，不记录修复历史（按 `CLAUDE.md` 规则）。
