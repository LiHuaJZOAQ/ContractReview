<template>
  <!-- 模式1: 独立按钮 — 放在侧边栏底部，宽度与用户信息框一致 -->
  <button v-if="mode === 'standalone'" class="theme-btn-standalone" @click="toggle" :title="isDark ? '切换浅色模式' : '切换深色模式'">
    <svg v-if="isDark" class="theme-btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="12" cy="12" r="5"/>
      <line x1="12" y1="1" x2="12" y2="3"/>
      <line x1="12" y1="21" x2="12" y2="23"/>
      <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
      <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
      <line x1="1" y1="12" x2="3" y2="12"/>
      <line x1="21" y1="12" x2="23" y2="12"/>
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
      <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
    </svg>
    <svg v-else class="theme-btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
    </svg>
    <span class="theme-btn-label">{{ isDark ? '浅色模式' : '深色模式' }}</span>
  </button>

  <!-- 模式2: 下拉菜单行 — Material Design list item -->
  <div v-else class="theme-list-item" @click.stop="toggle">
    <svg v-if="isDark" class="theme-list-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="12" cy="12" r="5"/>
      <line x1="12" y1="1" x2="12" y2="3"/>
      <line x1="12" y1="21" x2="12" y2="23"/>
      <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
      <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
      <line x1="1" y1="12" x2="3" y2="12"/>
      <line x1="21" y1="12" x2="23" y2="12"/>
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
      <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
    </svg>
    <svg v-else class="theme-list-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
    </svg>
    <span class="theme-list-label">{{ isDark ? '浅色模式' : '深色模式' }}</span>
    <span class="theme-list-check">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="20 6 9 17 4 12"/>
      </svg>
    </span>
  </div>
</template>

<script setup>
import { useTheme } from '@/composables/useTheme'
const { isDark, toggle } = useTheme()

defineProps({
  mode: {
    type: String,
    default: 'standalone',
    validator: (v) => ['standalone', 'dropdown'].includes(v)
  }
})
</script>

<style scoped>
/* ——— 独立按钮: 宽度与 user-card 等宽，Material Design outlined button ——— */
.theme-btn-standalone {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: var(--text-sm);
  font-weight: 500;
  font-family: var(--font-ui);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.theme-btn-standalone:hover {
  background: var(--color-bg-hover);
  border-color: var(--color-border);
}

.theme-btn-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  color: var(--color-accent);
}

.theme-btn-label {
  flex: 1;
  text-align: left;
}

/* ——— 下拉菜单行: Material Design list item ——— */
.theme-list-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.theme-list-item:hover {
  background: var(--color-bg-hover);
}

.theme-list-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  color: var(--color-text-secondary);
}

.theme-list-label {
  flex: 1;
  text-align: left;
  font-size: var(--text-sm);
  font-family: var(--font-ui);
  color: var(--color-text-primary);
}

.theme-list-check {
  flex-shrink: 0;
  color: var(--color-accent);
}
</style>
