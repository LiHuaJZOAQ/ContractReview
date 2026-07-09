<template>
  <div class="report-page" v-loading="loading">
    <el-card v-if="report">
      <template #header>
        <span>审查报告 #{{ $route.params.taskId }}</span>
      </template>

      <el-alert :title="report.summary" type="info" :closable="false" style="margin-bottom:16px" />

      <div class="risk-count">
        <el-tag type="danger" size="large">高危 {{ report.riskCount.high }}</el-tag>
        <el-tag type="warning" size="large" style="margin-left:8px">中危 {{ report.riskCount.medium }}</el-tag>
        <el-tag type="info" size="large" style="margin-left:8px">低危 {{ report.riskCount.low }}</el-tag>
      </div>

      <el-divider />

      <div v-for="(item, idx) in report.risks" :key="idx" class="risk-item">
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
                <el-tag size="small" effect="plain" style="margin:2px">{{ law }}</el-tag>
              </span>
              <span v-if="!item.relatedLaws?.length">无</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-divider v-if="idx < report.risks.length - 1" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getReport } from '@/api/contract'

const route = useRoute()
const report = ref(null)
const loading = ref(true)

function riskTagType(level) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
}

onMounted(async () => {
  try {
    report.value = await getReport(route.params.taskId)
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
</style>
