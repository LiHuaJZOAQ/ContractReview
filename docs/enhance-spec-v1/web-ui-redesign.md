# 前端 UI 设计重设计方案

## 概述

### 目标

将现有基于 Element Plus 的功能性前端界面，重设计为 **Apple/ChatGPT 混合风格**的简约现代设计，同时支持暗色模式。保留 Element Plus 的功能组件（表格、分页、表单校验等），通过 CSS 变量体系全面覆盖其默认视觉。

### 设计理念

| 借鉴来源 | 吸取要素 |
|---------|---------|
| Apple / Claude | 大量留白、微妙阴影、无边框分割、内容优先 |
| Material Design 3 | 色彩系统、组件状态规范、动态色彩理念 |
| ChatGPT / Kimi | 暗色模式、极简导航、侧边栏交互 |

### 决策摘要

| 决策项 | 结果 |
|--------|------|
| UI 框架 | 深度定制 Element Plus（CSS 变量覆盖） |
| 视觉风格 | 混合风格（Apple 留白 + Material 色彩 + ChatGPT 暗色） |
| 暗色模式 | 需要，Header 切换 + localStorage 持久化 |
| 主题色 | Google Material 浅绿色 (Emerald) |
| Logo | 简约单线条 SVG 小图标（盾牌+文档元素） |
| 实施节奏 | 逐页逐步迭代 |

---

## 设计令牌 (Design Tokens)

### 颜色系统

#### 主色 (Emerald / Material 浅绿)

| Token | 浅色模式 | 暗色模式 | 用途 |
|-------|---------|---------|------|
| `--color-accent` | `#10b981` | `#34d399` | 主强调色 |
| `--color-accent-hover` | `#059669` | `#6ee7b7` | 悬停态 |
| `--color-accent-light` | `#ecfdf5` | `rgba(16,185,129,0.12)` | 浅底色 |
| `--color-accent-text` | `#047857` | `#6ee7b7` | 强调文字 |

#### 中性色

| Token | 浅色模式 | 暗色模式 |
|-------|---------|---------|
| `--color-bg-primary` | `#ffffff` | `#0f0f0f` |
| `--color-bg-secondary` | `#f9fafb` | `#171717` |
| `--color-bg-tertiary` | `#f3f4f6` | `#1f1f1f` |
| `--color-bg-elevated` | `#ffffff` | `#1a1a1a` |
| `--color-bg-hover` | `rgba(0,0,0,0.04)` | `rgba(255,255,255,0.06)` |
| `--color-bg-active` | `rgba(0,0,0,0.06)` | `rgba(255,255,255,0.08)` |
| `--color-text-primary` | `#111827` | `#f3f4f6` |
| `--color-text-secondary` | `#6b7280` | `#9ca3af` |
| `--color-text-tertiary` | `#9ca3af` | `#6b7280` |
| `--color-text-inverse` | `#ffffff` | `#111827` |
| `--color-border` | `#e5e7eb` | `#2a2a2a` |
| `--color-border-light` | `#f3f4f6` | `#1f1f1f` |

#### 语义色

| Token | 浅色模式 | 暗色模式 |
|-------|---------|---------|
| `--color-success` | `#10b981` | `#34d399` |
| `--color-warning` | `#f59e0b` | `#fbbf24` |
| `--color-danger` | `#ef4444` | `#f87171` |
| `--color-info` | `#6366f1` | `#818cf8` |

#### 风险等级色

| Token | 浅色模式 | 暗色模式 |
|-------|---------|---------|
| `--color-risk-high` | `#ef4444` | `#f87171` |
| `--color-risk-high-bg` | `#fef2f2` | `rgba(239,68,68,0.1)` |
| `--color-risk-medium` | `#f59e0b` | `#fbbf24` |
| `--color-risk-medium-bg` | `#fffbeb` | `rgba(245,158,11,0.1)` |
| `--color-risk-low` | `#6b7280` | `#9ca3af` |
| `--color-risk-low-bg` | `#f9fafb` | `rgba(107,114,128,0.1)` |

### 间距 (8px 网格)

| Token | 值 |
|-------|---|
| `--space-1` | 4px |
| `--space-2` | 8px |
| `--space-3` | 12px |
| `--space-4` | 16px |
| `--space-5` | 20px |
| `--space-6` | 24px |
| `--space-8` | 32px |
| `--space-10` | 40px |
| `--space-12` | 48px |
| `--space-16` | 64px |

### 圆角

