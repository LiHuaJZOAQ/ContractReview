<template>
  <div class="report-page" v-loading="loading">
    <el-card v-if="!loading">
      <template #header>
        <span>审查报告 #{{ $route.params.taskId }}</span>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="审查报告" name="report">
          <el-alert :title="report?.summary" type="info" :closable="false" style="margin-bottom:16px" />

          <div class="risk-count">
            <el-tag type="danger" size="large">高危 {{ report?.riskCount?.high || 0 }}</el-tag>
            <el-tag type="warning" size="large" style="margin-left:8px">中危 {{ report?.riskCount?.medium || 0 }}</el-tag>
            <el-tag type="info" size="large" style="margin-left:8px">低危 {{ report?.riskCount?.low || 0 }}</el-tag>
          </div>

          <el-divider />

          <div v-for="(item, idx) in report?.risks" :key="idx" class="risk-item">
            <el-card shadow="hover">
              <div class="risk-header">
                <span class="clause-index">条款 {{ item.clauseIndex }}</span>
                <el-tag :type="riskTagType(item.riskLevel)" size="small">{{ item.riskLevel }}</el-tag>
                <el-tag type="" size="small" effect="plain">{{ item.riskType }}</el-tag>
              </div>
              <p class="clause-content">{{ item.clauseContent }}</p>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="风险描述">{{ item.description }}</el-descriptions-item>
                <el-descriptions-item label="修改建议">{{ item.suggestion }}</el-descriptions-item>
                <el-descriptions-item label="关联法条">
                  <span v-for="(law, li) in item.relatedLaws" :key="li">
                    <el-popover trigger="hover" placement="top" :width="400">
                      <template #reference>
                        <el-tag size="small" effect="plain" style="margin:2px; cursor:pointer">
                          {{ law.split('：')[0] }}
                        </el-tag>
                      </template>
                      <div style="font-size:13px; line-height:1.6">{{ law }}</div>
                    </el-popover>
                  </span>
                  <span v-if="!item.relatedLaws?.length">无</span>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
            <el-divider v-if="idx < report.risks.length - 1" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="合同原文" name="text">
          <div v-if="previewText" class="text-content">
            <pre>{{ previewText }}</pre>
          </div>
          <el-empty v-else description="暂无原文" />
        </el-tab-pane>

        <el-tab-pane label="审查过程" name="logs">
          <div v-if="logs.length" class="log-list">
            <div v-for="(log, idx) in logs" :key="idx" class="log-item">
              <div class="log-header">
                <span class="log-agent">▶ {{ log.agent }}</span>
                <span class="log-time">{{ log.createdAt }}</span>
              </div>
              <pre class="log-content">{{ log.content }}</pre>
            </div>
          </div>
          <el-empty v-else description="暂无审查过程记录" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
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

function riskTagType(level) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
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
  max-width: 900px;
  margin: 0 auto;
}
.risk-count {
  margin: 16px 0;
}
.risk-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.clause-index {
  font-weight: bold;
  font-size: 14px;
}
.clause-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  margin: 8px 0;
  font-size: 13px;
}
.text-content pre {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 600px;
  overflow-y: auto;
}
.log-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.log-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fafafa;
}
.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}
.log-agent {
  font-weight: 600;
  color: #409eff;
  font-size: 13px;
}
.log-time {
  font-size: 12px;
  color: #909399;
}
.log-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Microsoft YaHei', sans-serif;
}
</style>
