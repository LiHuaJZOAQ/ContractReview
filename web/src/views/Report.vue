<template>
  <div class="report-page">
    <div v-if="loading" class="loading-state">
      <div class="skeleton" style="height: 200px; border-radius: var(--radius-lg);" />
    </div>

    <template v-else-if="report">
      <div class="report-header">
        <h2 class="report-title">审查报告</h2>
        <span class="report-id">#{{ $route.params.taskId }}</span>
      </div>

      <div class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-btn"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="activeTab === 'report'" class="tab-content">
        <div v-if="report.summary" class="summary-block">
          <p class="summary-text">{{ report.summary }}</p>
        </div>

        <div class="risk-stats">
          <div class="stat-card stat-high">
            <span class="stat-number">{{ report.riskCount?.high || 0 }}</span>
            <span class="stat-label">高危</span>
          </div>
          <div class="stat-card stat-medium">
            <span class="stat-number">{{ report.riskCount?.medium || 0 }}</span>
            <span class="stat-label">中危</span>
          </div>
          <div class="stat-card stat-low">
            <span class="stat-number">{{ report.riskCount?.low || 0 }}</span>
            <span class="stat-label">低危</span>
          </div>
        </div>

        <div v-if="report.risks?.length" class="risks-section">
          <div class="section-title">风险详情</div>
          <div v-for="(item, idx) in report.risks" :key="idx" class="risk-card">
            <div class="risk-top">
              <span class="risk-clause">条款 {{ item.clauseIndex }}</span>
              <span class="risk-badge" :class="'badge-' + item.riskLevel.toLowerCase()">
                {{ riskLabel(item.riskLevel) }}
              </span>
              <span class="risk-type-tag">{{ item.riskType }}</span>
            </div>

            <blockquote v-if="item.clauseContent" class="clause-quote">
              {{ item.clauseContent }}
            </blockquote>

            <div class="risk-section">
              <div class="risk-section-label">风险描述</div>
              <p class="risk-section-text">{{ item.description }}</p>
            </div>

            <div class="risk-section">
              <div class="risk-section-label">修改建议</div>
              <p class="risk-section-text">{{ item.suggestion }}</p>
            </div>

            <div v-if="item.relatedLaws?.length" class="risk-section">
              <div class="risk-section-label">关联法条</div>
              <div class="law-tags">
                <el-popover
                  v-for="(law, li) in item.relatedLaws"
                  :key="li"
                  trigger="hover"
                  placement="top"
                  :width="400"
                >
                  <template #reference>
                    <span class="law-tag">{{ law.split('：')[0] }}</span>
                  </template>
                  <div class="law-popover">{{ law }}</div>
                </el-popover>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <p>未发现风险项</p>
        </div>
      </div>

      <div v-if="activeTab === 'text'" class="tab-content">
        <div v-if="previewText" class="text-block">
          <pre class="text-content">{{ previewText }}</pre>
        </div>
        <div v-else class="empty-state">
          <p>暂无原文</p>
        </div>
      </div>

      <div v-if="activeTab === 'logs'" class="tab-content">
        <div v-if="logs.length" class="logs-list">
          <div v-for="(log, idx) in logs" :key="idx" class="log-card">
            <div class="log-header">
              <span class="log-agent">{{ log.agent }}</span>
              <span class="log-time">{{ log.createdAt }}</span>
            </div>
            <pre class="log-content">{{ log.content }}</pre>
          </div>
        </div>
        <div v-else class="empty-state">
          <p>暂无审查过程记录</p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getReport, getPreviewText, getProcessLogs } from '@/api/contract'

const route = useRoute()
const activeTab = ref('report')
const report = ref(null)
const previewText = ref('')
const logs = ref([])
const loading = ref(true)

const tabs = [
  { key: 'report', label: '审查报告' },
  { key: 'text', label: '合同原文' },
  { key: 'logs', label: '审查过程' },
]

function riskLabel(level) {
  const map = { HIGH: '高危', MEDIUM: '中危', LOW: '低危' }
  return map[level] || level
}

