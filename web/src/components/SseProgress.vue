<template>
  <div v-if="visible" class="sse-progress">
    <div class="progress-header">
      <span class="progress-title">审查进度</span>
      <span v-if="timerRunning" class="timer">⏱ {{ elapsedStr }}</span>
    </div>

    <el-progress :percentage="percentage" :status="progressStatus" :stroke-width="16" />

    <div class="stage-list">
      <div v-for="(stage, idx) in stages" :key="stage.key" class="stage-item" :class="stage.status">
        <span v-if="stage.status === 'done'" class="stage-icon done-icon">✓</span>
        <span v-else-if="stage.status === 'active'" class="stage-icon active-icon">◉</span>
        <span v-else-if="stage.status === 'error'" class="stage-icon error-icon">✕</span>
        <span v-else class="stage-icon pending-icon">○</span>
        <span class="stage-label">{{ stage.label }}</span>
        <span v-if="stage.detail" class="stage-detail">{{ stage.detail }}</span>
        <span v-if="idx < stages.length" class="stage-line" :class="stage.status"></span>
      </div>
    </div>

    <div v-if="outputs.length" class="output-area">
      <div class="output-title">
        审查中间结果
        <span class="output-count">{{ outputs.length }} 条</span>
      </div>
      <div class="output-content" ref="outputRef">
        <div v-for="(item, i) in outputs" :key="i" class="output-line">
          <div class="output-agent">▶ {{ item.agent }}</div>
          <pre class="output-text">{{ item.content }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  taskId: { type: [Number, String], required: true }
})

const emit = defineEmits(['complete', 'error'])

const visible = ref(false)
const percentage = ref(0)
const progressStatus = ref('')
const outputs = ref([])
const outputRef = ref(null)

const elapsedStr = ref('00:00')
const timerRunning = ref(false)
let timerInterval = null
let startTime = null

const stages = ref([
  { key: 'parsing', label: '解析文档', status: 'pending', detail: '' },
  { key: 'retrieving', label: '检索法条', status: 'pending', detail: '' },
  { key: 'reviewing', label: '审查条款', status: 'pending', detail: '' },
  { key: 'summarizing', label: '汇总报告', status: 'pending', detail: '' },
])

let eventSource = null

function formatTime(sec) {
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${m}:${s}`
}

function startTimer() {
  startTime = Date.now()
  timerRunning.value = true
  timerInterval = setInterval(() => {
    const elapsed = Math.floor((Date.now() - startTime) / 1000)
    elapsedStr.value = formatTime(elapsed)
  }, 1000)
}

function stopTimer() {
  timerRunning.value = false
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
  if (startTime) {
    elapsedStr.value = formatTime(Math.floor((Date.now() - startTime) / 1000)) + ' ✓'
  }
}

function reset() {
  visible.value = false
  stopTimer()
  stages.value.forEach(s => {
    s.status = 'pending'
    s.detail = ''
  })
  outputs.value = []
  percentage.value = 0
  progressStatus.value = ''
  elapsedStr.value = '00:00'
}

function open() {
  reset()
  visible.value = true
  startTimer()

  const token = localStorage.getItem('token')
  eventSource = new EventSource(`/api/v1/contract/${props.taskId}/progress?token=${token}`)

  eventSource.addEventListener('progress', e => {
    const data = JSON.parse(e.data)
    percentage.value = data.progress

    const status = (data.status || '').toLowerCase()
    const stageKeys = ['parsing', 'retrieving', 'reviewing', 'summarizing']
    const idx = stageKeys.indexOf(status)

    stages.value.forEach((s, i) => {
      if (i < idx) s.status = 'done'
      else if (i === idx) {
        s.status = 'active'
        if (data.message) s.detail = data.message
      }
    })
  })

  eventSource.addEventListener('llm_output', e => {
    const data = JSON.parse(e.data)
    outputs.value.push({ agent: data.agent, content: data.content })
    nextTick(() => {
      if (outputRef.value) {
        outputRef.value.scrollTop = outputRef.value.scrollHeight
      }
    })
  })

  eventSource.addEventListener('complete', e => {
    percentage.value = 100
    progressStatus.value = 'success'
    stages.value.forEach(s => { s.status = 'done' })
    stopTimer()
    emit('complete')
  })

  eventSource.addEventListener('error', e => {
    let msg = '审查失败'
    try {
      const data = JSON.parse(e.data)
      msg = data.message || msg
    } catch {}
    progressStatus.value = 'exception'
    const activeStage = stages.value.find(s => s.status === 'active')
    if (activeStage) activeStage.status = 'error'
    stopTimer()
    emit('error', msg)
  })

  eventSource.onerror = () => {
    if (progressStatus.value === '') {
      progressStatus.value = 'exception'
      stopTimer()
      emit('error', '连接断开')
    }
  }
}

function close() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  stopTimer()
}

watch(() => props.taskId, () => { close() })

onBeforeUnmount(close)

defineExpose({ open, close })
</script>

<style scoped>
.sse-progress {
  margin-top: 20px;
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}
.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.progress-title {
  font-weight: 600;
  font-size: 15px;
}
.timer {
  font-size: 13px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}
.stage-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
}
.stage-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #c0c4cc;
  position: relative;
}
.stage-item.done {
  color: #67c23a;
}
.stage-item.active {
  color: #409eff;
}
.stage-item.error {
  color: #f56c6c;
}
.stage-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}
.done-icon {
  color: #67c23a;
}
.active-icon {
  color: #409eff;
  animation: pulse 1.2s ease-in-out infinite;
}
.error-icon {
  color: #f56c6c;
}
.pending-icon {
  color: #c0c4cc;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.stage-label {
  flex-shrink: 0;
}
.stage-detail {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}
.output-area {
  margin-top: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
}
.output-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.output-count {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.output-content {
  padding: 8px 12px;
  max-height: 300px;
  overflow-y: auto;
  font-size: 13px;
}
.output-line {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #eee;
}
.output-line:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.output-agent {
  font-weight: 600;
  color: #409eff;
  margin-bottom: 4px;
}
.output-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Microsoft YaHei', sans-serif;
}
</style>
