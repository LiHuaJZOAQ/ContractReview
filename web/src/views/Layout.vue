<template>
  <div class="app-layout" :class="{ collapsed: sidebarCollapsed }">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img src="@/assets/logo.svg" alt="Logo" class="brand-icon" />
        <transition name="fade">
          <span v-if="!sidebarCollapsed" class="brand-text">合同审查</span>
        </transition>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
        >
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <transition name="fade">
            <span v-if="!sidebarCollapsed" class="nav-label">{{ item.label }}</span>
          </transition>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon :size="18">
            <DArrowLeft v-if="!sidebarCollapsed" />
            <DArrowRight v-else />
          </el-icon>
        </button>
      </div>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <span class="page-title">{{ currentPageTitle }}</span>
        </div>
        <div class="topbar-right">
          <ThemeToggle />
          <el-dropdown trigger="click">
            <button class="user-btn">
              <span class="user-avatar">{{ userInitial }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span style="color: var(--color-text-secondary)">{{ auth.userId }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

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
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Upload, Clock, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const sidebarCollapsed = ref(false)

const navItems = [
  { to: '/upload', label: '合同上传', icon: Upload },
  { to: '/history', label: '审查历史', icon: Clock },
]

const routeTitleMap = {
  Upload: '合同上传',
  History: '审查历史',
  Report: '审查报告',
}

const currentPageTitle = computed(() => routeTitleMap[route.name] || '')

const userInitial = computed(() => {
  const id = String(auth.userId || '')
  return id.charAt(0).toUpperCase() || '?'
})

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
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

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  height: var(--header-height);
  border-bottom: 1px solid var(--color-border-light);
  overflow: hidden;
  white-space: nowrap;
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

.sidebar-nav {
  flex: 1;
  padding: var(--space-3);
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
  text-decoration: none;
  transition: all var(--transition-fast);
  white-space: nowrap;
  overflow: hidden;
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

.sidebar-footer {
  padding: var(--space-3);
  border-top: 1px solid var(--color-border-light);
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 36px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.collapse-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--space-6);
  background: var(--color-bg-primary);
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
}

.page-title {
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--color-text-primary);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--color-accent-light);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.user-btn:hover {
  box-shadow: var(--shadow-sm);
}

.user-avatar {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-accent-text);
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

@media (max-width: 1023px) {
  .app-layout:not(.collapsed) .sidebar {
    width: var(--sidebar-collapsed-width);
  }
  .app-layout:not(.collapsed) .brand-text,
  .app-layout:not(.collapsed) .nav-label {
    display: none;
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
  .app-layout:not(.collapsed) .brand-text,
  .app-layout:not(.collapsed) .nav-label {
    display: inline;
  }
  .content {
    padding: var(--space-4);
  }
}
</style>
