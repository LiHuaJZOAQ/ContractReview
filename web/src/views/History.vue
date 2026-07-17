<template>
  <div class="history-page">
    <div class="page-header">
      <h2 class="page-title">审查历史</h2>
    </div>

    <div class="history-card">
      <div class="filter-bar">
        <div class="segment-control">
          <button
            v-for="f in filters"
            :key="f.value"
            class="segment-btn"
            :class="{ active: statusFilter === f.value }"
            @click="switchFilter(f.value)"
          >
            {{ f.label }}
          </button>
        </div>
      </div>

      <div v-if="loading" class="loading-list">
        <div v-for="i in 5" :key="i" class="skeleton" style="height: 56px; border-radius: var(--radius-md);" />
      </div>

      <div v-else-if="tasks.length" class="table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th class="th-id">ID</th>
              <th class="th-file">文件名</th>
              <th class="th-status">状态</th>
              <th class="th-time">创建时间</th>
              <th class="th-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in tasks" :key="row.taskId" class="table-row">
              <td class="td-id">{{ row.taskId }}</td>
              <td class="td-file">
                <span class="file-name-text">{{ row.fileName }}</span>
              </td>
              <td class="td-status">
                <span class="status-badge" :class="'status-' + statusClass(row.status)">
                  <span class="status-dot" />
                  {{ statusLabel(row.status) }}
                </span>
              </td>
              <td class="td-time">{{ row.createdAt }}</td>
              <td class="td-action">
                <button
                  v-if="row.status === 'SUCCESS'"
                  class="action-link"
                  @click="$router.push(`/report/${row.taskId}`)"
                >
                  查看报告
                </button>
                <button
                  v-if="row.status === 'FAILED'"
                  class="action-link action-warning"
                  @click="handleRetry(row)"
                >
                  重试
                </button>
                <button
                  v-else-if="isProcessing(row.status)"
                  class="action-link"
                  @click="$router.push(`/report/${row.taskId}`)"
                >
                  查看进度
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state">
        <svg class="empty-icon" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <rect x="8" y="6" width="32" height="36" rx="3"/>
          <line x1="16" y1="16" x2="32" y2="16"/>
          <line x1="16" y1="24" x2="28" y2="24"/>
          <line x1="16" y1="32" x2="24" y2="32"/>
        </svg>
        <p>暂无审查记录</p>
      </div>

      <div v-if="total > size" class="pagination">
        <button class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="15,18 9,12 15,6"/></svg>
        </button>
        <template v-for="p in pageNumbers" :key="p">
          <span v-if="p === '...'" class="page-ellipsis">...</span>
          <button
            v-else
            class="page-btn"
            :class="{ active: p === page }"
            @click="goPage(p)"
          >
            {{ p }}
          </button>
        </template>
        <button class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="9,6 15,12 9,18"/></svg>
        </button>
        <span class="page-total">共 {{ total }} 条</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistory, retryTask } from '@/api/contract'

const tasks = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const statusFilter = ref('ALL')