| Token | 值 | 用途 |
|-------|---|------|
| `--radius-sm` | 6px | 输入框、小元素 |
| `--radius-md` | 10px | 卡片、按钮 |
| `--radius-lg` | 14px | 弹窗、大卡片 |
| `--radius-xl` | 20px | Auth 卡片 |
| `--radius-full` | 9999px | 胶囊、圆形 |

### 阴影 (Apple 风格多层柔和)

| Token | 浅色模式 | 暗色模式 |
|-------|---------|---------|
| `--shadow-xs` | `0 1px 2px rgba(0,0,0,0.04)` | `0 1px 2px rgba(0,0,0,0.2)` |
| `--shadow-sm` | `0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04)` | `0 1px 3px rgba(0,0,0,0.3)` |
| `--shadow-md` | `0 4px 6px -1px rgba(0,0,0,0.06), 0 2px 4px -2px rgba(0,0,0,0.04)` | `0 4px 6px rgba(0,0,0,0.3)` |
| `--shadow-lg` | `0 10px 15px -3px rgba(0,0,0,0.06), 0 4px 6px -4px rgba(0,0,0,0.04)` | `0 10px 15px rgba(0,0,0,0.35)` |
| `--shadow-xl` | `0 20px 25px -5px rgba(0,0,0,0.08), 0 8px 10px -6px rgba(0,0,0,0.04)` | `0 20px 25px rgba(0,0,0,0.4)` |

### 字体

| Token | 值 |
|-------|---|
| `--font-family` | `-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif` |
| `--font-mono` | `'SF Mono', 'Fira Code', 'Consolas', monospace` |
| `--text-xs` | 12px |
| `--text-sm` | 13px |
| `--text-base` | 14px |
| `--text-md` | 15px |
| `--text-lg` | 16px |
| `--text-xl` | 18px |
| `--text-2xl` | 20px |
| `--text-3xl` | 24px |
| `--leading-tight` | 1.25 |
| `--leading-normal` | 1.5 |
| `--leading-relaxed` | 1.75 |

### 过渡

| Token | 值 |
|-------|---|
| `--transition-fast` | 150ms cubic-bezier(0.4, 0, 0.2, 1) |
| `--transition-base` | 200ms cubic-bezier(0.4, 0, 0.2, 1) |
| `--transition-slow` | 300ms cubic-bezier(0.4, 0, 0.2, 1) |

### 布局

| Token | 值 |
|-------|---|
| `--sidebar-width` | 240px |
| `--sidebar-collapsed-width` | 64px |
| `--header-height` | 56px |
| `--content-max-width` | 1200px |

---

## Element Plus 主题覆盖

通过 CSS 变量覆盖 Element Plus 默认值，使其与设计令牌对齐。

### CSS 变量映射

```
--el-color-primary        → var(--color-accent)
--el-color-primary-light-3 → #6ee7b7
--el-color-primary-light-5 → #a7f3d0
--el-color-primary-light-7 → #d1fae5
--el-color-primary-light-9 → var(--color-accent-light)
--el-color-primary-dark-2  → var(--color-accent-hover)
--el-color-success         → var(--color-success)
--el-color-warning         → var(--color-warning)
--el-color-danger          → var(--color-danger)
--el-color-info            → var(--color-info)
--el-bg-color              → var(--color-bg-primary)
--el-bg-color-page         → var(--color-bg-secondary)
--el-bg-color-overlay      → var(--color-bg-elevated)
--el-text-color-primary    → var(--color-text-primary)
--el-text-color-regular    → var(--color-text-secondary)
--el-text-color-secondary  → var(--color-text-tertiary)
--el-border-color          → var(--color-border)
--el-border-color-light    → var(--color-border-light)
--el-border-radius-base    → var(--radius-md)
--el-border-radius-small   → var(--radius-sm)
--el-box-shadow            → var(--shadow-md)
--el-box-shadow-light      → var(--shadow-sm)
--el-font-family           → var(--font-family)
```

### 组件级覆盖要点

| 组件 | 覆盖策略 |
|------|---------|
| `el-card` | 无边框(浅色仅阴影)、`border-radius: var(--radius-lg)`、hover 阴影提升 |
| `el-button` | `border-radius: var(--radius-md)`、`font-weight: 500`、主按钮带淡色 `box-shadow` |
| `el-input` | 去掉内阴影，改为 `border + border-radius: var(--radius-md)`、focus 时强调色边框 |
| `el-table` | 无竖线、header 背景透明、header 文字小写+加粗+letter-spacing |
| `el-menu` | 去掉右边框、导航项圆角矩形高亮 |
| `el-tabs` | 底部指示线风格（替代 Element Plus 默认顶部 tab） |
| `el-tag` | 降低饱和度、圆角加大 |
| `el-pagination` | 简化样式、当前页用强调色圆形 |
| `el-dialog` | `border-radius: var(--radius-lg)`、backdrop 模糊 |
| `el-switch` | 过渡更柔和、开启态用强调色 |
| `el-progress` | 细条 (4px)、圆角、渐变色 |

