<template>
  <div class="error-page">
    <div class="error-card">
      <div class="error-code">404</div>
      <div class="error-divider"></div>
      <h2 class="error-title">页面未找到</h2>
      <p class="error-desc">您访问的页面不存在或已被移除</p>
      <div class="error-actions">
        <button v-if="auth.isAuthenticated" class="error-btn primary" @click="goWorkspace">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          返回工作台
        </button>
        <button class="error-btn secondary" @click="goHome">返回首页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const router = useRouter()
const auth = useAuthStore()
function goHome() { router.push('/') }
function goWorkspace() { router.push('/upload') }
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--color-bg-secondary);
  font-family: var(--font-ui);
  animation: fade-in 0.3s ease-out;
}

.error-card {
  text-align: center;
  padding: var(--space-12) var(--space-16);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.error-code {
  font-size: 96px;
  font-weight: 700;
  color: var(--color-accent);
  line-height: 1;
  font-family: var(--font-mono);
  opacity: 0.9;
}

.error-divider {
  width: 48px;
  height: 3px;
  background: var(--color-accent);
  border-radius: 2px;
  margin: var(--space-6) auto;
}

.error-title {
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-3);
}

.error-desc {
  font-size: var(--text-base);
  color: var(--color-text-tertiary);
  margin: 0 0 var(--space-8);
}

.error-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: center;
}

.error-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-6);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: 500;
  font-family: var(--font-ui);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.error-btn.primary {
  background: var(--color-accent);
  color: var(--color-text-inverse);
}
.error-btn.primary:hover {
  background: var(--color-accent-hover);
  box-shadow: var(--shadow-md);
}

.error-btn.secondary {
  background: var(--color-bg-tertiary);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}
.error-btn.secondary:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