const filters = [
  { label: '全部', value: 'ALL' },
  { label: '进行中', value: 'PROCESSING' },
  { label: '已完成', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

const PROCESSING_STATUSES = ['PENDING', 'PARSING', 'RETRIEVING', 'REVIEWING', 'SUMMARIZING']
const STATUS_FILTER_MAP = {
  ALL: 'ALL',
  PROCESSING: PROCESSING_STATUSES.join(','),
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
}

const totalPages = computed(() => Math.ceil(total.value / size.value))

const pageNumbers = computed(() => {
  const tp = totalPages.value
  const cp = page.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const pages = []
  pages.push(1)
  if (cp > 3) pages.push('...')
  for (let i = Math.max(2, cp - 1); i <= Math.min(tp - 1, cp + 1); i++) {
    pages.push(i)
  }
  if (cp < tp - 2) pages.push('...')
  pages.push(tp)
  return pages
})

function isProcessing(status) {
  return PROCESSING_STATUSES.includes(status)
}

function statusLabel(status) {
  const map = {
    PENDING: '待提交', PARSING: '解析中', RETRIEVING: '检索中',
    REVIEWING: '审查中', SUMMARIZING: '汇总中',
    SUCCESS: '已完成', FAILED: '失败',
  }
  return map[status] || status
}

function statusClass(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'pending'
  return 'processing'
}

function switchFilter(val) {
  statusFilter.value = val
  page.value = 1
  fetchHistory()
}

function goPage(p) {
  page.value = p
  fetchHistory()
}

async function fetchHistory() {
  loading.value = true
  try {
    const res = await getHistory(page.value, size.value, STATUS_FILTER_MAP[statusFilter.value])
    tasks.value = res.tasks
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function handleRetry(row) {
  try {
    await retryTask(row.taskId)
    ElMessage.success('已重新提交审查')
    await fetchHistory()
  } catch (e) {
    ElMessage.error(e?.message || '重试失败')
  }
}

onMounted(fetchHistory)
</script>

<style scoped>
.history-page {
  max-width: 1000px;
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

.history-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.filter-bar {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
}

.segment-control {
  display: inline-flex;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  padding: 3px;
}
.segment-btn {
  padding: var(--space-2) var(--space-4);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-ui);
}
.segment-btn:hover {
  color: var(--color-text-primary);
}
.segment-btn.active {
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-xs);
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
  padding: var(--space-4) var(--space-5);
  font-size: var(--text-sm);
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border-light);
}
.table-row {
  transition: background var(--transition-fast);
}
.table-row:hover {
  background: var(--color-bg-hover);
}
.table-row:last-child td {
  border-bottom: none;
}

.th-id, .td-id {
  width: 70px;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
.th-file { min-width: 200px; }
.th-status { width: 120px; }
.th-time { width: 180px; }
.th-action { width: 120px; text-align: right; }

.td-file {
  max-width: 280px;
}
.file-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  white-space: nowrap;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-success {
  background: var(--color-accent-light);
  color: var(--color-accent-text);
}
.status-success .status-dot {
  background: var(--color-accent);
}
.status-danger {
  background: var(--color-risk-high-bg);
  color: var(--color-risk-high);
}
.status-danger .status-dot {
  background: var(--color-risk-high);
}
.status-pending {
  background: var(--color-bg-tertiary);
  color: var(--color-text-tertiary);
}
.status-pending .status-dot {
  background: var(--color-text-tertiary);
}
.status-processing {
  background: var(--color-risk-medium-bg);
  color: var(--color-risk-medium);
}
.status-processing .status-dot {
  background: var(--color-risk-medium);
  animation: pulse-dot 2s ease-in-out infinite;
}

.td-action {
  text-align: right;
}
.action-link {
  border: none;
  background: none;
  color: var(--color-accent-text);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
  font-family: var(--font-ui);
}
.action-link:hover {
  background: var(--color-accent-light);
}
.action-warning {
  color: var(--color-warning);
}
.action-warning:hover {
  background: var(--color-risk-medium-bg);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--color-border-light);
}
.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-ui);
}
.page-btn:hover:not(:disabled):not(.active) {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}
.page-btn.active {
  background: var(--color-accent);
  color: #fff;
}
.page-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.page-btn svg {
  width: 16px;
  height: 16px;
}
.page-ellipsis {
  padding: 0 var(--space-1);
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
}
.page-total {
  margin-left: var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-16) var(--space-6);
  color: var(--color-text-tertiary);
}
.empty-icon {
  width: 48px;
  height: 48px;
  opacity: 0.4;
}

.loading-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
}

@media (max-width: 767px) {
  .data-table {
    font-size: var(--text-sm);
  }
  .data-table th,
  .data-table td {
    padding: var(--space-3) var(--space-3);
  }
  .th-id, .td-id,
  .th-time, .td-time {
    display: none;
  }
  .segment-control {
    width: 100%;
  }
  .segment-btn {
    flex: 1;
    text-align: center;
    padding: var(--space-2) var(--space-2);
    font-size: var(--text-xs);
  }
}
</style>