---

## Logo 设计

简约单线条 SVG 小图标，风格要求：

- **元素**：盾牌轮廓 + 内部文档/文本线条
- **线条**：单线条描边，stroke-width 1.5-2px，无填充
- **尺寸**：24x24 或 28x28 viewBox
- **颜色**：跟随当前主题（浅色用 `--color-text-primary`，暗色同理）
- **用途**：侧边栏顶部 + Auth 页面标题上方

---

## 页面重设计规格

### Layout (主框架)

**结构：**

```
┌──────────┬──────────────────────────────────────┐
│          │  Header (56px)                        │
│ Sidebar  │  [页面标题]              [主题] [用户] │
│ (240px)  ├──────────────────────────────────────┤
│          │                                       │
│ [Logo]   │  Content (max-width: 1200px)          │
│ 导航项   │  <router-view> + Transition           │
│ ...      │                                       │
│ [折叠]   │                                       │
└──────────┴──────────────────────────────────────┘
```

**Sidebar 规格：**
- 宽度：240px / 折叠 64px
- 背景：`--color-bg-primary`（与内容区 `--color-bg-secondary` 形成分层）
- 顶部：Logo 图标 + "合同审查" 文字
- 导航项：圆角矩形，icon + 文字，hover 用 `--color-bg-hover`，active 用 `--color-accent-light` 背景 + `--color-accent-text` 文字色
- 底部：折叠按钮
- 折叠时仅显示图标，hover 显示 tooltip

**Header 规格：**
- 高度：56px
- 背景：半透明 + `backdrop-filter: blur(12px)` (可选)
- 左侧：当前页面标题（`--text-xl`, `font-weight: 600`）
- 右侧：ThemeToggle 组件 + 用户头像下拉（圆形首字母）

**路由过渡：** `<transition name="page-fade" mode="out-in">` 包裹 `<router-view>`

### Login / Register (认证页)

**布局：**
```
┌─────────────────────────────────────────┐
│                                         │
│           ┌──────────────────┐          │
│           │   [Logo 图标]    │          │
│           │  智能合同风险审查  │          │
│           │    登录以继续使用  │          │
│           │                  │          │
│           │  [用户名输入框]   │          │
│           │  [密码输入框]     │          │
│           │  [  登  录  ]     │          │
│           │                  │          │
│           │  还没有账号？注册  │          │
│           └──────────────────┘          │
│                                         │
└─────────────────────────────────────────┘
```

**规格：**
- 背景：`--color-bg-secondary`（纯净中性色，去掉紫色渐变）
- 卡片：居中，宽度 380px，`border-radius: var(--radius-xl)`，`box-shadow: var(--shadow-lg)`，内部 padding 40px
- Logo：单线条图标，居中，40x40
- 标题：`--text-2xl`, `font-weight: 600`, `--color-text-primary`
- 副标题：`--text-base`, `--color-text-secondary`
- 输入框：`size="large"`，间距 `--space-4`
- 主按钮：全宽 `type="primary"`, `size="large"`, `border-radius: var(--radius-md)`
- 链接：`--color-accent-text`，无下划线，hover 时下划线
- Register 与 Login 统一风格，仅表单字段不同（多一个确认密码）

### Upload (合同上传)

**布局：**
```
┌─────────────────────────────────────┐
│  上传合同文件                        │
│                                     │
│  ┌─────────────────────────────┐    │
│  │                             │    │
│  │   📄 拖拽文件到此处          │    │
│  │   或 点击选择               │    │
│  │   支持 PDF / Word, ≤20MB   │    │
│  │                             │    │
│  └─────────────────────────────┘    │
│                                     │
│  ○ 启用脱敏                         │
│                                     │
│  [ 上传预览 ]                       │
│                                     │
│  ── 文本预览 ──────────────────      │
│  ┌─────────────────────────────┐    │
│  │ (代码风格预览文本)           │    │
│  │ monospace, 灰色背景          │    │
│  └─────────────────────────────┘    │
│                                     │
│  [ 返回 ]   [ 提交审查 ]            │
│                                     │
│  [SSE 进度组件]                      │
└─────────────────────────────────────┘
```