onMounted(async () => {
  try {
    const taskId = route.params.taskId
    report.value = await getReport(taskId)
    getPreviewText(taskId).then(t => { previewText.value = t }).catch(() => {})
    getProcessLogs(taskId).then(l => { logs.value = l }).catch(() => {})
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.report-page {
  max-width: 860px;
  margin: 0 auto;
}

.loading-state {
  padding: var(--space-8);
}

.report-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}
.report-title {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}
.report-id {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
}

.tabs {
  display: flex;
  gap: var(--space-1);
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: var(--space-6);
}
.tab-btn {
  padding: var(--space-3) var(--space-4);
  border: none;
  background: none;
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  position: relative;
  transition: color var(--transition-fast);
  font-family: var(--font-family);
}
.tab-btn:hover {
  color: var(--color-text-primary);
}
.tab-btn.active {
  color: var(--color-text-primary);
}
.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--color-accent);
  border-radius: 1px;
}

.tab-content {
  animation: fade-in var(--transition-base) ease-out;
}

.summary-block {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  padding: var(--space-5);
  margin-bottom: var(--space-6);
}
.summary-text {
  font-size: var(--text-md);
  line-height: var(--leading-relaxed);
  color: var(--color-text-primary);
  margin: 0;
}

.risk-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}
.stat-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  padding: var(--space-5);
  text-align: center;
}
.stat-number {
  display: block;
  font-size: var(--text-3xl);
  font-weight: 700;
  line-height: 1;
  margin-bottom: var(--space-2);
}
.stat-label {
  font-size: var(--text-sm);
  font-weight: 500;
}
.stat-high .stat-number { color: var(--color-risk-high); }
.stat-high .stat-label { color: var(--color-risk-high); }
.stat-medium .stat-number { color: var(--color-risk-medium); }
.stat-medium .stat-label { color: var(--color-risk-medium); }
.stat-low .stat-number { color: var(--color-risk-low); }
.stat-low .stat-label { color: var(--color-risk-low); }

.section-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--space-4);
}

.risk-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  margin-bottom: var(--space-4);
  transition: box-shadow var(--transition-base);
}
.risk-card:hover {
  box-shadow: var(--shadow-sm);
}

.risk-top {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.risk-clause {
  font-weight: 600;
  font-size: var(--text-md);
  color: var(--color-text-primary);
}
.risk-badge {
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
}
.badge-high {
  background: var(--color-risk-high-bg);
  color: var(--color-risk-high);
}
.badge-medium {
  background: var(--color-risk-medium-bg);
  color: var(--color-risk-medium);
}
.badge-low {
  background: var(--color-risk-low-bg);
  color: var(--color-risk-low);
}
.risk-type-tag {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  background: var(--color-bg-tertiary);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.clause-quote {
  margin: 0 0 var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-tertiary);
  border-left: 3px solid var(--color-accent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.risk-section {
  margin-bottom: var(--space-3);
}
.risk-section:last-child {
  margin-bottom: 0;
}
.risk-section-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin-bottom: var(--space-1);
}
.risk-section-text {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text-primary);
  margin: 0;
}

.law-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
.law-tag {
  display: inline-block;
  padding: 2px 10px;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.law-tag:hover {
  background: var(--color-accent-light);
  color: var(--color-accent-text);
}
.law-popover {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text-primary);
}

.text-block {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  padding: var(--space-5);
}
.text-content {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 600px;
  overflow-y: auto;
  color: var(--color-text-primary);
  margin: 0;
}

.logs-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.log-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-light);
}
.log-agent {
  font-weight: 600;
  font-size: var(--text-sm);
  color: var(--color-accent-text);
}
.log-time {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
}
.log-content {
  margin: 0;
  padding: var(--space-4);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-mono);
}

.empty-state {
  text-align: center;
  padding: var(--space-16) var(--space-6);
  color: var(--color-text-tertiary);
  font-size: var(--text-base);
}

@media (max-width: 767px) {
  .risk-stats {
    grid-template-columns: 1fr;
    gap: var(--space-3);
  }
  .risk-card {
    padding: var(--space-4);
  }
  .tabs {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .report-header {
    flex-direction: column;
    gap: var(--space-1);
  }
}
</style>
