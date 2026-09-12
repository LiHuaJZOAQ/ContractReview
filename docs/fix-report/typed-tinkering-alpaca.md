# 脱敏范围收敛为「开头 + 用户可编辑预览」

## Context

`DesensitizationUtil.desensitize()` 当前对整篇合同跑 6 条正则（姓名/身份证/手机/银行卡等），把正文中 19 位数字、合同编号、长串数字等"非个人信息"误判为敏感信息，损伤条款文本。改造方向：
1. **脱敏范围限定到开头**：从文本起点到第一条 `第X条`（含），找不到时 fallback 到前 500 字符。
2. **用户可编辑预览**：上传/粘贴后立即给出可编辑的脱敏预览，用户可手动修补误判后保存。
3. **保留原文**：新增 `review_task.raw_text` 列保存未脱敏原文，仅供备份与重生成；不通过任何 API 暴露。

## Critical files to be modified

- `src/main/java/com/contractreview/util/DesensitizationUtil.java` — 新增 `desensitizeHeader()` + `findHeaderEndIndex()`
- `src/main/java/com/contractreview/domain/entity/ReviewTask.java` — 新增 `rawText` 字段
- `src/main/java/com/contractreview/service/impl/ContractServiceImpl.java` — `upload/pasteText` 写 `rawText`；新增 `updatePreviewText()`
- `src/main/java/com/contractreview/controller/ContractController.java` — 新增 `PUT /{taskId}/preview`
- `src/main/resources/db/init.sql` — ALTER TABLE 新增 `raw_text` 列
- `web/src/views/Upload.vue` — `<pre>` 替换为 `<el-input type="textarea">` + 保存按钮
- `web/src/api/contract.js` — 新增 `updatePreview()`
- `src/test/java/com/contractreview/util/DesensitizationUtilTest.java` — 新增 4 个 header 行为测试
- `src/test/java/com/contractreview/service/impl/ContractServiceImplTest.java` — 断言 `rawText == original`

## 1. Backend

### 1.1 DesensitizationUtil
- 新增常量 `CLAUSE_HEADING = Pattern.compile("(?m)^[\\s\\S]*?第[零一二三四五六七八九十百千0-9]+条")`
- 新增 `public static int findHeaderEndIndex(String text)`：null/empty → 0；用 matcher `.find().end()` 拿第一条"第X条"匹配结束位置；未匹配则返回 `Math.min(500, text.length())`
- 新增 `public static String desensitizeHeader(String text)`：计算 `headerEnd`，对 `text.substring(0, headerEnd)` 复用现有 6 条正则逻辑，拼接 `text.substring(headerEnd)` 后返回
- **保留**原 `desensitize(String text)` 不动（向后兼容、测试仍依赖）

### 1.2 DB
在 `init.sql` 的 `review_task` 表后追加：
```sql
ALTER TABLE `review_task`
    ADD COLUMN `raw_text` MEDIUMTEXT NULL COMMENT '合同原文（未脱敏），用于备份与重生成' AFTER `preview_text`;
```
- NULLABLE，不设默认值；老数据 `raw_text` 为 NULL，不影响任何读路径
- 对已运行 DB：直接执行此 ALTER 即可

### 1.3 ReviewTask 实体
在 `previewText` 字段后追加：
```java
@TableField("raw_text")
private String rawText;
```

### 1.4 ContractServiceImpl
- `upload()` 与 `pasteText()`：
  - 将 `DesensitizationUtil.desensitize(rawText)` 改为 `DesensitizationUtil.desensitizeHeader(rawText)`
  - `task.setPreviewText(processedText)` 之后追加 `task.setRawText(rawText)`（原文保存）
- 新增 `updatePreviewText(taskId, userId, newText)`：
  - 校验任务存在 + 归属当前 userId（否则抛 `TASK_NOT_FOUND`）
  - 状态校验：仅 `PENDING` / `PARSING` 可改（其它抛 `INVALID_STATE`）
  - 长度校验：≤ 200000 字符
  - 覆盖 `previewText` 并 `taskMapper.updateById`
- `getPreviewText()` 不变，仍返回 `task.getPreviewText()`（用户编辑后版本）

### 1.5 ContractController
新增端点：
```java
@PutMapping("/{taskId}/preview")
@AuditLog(action = "UPDATE_PREVIEW")
public R<Void> updatePreviewText(@PathVariable Long taskId,
                                 @Valid @RequestBody UpdatePreviewRequest req) {
    contractService.updatePreviewText(taskId, UserContext.getUserId(), req.getText());
    return R.ok();
}
```
DTO `UpdatePreviewRequest { @NotBlank @Size(max=200000) String text; }`

### 1.6 MQ 消费者
- `ReviewMessageListenerImpl` 第 65 行 `String fullText = task.getPreviewText();` 不动
- 用户保存预览后，DB 中 `previewText` 已是用户编辑版，消费者自然读到该版本

## 2. Frontend

