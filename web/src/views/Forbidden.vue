<template>
  <div class="error-page">
    <div class="error-card">
      <div class="error-code">403</div>
      <div class="error-divider forbidden"></div>
      <h2 class="error-title">无访问权限</h2>
      <p class="error-desc">您没有权限访问该页面，请联系管理员</p>
      <div class="error-actions">
        <button class="error-btn primary" @click="goBack">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
          </svg>
          返回上页
        </button>
        <button v-if="auth.isAuthenticated" class="error-btn primary" @click="goWorkspace">
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
function goBack() { router.back() }
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
  color: var(--color-danger);
  line-height: 1;
  font-family: var(--font-mono);
  opacity: 0.85;
}

.error-divider {
  width: 48px;
  height: 3px;
  border-radius: 2px;
  margin: var(--space-6) auto;
  background: var(--color-danger);
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
