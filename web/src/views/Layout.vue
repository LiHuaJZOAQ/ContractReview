<template>
  <div class="app-layout" :class="{ collapsed: sidebarCollapsed }">
    <aside class="sidebar">
      <div class="sidebar-header">
        <transition name="fade">
          <svg v-if="!sidebarCollapsed" class="brand-icon" viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2L4 7v7c0 5.25 4.25 10.74 10 12 5.75-1.26 10-6.75 10-12V7L14 2z"/>
            <line x1="10" y1="11" x2="18" y2="11"/>
            <line x1="10" y1="15" x2="18" y2="15"/>
            <line x1="10" y1="19" x2="15" y2="19"/>
          </svg>
        </transition>
        <transition name="fade">
          <span v-if="!sidebarCollapsed" class="brand-text">合同审查系统</span>
        </transition>
        <button class="collapse-toggle" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <line x1="9" y1="3" x2="9" y2="21"/>
          </svg>
        </button>
      </div>

      <div v-if="!sidebarCollapsed" class="sidebar-action">
        <router-link to="/upload" class="new-review-btn">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span class="new-review-text">新建审查</span>
          <span class="shortcut-hint">Ctrl K</span>
        </router-link>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
          :title="sidebarCollapsed ? item.label : undefined"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <transition name="fade">
            <span v-if="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
          </transition>
        </router-link>
      </nav>

      <div v-if="!sidebarCollapsed" class="sidebar-records">
        <button class="records-toggle" @click="recordsExpanded = !recordsExpanded">
          <span class="toggle-dots">···</span>
          <span class="toggle-text">{{ recordsExpanded ? '收起记录' : '展开记录' }}</span>
          <span class="toggle-arrow" :class="{ expanded: recordsExpanded }">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </span>
        </button>
        <transition name="expand">
          <div v-if="recordsExpanded" class="records-list">
            <router-link
              v-for="record in recentRecords"
              :key="record.taskId"
              :to="`/report/${record.taskId}`"
              class="record-item"
            >
              <span class="record-icon">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
              </span>
              <span class="record-title">{{ record.fileName || '未命名' }}</span>
            </router-link>
            <div v-if="recentRecords.length === 0" class="records-empty">暂无审查记录</div>
          </div>
        </transition>
      </div>

      <div class="sidebar-spacer"></div>

      <div v-if="!sidebarCollapsed" class="sidebar-quota">
        <div class="quota-info">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
          <span>剩余额度: {{ quotaRemaining }}/{{ quotaTotal }} 次</span>
        </div>
      </div>

      <div class="sidebar-footer">
        <template v-if="!sidebarCollapsed">
          <div class="footer-actions">
            <ThemeToggle mode="standalone" />
          </div>
          <el-dropdown trigger="click" class="user-card-dropdown" popper-class="user-card-popper">
            <button class="user-card">
              <span class="user-avatar">{{ userInitial }}</span>
              <span class="user-name">{{ auth.username || auth.userId }}</span>
              <svg class="user-card-arrow" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span style="color: var(--color-text-secondary)">{{ auth.username || auth.userId }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided>
                  <ThemeToggle mode="dropdown" />
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <button class="user-btn-collapsed" :title="auth.userId">
            <span class="user-avatar">{{ userInitial }}</span>
          </button>
        </template>
      </div>
    </aside>

    <div class="main-area">
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getHistory } from '@/api/contract'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const sidebarCollapsed = ref(false)
const recordsExpanded = ref(true)
const recentRecords = ref([])
const quotaRemaining = ref(10)
const quotaTotal = ref(10)

const navItems = [
  { 
    to: '/upload', 
    label: '合同上传',
    icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>'
  },
  { 
    to: '/history', 
    label: '审查历史',
    icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>'
  }
]

const userInitial = computed(() => {
  const name = auth.username || String(auth.userId || '')
  return name.charAt(0).toUpperCase() || '?'
})

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}

async function fetchRecentRecords() {
  try {
    const res = await getHistory(1, 5)
    recentRecords.value = res.tasks || []
  } catch {
    recentRecords.value = []
  }
}

onMounted(() => {
  fetchRecentRecords()
})

watch(
  () => route.path,
  () => {
    fetchRecentRecords()
  }
)
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: var(--sidebar-width);
  background: var(--color-bg-primary);
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-slow);
  flex-shrink: 0;
  z-index: 10;
}
.app-layout.collapsed .sidebar {
  width: var(--sidebar-collapsed-width);
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  height: var(--header-height);
  overflow: hidden;
  white-space: nowrap;
}
.app-layout.collapsed .sidebar-header {
  justify-content: center;
}

.brand-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  color: var(--color-accent);
}

