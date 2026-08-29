<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">管理后台</h2>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon users-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.totalUsers }}</span>
          <span class="stat-label">总用户数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon tasks-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14,2 14,8 20,8"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.totalTasks }}</span>
          <span class="stat-label">总任务数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon success-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20,6 9,17 4,12"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.successTasks }}</span>
          <span class="stat-label">成功任务</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon failed-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.failedTasks }}</span>
          <span class="stat-label">失败任务</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon processing-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.processingTasks }}</span>
          <span class="stat-label">进行中任务</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon laws-icon">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.totalLaws }}</span>
          <span class="stat-label">法规数量</span>
        </div>
      </div>
    </div>

    <div class="admin-section">
      <div class="section-header">
        <h3 class="section-title">用户管理</h3>
      </div>
      <div class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户名</th>
              <th>角色</th>
              <th>剩余额度</th>
              <th>注册时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.userId" class="table-row">
              <td class="td-id">{{ user.userId }}</td>
              <td>{{ user.username }}</td>
              <td>
                <span class="role-badge" :class="'role-' + (user.role || 'USER').toLowerCase()">
                  {{ user.role === 'ADMIN' ? '管理员' : '用户' }}
                </span>
              </td>
              <td>{{ user.reviewQuota }}</td>
              <td>{{ user.createdAt || '-' }}</td>
              <td class="td-actions">
                <button class="action-btn" @click="handleToggleRole(user)">
                  {{ user.role === 'ADMIN' ? '设为用户' : '设为管理员' }}
                </button>
                <button class="action-btn" @click="handleResetQuota(user)">重置额度</button>
                <button class="action-btn action-danger" @click="handleDeleteUser(user)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSystemStats, getAdminUsers, updateUserRole, resetUserQuota, deleteUser } from '@/api/admin'

const stats = ref({
  totalUsers: 0, totalTasks: 0, successTasks: 0,
  failedTasks: 0, processingTasks: 0, totalLaws: 0
})
const users = ref([])

async function fetchStats() {
  try {
    stats.value = await getSystemStats()
  } catch {}
}

async function fetchUsers() {
  try {
    users.value = await getAdminUsers()
  } catch {}
}

async function handleToggleRole(user) {
  const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  try {
    await ElMessageBox.confirm(`确定将「${user.username}」设为${newRole === 'ADMIN' ? '管理员' : '普通用户'}？`, '确认操作', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await updateUserRole(user.userId, newRole)
    user.role = newRole
    ElMessage.success('角色已更新')
  } catch {}
}

async function handleResetQuota(user) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的额度值', '重置额度', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: /^\d+$/,
      inputErrorMessage: '请输入数字',
      inputValue: '10'
    })
    await resetUserQuota(user.userId, parseInt(value))
    user.reviewQuota = parseInt(value)
    ElMessage.success('额度已重置')
  } catch {}
}

async function handleDeleteUser(user) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${user.username}」？此操作不可恢复。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteUser(user.userId)
    users.value = users.value.filter(u => u.userId !== user.userId)
    await fetchStats()
    ElMessage.success('用户已删除')
  } catch {}
}

onMounted(() => {
  fetchStats()
  fetchUsers()
})
</script>

<style scoped>
.admin-page {
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--space-6);
}
.page-title {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.users-icon { background: #ede9fe; color: #7c3aed; }
.tasks-icon { background: #dbeafe; color: #2563eb; }
.success-icon { background: var(--color-accent-light); color: var(--color-accent); }
.failed-icon { background: var(--color-risk-high-bg); color: var(--color-risk-high); }
.processing-icon { background: var(--color-risk-medium-bg); color: var(--color-risk-medium); }
.laws-icon { background: #fef3c7; color: #d97706; }

.stat-info {
  display: flex;
  flex-direction: column;
}
.stat-value {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}
.stat-label {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.admin-section {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.section-header {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
}
.section-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.table-wrapper {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table th {
  text-align: left;
  padding: var(--space-3) var(--space-5);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--color-border-light);
  white-space: nowrap;
}
.data-table td {
  padding: var(--space-3) var(--space-5);
  font-size: var(--text-sm);
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border-light);
}
.table-row:hover {
  background: var(--color-bg-hover);
}
.table-row:last-child td {
  border-bottom: none;
}

.td-id {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
.td-actions {
  display: flex;
  gap: var(--space-2);
}

.role-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
}
.role-admin {
  background: var(--color-risk-high-bg);
  color: var(--color-risk-high);
}
.role-user {
  background: var(--color-accent-light);
  color: var(--color-accent-text);
}

.action-btn {
  border: none;
  background: none;
  color: var(--color-accent-text);
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  font-family: var(--font-ui);
  white-space: nowrap;
}
.action-btn:hover {
  background: var(--color-accent-light);
}
.action-btn.action-danger {
  color: var(--color-risk-high);
}
.action-btn.action-danger:hover {
  background: var(--color-risk-high-bg);
}

@media (max-width: 767px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
