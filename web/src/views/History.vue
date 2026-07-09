<template>
  <div class="history-page">
    <el-card>
      <template #header>
        <span>审查历史</span>
      </template>

      <el-table :data="tasks" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="taskId" label="任务ID" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :status="row.status === 'FAILED' ? 'exception' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUCCESS'" type="primary" link @click="$router.push(`/report/${row.taskId}`)">查看报告</el-button>
            <el-button v-if="row.status === 'FAILED'" type="warning" link @click="handleRetry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top:16px;justify-content:center"
        @current-change="fetchHistory"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistory, retryTask } from '@/api/contract'

const tasks = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'warning'
}

async function fetchHistory() {
  loading.value = true
  try {
    const res = await getHistory(page.value, size.value)
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
  } catch {
    // handled by interceptor
  }
}

onMounted(fetchHistory)
</script>