**规格：**
- 最大宽度 800px，水平居中
- 上传区域：`border: 2px dashed var(--color-border)`，圆角 `--radius-lg`，hover 时边框变强调色
- 预览文本：`font-family: var(--font-mono)`，背景 `--color-bg-tertiary`，圆角 `--radius-md`，内边距 `--space-4`
- 操作按钮：水平排列，主要按钮强调色，次要按钮默认

### Report (审查报告)

**Tab 样式：** 底部指示线风格，当前 tab 下方显示强调色 2px 横线

**Tab 1 — 审查报告：**
```
┌─────────────────────────────────────┐
│  摘要文本 (大字号, 行高宽松)         │
│                                     │
│  ┌────────┐ ┌────────┐ ┌────────┐  │
│  │ 🔴 高危 │ │ 🟡 中危 │ │ ⚪ 低危 │  │
│  │   3    │ │   2    │ │   1    │  │
│  └────────┘ └────────┘ └────────┘  │
│                                     │
│  ── 风险详情 ──────────────────      │
│                                     │
│  ┌─────────────────────────────┐    │
│  │ 条款 5        HIGH  违约金   │    │
│  │ ──────────────────────────  │    │
│  │ > 条款原文 (引用样式)        │    │
│  │                             │    │
│  │ 风险描述                     │    │
│  │ xxxxxxxxxxxxxxxxxx          │    │
│  │                             │    │
│  │ 修改建议                     │    │
│  │ xxxxxxxxxxxxxxxxxx          │    │
│  │                             │    │
│  │ 关联法条  [民法典 xxx] [...] │    │
│  └─────────────────────────────┘    │
│  ...                                │
└─────────────────────────────────────┘
```

**规格：**
- 摘要：`--text-lg`，`--color-text-primary`，行高 1.75
- 风险统计：3 个等宽色块卡片，`border-radius: var(--radius-md)`，数字大字号 + 标签小字号，背景用对应 `--color-risk-*-bg`
- 风险条目卡片：
  - 无边框，仅 `--shadow-xs`，`--radius-lg`
  - 标题行：条款编号（`--text-md` 加粗）+ 风险等级 badge（圆角矩形，背景色对应等级）+ 风险类型 tag
  - 原文区：左侧 3px 强调色边框 + `--color-bg-tertiary` 背景 + `--font-mono`
  - 描述/建议：小标题（`--text-xs`, `--color-text-secondary`, 大写）+ 正文
  - 法条：小 tag 列表，hover popover

**Tab 2 — 合同原文：** monospace 字体，灰色背景块，最大高度限制 + 滚动

**Tab 3 — 审查过程：** Agent 日志列表，每条显示 agent 名 + 时间 + 内容块

### History (审查历史)

**规格：**
- 筛选器：Pill 样式分段控件（类似 iOS Segmented Control），替代 el-tabs
- 表格：无竖线、行高加大、hover 用 `--color-bg-hover`
- 状态 badge：圆角矩形，颜色柔和（SUCCESS=绿色, FAILED=红色, 进行中=黄色/橙色, PENDING=灰色）
- 分页：简化样式，当前页圆底强调色
- 空状态：大图标 + 文字提示

### SseProgress (实时进度)

**布局：** 竖向时间线

```
  ● 解析文档          ✓ 完成
  │
  ● 检索法条          ◉ 进行中 (脉冲)
  │
  ○ 审查条款          等待中
  │
  ○ 汇总报告          等待中
```

**规格：**
- 时间线：左侧竖线连接各阶段圆点
- 圆点状态：pending(空心灰) / active(实心强调色+脉冲) / done(实心绿+勾) / error(实心红+叉)
- 进度条：顶部，`height: 4px`，圆角，渐变色（从强调色到浅绿）
- 计时器：右上角小标签 `MM:SS`
- LLM 输出区：暗色代码块风格 (`--font-mono`)，背景 `--color-bg-tertiary`，自动滚动

---

## 动画与过渡

| 动画名 | 效果 | 用途 |
|--------|------|------|
| `page-fade` | opacity 0→1, 200ms | 路由切换 |
| `slideUp` | opacity 0→1 + translateY(12px→0), 300ms | 卡片首次出现 |
| `pulse` | opacity 1→0.5→1, 2s infinite | SSE 活跃阶段 |
| `shimmer` | 渐变背景位移, 1.5s infinite | 骨架屏加载态 |

---

## 暗色模式实现

### 技术方案

1. `useTheme()` composable 管理主题状态
2. 默认跟随系统 `prefers-color-scheme`，可手动切换
3. 手动选择持久化到 `localStorage('theme')`
4. 通过 `document.documentElement.classList.toggle('dark')` 切换
5. 所有颜色通过 CSS 变量控制，`.dark` 选择器重定义变量值