### 2.1 `web/src/views/Upload.vue`
- L93：`<pre class="preview-text">{{ previewText }}</pre>` → 编辑器区域（见下方"查找替换"段落）
- L95-101 按钮行：新增"保存预览"按钮，置于"提交审查"之前；状态：`saving || !editablePreview.trim()` 时禁用
- `<script setup>`：
  - 新增 `const editablePreview = ref('')`
  - 新增 `const saving = ref(false)`
  - 新增 `async function savePreview()` → `await updatePreview(currentTaskId.value, editablePreview.value)`，成功 `ElMessage.success('预览已保存')`
  - `handleUpload/handlePaste` 成功回调：`editablePreview.value = res.previewText`

**查找替换面板**（VSCode 风格轻量版，紧凑布局常驻在编辑器右上角）：
- 模板结构：
  ```html
  <div class="editor-wrap">
    <el-input type="textarea" v-model="editablePreview" :rows="14" />
    <div class="find-bar">
      <el-input v-model="findKw" placeholder="查找" size="small" style="width:140px" @keyup.enter="findNext" />
      <el-input v-model="replaceKw" placeholder="替换为" size="small" style="width:140px" />
      <span class="hit-count">{{ hitIdx + 1 }}/{{ hits.length || 0 }}</span>
      <el-button size="small" :disabled="!hits.length" @click="findPrev">上一处</el-button>
      <el-button size="small" :disabled="!hits.length" @click="findNext">下一处</el-button>
      <el-button size="small" :disabled="!hits.length" @click="replaceOne">替换</el-button>
      <el-button size="small" type="primary" :disabled="!hits.length" @click="replaceAll">全部替换</el-button>
    </div>
  </div>
  ```
- `<script setup>` 状态与函数：
  ```js
  const findKw = ref('')
  const replaceKw = ref('')
  const hits = ref([])        // 存放所有 match.start 索引
  const hitIdx = ref(0)

  function recomputeHits() {
    if (!findKw.value) { hits.value = []; hitIdx.value = 0; return }
    const re = new RegExp(escapeRegExp(findKw.value), 'g')
    const list = []
    let m
    while ((m = re.exec(editablePreview.value)) !== null) {
      list.push(m.index)
      if (m.index === re.lastIndex) re.lastIndex++   // 防零宽死循环
    }
    hits.value = list
    hitIdx.value = list.length ? 0 : -1
  }

  function findNext() { if (hits.value.length) hitIdx.value = (hitIdx.value + 1) % hits.value.length }
  function findPrev() { if (hits.value.length) hitIdx.value = (hitIdx.value - 1 + hits.value.length) % hits.value.length }

  function replaceOne() {
    if (!hits.value.length) return
    const i = hits.value[hitIdx.value]
    editablePreview.value =
      editablePreview.value.slice(0, i) + replaceKw.value +
      editablePreview.value.slice(i + findKw.value.length)
    recomputeHits()
  }

  function replaceAll() {
    if (!findKw.value) return
    const re = new RegExp(escapeRegExp(findKw.value), 'g')
    editablePreview.value = editablePreview.value.replace(re, replaceKw.value)
    recomputeHits()
  }

  function escapeRegExp(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }

  watch([findKw, editablePreview], recomputeHits)
  ```
- 行为约束：
  - 查找/替换使用文本值（`editablePreview`），不直接操作 DOM
  - 替换单次后重算 hits，hitIdx 重置到 0
  - "保存预览"按钮始终保存 `editablePreview` 全量内容（不被查找替换功能影响）
  - 不引入 Monaco/CodeMirror（资源体积考虑），不接快捷键（保持简洁）

### 2.2 `web/src/api/contract.js`
```js
export function updatePreview(taskId, text) {
  return axios.put(`/contract/${taskId}/preview`, { text })
}
```

## 3. Tests

### 3.1 DesensitizationUtilTest（追加，保留原 7 个）
- `testHeaderOnly`：正文有"第一条" → 头部名字/电话被替换，正文中 19 位数字不动
- `testFallbackToCharLimit`：无"第X条" → 仅前 500 字符处理
- `testIdCardInBodyNotMasked`：18 位数字在第一条之后 → 不替换
- `testPhoneInHeaderMasked`：电话在头部 → 替换

### 3.2 ContractServiceImplTest
- 现有 upload/paste 测试追加 `verify(task).setRawText(originalRawText)`（用 ArgumentCaptor 抓 previewText 与 rawText）
- 新增 `testRawTextIsOriginal`：断言 rawText == 原始未脱敏文本，previewText != rawText

## 4. Migration safety

- ALTER TABLE 新增 NULLABLE 列 → 已有数据无需处理
- 旧任务 `raw_text IS NULL`：`getPreviewText()` 与 MQ 消费仍正常读 `preview_text`
- 状态守卫：已提交任务（REVIEWING/SUCCESS/FAILED）拒绝修改预览

## 5. Verification

1. 上传含"甲方张三 13800138000"开头、含"第一条 标的"和正文 19 位编号的合同
   - 头部：张三 / 13800138000 被替换为 `***`
   - 正文："第一条 标的"及之后：19 位编号不动
   - DB：`raw_text` = 完整原文；`preview_text` = 头部替换 + 原文
2. 编辑 textarea → 点"保存预览" → 查 DB `preview_text` 已更新
3. 点"提交审查" → MQ 消费 → LLM 收到的是用户编辑版（通过日志 `fullText.length()` 验证）
4. 提交后再 PUT `/preview` → 收到 `INVALID_STATE`