.brand-text {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.collapse-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-left: auto;
}
.collapse-toggle:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.sidebar-action {
  padding: 0 var(--space-4) var(--space-3);
}

.new-review-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: var(--text-base);
  font-weight: 500;
  font-family: var(--font-ui);
  text-decoration: none;
  transition: all var(--transition-fast);
  cursor: pointer;
}
.new-review-btn:hover {
  background: var(--color-bg-hover);
  border-color: var(--color-accent);
  text-decoration: none;
}

.new-review-text {
  flex: 1;
  text-align: left;
}

.shortcut-hint {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--color-bg-tertiary);
  color: var(--color-text-tertiary);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
}

.sidebar-nav {
  padding: 0 var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: var(--text-base);
  font-weight: 500;
  font-family: var(--font-ui);
  text-decoration: none;
  transition: all var(--transition-fast);
  white-space: nowrap;
  overflow: hidden;
}
.app-layout.collapsed .nav-item {
  justify-content: center;
  padding: var(--space-3);
}
.nav-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
  text-decoration: none;
}
.nav-item.active {
  background: var(--color-accent-light);
  color: var(--color-accent-text);
}

.nav-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.sidebar-records {
  margin-top: var(--space-3);
  padding: 0 var(--space-3);
  border-top: 1px solid var(--color-border-light);
  padding-top: var(--space-3);
}

.records-toggle {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
  font-family: var(--font-ui);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.records-toggle:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-secondary);
}

.toggle-dots {
  font-weight: bold;
  letter-spacing: 2px;
}

.toggle-text {
  flex: 1;
  text-align: left;
}

.toggle-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--transition-fast);
}
.toggle-arrow.expanded {
  transform: rotate(180deg);
}

.records-list {
  padding: var(--space-2) 0;
}

.record-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  font-family: var(--font-ui);
  text-decoration: none;
  transition: all var(--transition-fast);
  overflow: hidden;
}
.record-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
  text-decoration: none;
}

.record-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--color-text-tertiary);
}

.record-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.records-empty {
  padding: var(--space-3);
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
  font-family: var(--font-ui);
}

.sidebar-spacer {
  flex: 1;
}

.sidebar-quota {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--color-border-light);
}

.quota-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-accent-light);
  color: var(--color-accent-text);
  font-size: var(--text-sm);
  font-weight: 500;
  font-family: var(--font-ui);
}

.sidebar-footer {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.app-layout.collapsed .sidebar-footer {
  display: flex;
  justify-content: center;
}

.footer-actions {
  width: 100%;
}

.user-card-dropdown {
  width: 100%;
}

.user-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  background: var(--color-bg-primary);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.user-card:hover {
  background: var(--color-bg-hover);
  border-color: var(--color-border);
}

.user-card .user-avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: var(--color-accent-light);
  color: var(--color-accent-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-sm);
  font-weight: 600;
  flex-shrink: 0;
}

.user-name {
  flex: 1;
  text-align: left;
  font-size: var(--text-sm);
  font-weight: 500;
  font-family: var(--font-ui);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card-arrow {
  flex-shrink: 0;
  color: var(--color-text-tertiary);
}

.user-btn-collapsed {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--color-accent-light);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.user-btn-collapsed:hover {
  box-shadow: var(--shadow-sm);
}

.user-btn-collapsed .user-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: var(--color-accent-light);
  color: var(--color-accent-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-sm);
  font-weight: 600;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
  background: var(--color-bg-secondary);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-fast);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.expand-enter-active,
.expand-leave-active {
  transition: all var(--transition-base);
  overflow: hidden;
}
.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}
.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 300px;
}

@media (max-width: 1023px) {
  .app-layout:not(.collapsed) .sidebar {
    width: var(--sidebar-collapsed-width);
  }
  .app-layout:not(.collapsed) .sidebar-action,
  .app-layout:not(.collapsed) .sidebar-records,
  .app-layout:not(.collapsed) .sidebar-quota {
    display: none;
  }
  .app-layout:not(.collapsed) .sidebar-footer {
    display: block;
  }
}

@media (max-width: 767px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform var(--transition-slow);
  }
  .app-layout:not(.collapsed) .sidebar {
    transform: translateX(0);
    width: var(--sidebar-width);
    box-shadow: var(--shadow-xl);
  }
  .app-layout:not(.collapsed) .sidebar-action,
  .app-layout:not(.collapsed) .sidebar-records,
  .app-layout:not(.collapsed) .sidebar-quota {
    display: flex;
  }
  .app-layout:not(.collapsed) .sidebar-footer {
    display: block;
  }
  .content {
    padding: var(--space-4);
  }
}
</style>