### ThemeToggle 组件

- 位置：Header 右侧
- 形态：太阳/月亮图标按钮，点击切换
- 动画：图标旋转过渡

### 暗色模式配色原则

- 背景接近纯黑 (`#0f0f0f`) 而非深灰，参考 ChatGPT 风格
- 卡片与背景通过微弱亮度差分层 (`#0f0f0f` vs `#1a1a1a`)
- 文字用浅灰而非纯白，减少刺眼感
- 强调色提高亮度 (`#34d399`) 以在深色背景上保持可读性
- 阴影加深以维持层次感

---

## 响应式设计

| 断点 | 行为 |
|------|------|
| `≥ 1024px` | 完整布局：侧边栏 240px + 内容区 |
| `768px - 1023px` | 侧边栏自动折叠为图标模式 (64px) |
| `< 768px` | 侧边栏隐藏（overlay 抽屉模式），内容区全宽，padding 缩减 |

**优先级较低，可后续迭代实现。**

---

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/assets/styles/tokens.css` | 新增 | 设计令牌 CSS 变量 |
| `src/assets/styles/global.css` | 新增 | CSS Reset、滚动条、全局排版 |
| `src/assets/styles/element-overrides.css` | 新增 | Element Plus 主题覆盖 + 暗色模式 |
| `src/assets/styles/animations.css` | 新增 | 动画与过渡 |
| `src/assets/logo.svg` | 新增 | 单线条 SVG Logo |
| `src/composables/useTheme.js` | 新增 | 暗色模式 composable |
| `src/components/ThemeToggle.vue` | 新增 | 主题切换按钮组件 |
| `src/main.js` | 修改 | 引入新样式文件 |
| `src/App.vue` | 修改 | 添加 `.dark` class 管理 + 页面过渡 |
| `src/views/Layout.vue` | 重写 | 现代侧边栏 + 顶栏 |
| `src/views/Login.vue` | 重写 | 极简认证页 |
| `src/views/Register.vue` | 重写 | 与 Login 统一风格 |
| `src/views/Upload.vue` | 重写 | 现代上传卡片 |
| `src/views/Report.vue` | 重写 | 更好的报告排版 |
| `src/views/History.vue` | 重写 | 更干净的列表 |
| `src/components/SseProgress.vue` | 重写 | 现代进度可视化 |

---

## 实施批次

### 批次 1：设计系统基础

| 任务 | 文件 |
|------|------|
| 1.1 创建设计令牌 | `tokens.css` |
| 1.2 创建全局样式 | `global.css` |
| 1.3 创建 EP 主题覆盖 | `element-overrides.css` |
| 1.4 创建动画样式 | `animations.css` |
| 1.5 创建暗色模式 composable | `useTheme.js` |
| 1.6 创建 Logo | `logo.svg` |
| 1.7 更新入口文件 | `main.js` + `App.vue` |

### 批次 2：Layout 重写

| 任务 | 文件 |
|------|------|
| 2.1 创建 ThemeToggle 组件 | `ThemeToggle.vue` |
| 2.2 重写 Layout | `Layout.vue` |

### 批次 3：Auth 页面

| 任务 | 文件 |
|------|------|
| 3.1 重写 Login | `Login.vue` |
| 3.2 重写 Register | `Register.vue` |

### 批次 4：Upload 页面

| 任务 | 文件 |
|------|------|
| 4.1 重写 Upload | `Upload.vue` |

### 批次 5：Report 页面

| 任务 | 文件 |
|------|------|
| 5.1 重写 Report | `Report.vue` |

### 批次 6：History 页面

| 任务 | 文件 |
|------|------|
| 6.1 重写 History | `History.vue` |

### 批次 7：SSE 进度组件

| 任务 | 文件 |
|------|------|
| 7.1 重写 SseProgress | `SseProgress.vue` |

### 批次 8：响应式适配

| 任务 | 文件 |
|------|------|
| 8.1 媒体查询适配 | 各 `.vue` 文件 |

---

## 依赖关系

```
批次 1 (tokens + global + EP overrides + animations + useTheme + Logo)
  │
  ├─→ 批次 2 (Layout + ThemeToggle)
  │     │
  │     ├─→ 批次 3 (Login / Register)
  │     ├─→ 批次 4 (Upload)
  │     ├─→ 批次 5 (Report)
  │     ├─→ 批次 6 (History)
  │     └─→ 批次 7 (SseProgress)
  │
  └─→ 批次 8 (Responsive, 可最后统一做)
```

批次 2-7 可按任意顺序逐页实施，每完成一个页面即可预览效果。
