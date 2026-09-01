<template>
  <div class="monitor-page">
    <div class="page-header">
      <h2 class="page-title">系统监控</h2>
      <button class="refresh-btn" @click="refreshAll" :disabled="loading">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" :class="{ spinning: loading }">
          <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
          <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
        </svg>
        刷新
      </button>
    </div>

    <div class="monitor-grid">
      <div class="monitor-card">
        <div class="card-header">
          <span class="card-icon cpu-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>
          </span>
          <span class="card-title">CPU</span>
        </div>
        <div class="card-body">
          <div class="metric-row">
            <span class="metric-label">负载</span>
            <span class="metric-value">{{ monitor.cpuLoad }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">核心数</span>
            <span class="metric-value">{{ monitor.availableProcessors }}</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill cpu-fill" :style="{ width: cpuPercent + '%' }"></div>
          </div>
          <span class="progress-text">{{ cpuPercent }}%</span>
        </div>
      </div>

      <div class="monitor-card">
        <div class="card-header">
          <span class="card-icon memory-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="6" width="20" height="12" rx="2"/><line x1="6" y1="10" x2="6" y2="14"/><line x1="10" y1="10" x2="10" y2="14"/><line x1="14" y1="10" x2="14" y2="14"/><line x1="18" y1="10" x2="18" y2="14"/></svg>
          </span>
          <span class="card-title">JVM 内存</span>
        </div>
        <div class="card-body">
          <div class="metric-row">
            <span class="metric-label">已用</span>
            <span class="metric-value">{{ formatBytes(monitor.heapUsed) }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">最大</span>
            <span class="metric-value">{{ formatBytes(monitor.heapMax) }}</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill memory-fill" :style="{ width: memoryPercent + '%' }"></div>
          </div>
          <span class="progress-text">{{ memoryPercent }}%</span>
        </div>
      </div>

      <div class="monitor-card">
        <div class="card-header">
          <span class="card-icon thread-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          </span>
          <span class="card-title">线程</span>
        </div>
        <div class="card-body">
          <div class="metric-row">
            <span class="metric-label">当前</span>
            <span class="metric-value">{{ monitor.threadCount }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">峰值</span>
            <span class="metric-value">{{ monitor.peakThreadCount }}</span>
          </div>
        </div>
      </div>

      <div class="monitor-card">
        <div class="card-header">
          <span class="card-icon info-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          </span>
          <span class="card-title">运行信息</span>
        </div>
        <div class="card-body">
          <div class="metric-row">
            <span class="metric-label">运行时间</span>
            <span class="metric-value">{{ formatUptime(monitor.uptime) }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">Java</span>
            <span class="metric-value text-sm">{{ monitor.javaVersion }}</span>
          </div>
          <div class="metric-row">
            <span class="metric-label">OS</span>
            <span class="metric-value text-sm">{{ monitor.osName }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="section-header">
      <h3 class="section-title">操作日志</h3>
      <div class="filter-bar">
        <select v-model="logFilter" class="filter-select" @change="fetchLogs(1)">
          <option value="">全部操作</option>
          <option value="REGISTER">注册</option>
          <option value="UPLOAD">上传</option>
          <option value="SUBMIT">提交审查</option>
          <option value="VIEW_REPORT">查看报告</option>
          <option value="RETRY">重试</option>
        </select>
      </div>
    </div>

    <div class="log-table-wrap">
      <table class="log-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>用户</th>
            <th>操作</th>
            <th>任务ID</th>
            <th>IP</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in operationLogs" :key="log.id">
            <td class="cell-time">{{ formatTime(log.createdAt) }}</td>
            <td>{{ log.username }}</td>
            <td><span class="action-tag" :class="'action-' + (log.action || '').toLowerCase()">{{ actionLabel(log.action) }}</span></td>
            <td>{{ log.taskId || '-' }}</td>
            <td class="cell-mono">{{ log.ipAddress || '-' }}</td>
            <td class="cell-detail">{{ formatDetail(log.detail) }}</td>
          </tr>
          <tr v-if="operationLogs.length === 0">
            <td colspan="6" class="cell-empty">暂无操作日志</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pagination" v-if="logTotal > logSize">
      <button class="page-btn" :disabled="logPage <= 1" @click="fetchLogs(logPage - 1)">上一页</button>
      <span class="page-info">{{ logPage }} / {{ Math.ceil(logTotal / logSize) }}</span>
      <button class="page-btn" :disabled="logPage >= Math.ceil(logTotal / logSize)" @click="fetchLogs(logPage + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getSystemMonitor, getOperationLogs } from '@/api/admin'

const loading = ref(false)
const monitor = ref({
  availableProcessors: 0,
  jvmMemoryMax: 0,
  jvmMemoryUsed: 0,
  heapUsed: 0,
  heapMax: 0,
  nonHeapUsed: 0,
  threadCount: 0,
  peakThreadCount: 0,
  uptime: 0,
  javaVersion: '-',
  osName: '-',
  cpuLoad: 0
})

const operationLogs = ref([])
const logPage = ref(1)
const logSize = 20
const logTotal = ref(0)
const logFilter = ref('')

const cpuPercent = computed(() => {
  if (!monitor.value.availableProcessors) return 0
  return Math.min(100, Math.round((monitor.value.cpuLoad / monitor.value.availableProcessors) * 100))
})

const memoryPercent = computed(() => {
  if (!monitor.value.heapMax) return 0
  return Math.min(100, Math.round((monitor.value.heapUsed / monitor.value.heapMax) * 100))
})

function formatBytes(bytes) {
  if (!bytes || bytes < 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let v = bytes
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return v.toFixed(1) + ' ' + units[i]
}

function formatUptime(ms) {
  if (!ms) return '-'
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return d + '天' + h + '小时' + m + '分'
  if (h > 0) return h + '小时' + m + '分'
  return m + '分钟'
}

function formatTime(iso) {
  if (!iso) return '-'
  return iso.replace('T', ' ').substring(0, 19)
}

function formatDetail(detail) {
  if (!detail) return '-'
  try {
    const obj = JSON.parse(detail)
    return obj.method ? obj.method + (obj.duration ? ' (' + obj.duration + 'ms)' : '') : detail
  } catch { return detail }
}

function actionLabel(action) {
  const map = { REGISTER: '注册', UPLOAD: '上传', SUBMIT: '提交审查', VIEW_REPORT: '查看报告', RETRY: '重试' }
  return map[action] || action
}

async function fetchMonitor() {
  try {
    const res = await getSystemMonitor()
    monitor.value = res
  } catch { /* ignore */ }
}

async function fetchLogs(page = 1) {
  try {
    const res = await getOperationLogs(page, logSize, logFilter.value)
    operationLogs.value = res
    logPage.value = page
    logTotal.value = res.length < logSize ? (page - 1) * logSize + res.length : page * logSize + 1
  } catch { /* ignore */ }
}

function refreshAll() {
  loading.value = true
  Promise.all([fetchMonitor(), fetchLogs(1)]).finally(() => { loading.value = false })
}

let refreshTimer = null

onMounted(() => {
  refreshAll()
  refreshTimer = setInterval(() => {
    if (!loading.value) fetchMonitor()
  }, 10000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.monitor-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
}

.page-title {
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--color-text-primary);
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.refresh-btn:hover:not(:disabled) {
  border-color: var(--color-accent);
  color: var(--color-accent-text);
}
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.spinning { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.monitor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.monitor-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
}
.cpu-icon { background: #fef3c7; color: #d97706; }
.memory-icon { background: #dbeafe; color: #2563eb; }
.thread-icon { background: #d1fae5; color: #059669; }
.info-icon { background: #ede9fe; color: #7c3aed; }

.card-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-primary);
}

.card-body {
  padding: var(--space-4) var(--space-5);
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-1) 0;
}

.metric-label {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.metric-value {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}
.metric-value.text-sm { font-size: var(--text-xs); }

.progress-bar {
  height: 6px;
  background: var(--color-bg-tertiary);
  border-radius: 3px;
  margin: var(--space-3) 0 var(--space-1);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}
.cpu-fill { background: #f59e0b; }
.memory-fill { background: #3b82f6; }

.progress-text {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.section-title {
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--color-text-primary);
}

.filter-select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-size: var(--text-sm);
  cursor: pointer;
}

.log-table-wrap {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.log-table {
  width: 100%;
  border-collapse: collapse;
}

.log-table th {
  padding: var(--space-3) var(--space-4);
  text-align: left;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-light);
}

.log-table td {
  padding: var(--space-3) var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border-light);
}

.log-table tr:last-child td { border-bottom: none; }
.log-table tr:hover td { background: var(--color-bg-hover); }

.cell-time {
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.cell-mono {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}

.cell-detail {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-empty {
  text-align: center;
  color: var(--color-text-tertiary);
  padding: var(--space-8) !important;
}

.action-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 500;
}
.action-register { background: #dbeafe; color: #1d4ed8; }
.action-upload { background: #fef3c7; color: #b45309; }
.action-submit { background: #d1fae5; color: #047857; }
.action-view_report { background: #ede9fe; color: #6d28d9; }
.action-retry { background: #fee2e2; color: #b91c1c; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.page-btn {
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
  color: var(--color-text-secondary);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.page-btn:hover:not(:disabled) { border-color: var(--color-accent); color: var(--color-accent-text); }
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.page-info {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}
</style>
