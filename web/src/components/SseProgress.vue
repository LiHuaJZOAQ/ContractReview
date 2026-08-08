<template>
  <div v-if="visible" class="sse-panel">
    <div class="sse-header">
      <span class="sse-title">审查进度</span>
      <span v-if="timerRunning" class="sse-timer">{{ elapsedStr }}</span>
    </div>

    <div class="progress-track">
      <div class="progress-fill" :style="{ width: percentage + '%' }" :class="{ complete: percentage >= 100 }" />
    </div>

    <div class="timeline">
      <div
        v-for="(stage, idx) in stages"
        :key="stage.key"
        class="timeline-item"
        :class="stage.status"
      >
        <div class="timeline-marker">
          <svg v-if="stage.status === 'done'" class="marker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20,6 9,17 4,12"/>
          </svg>
          <svg v-else-if="stage.status === 'error'" class="marker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
          <span v-else class="marker-dot" />
        </div>
        <div class="timeline-content">
          <span class="timeline-label">{{ stage.label }}</span>
          <span v-if="stage.detail" class="timeline-detail">{{ stage.detail }}</span>
        </div>
        <div v-if="idx < stages.length - 1" class="timeline-line" :class="stage.status" />
      </div>
    </div>

    <div v-if="outputs.length" class="output-area">
      <div class="output-header">
        <span class="output-title">中间结果</span>
        <span class="output-count">{{ outputs.length }} 条</span>
      </div>
      <div class="output-body" ref="outputRef">
        <div v-for="(item, i) in outputs" :key="i" class="output-item">
          <span class="output-agent">{{ item.agent }}</span>
          <pre class="output-text">{{ item.content }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  taskId: { type: [Number, String], required: true }
})

const emit = defineEmits(['complete', 'error'])

const visible = ref(false)
const percentage = ref(0)
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
    elapsedStr.value = formatTime(Math.floor((Date.now() - startTime) / 1000))
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

  eventSource.addEventListener('complete', () => {
    percentage.value = 100
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
    const activeStage = stages.value.find(s => s.status === 'active')
    if (activeStage) activeStage.status = 'error'
    stopTimer()
    emit('error', msg)
  })

  eventSource.onerror = () => {
    if (timerRunning.value) {
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
.sse-panel {
  margin-top: var(--space-6);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
}

.sse-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
}
.sse-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}
.sse-timer {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.progress-track {
  height: 4px;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-full);
  overflow: hidden;
  margin-bottom: var(--space-6);
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-accent), #6ee7b7);
  border-radius: var(--radius-full);
  transition: width var(--transition-slow);
}
.progress-fill.complete {
  background: var(--color-accent);
}

.timeline {
  display: flex;
  flex-direction: column;
}

.timeline-item {
  display: flex;
  align-items: flex-start;
  position: relative;
  min-height: 48px;
}

.timeline-marker {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  z-index: 1;
}
.marker-icon {
  width: 18px;
  height: 18px;
}
.marker-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-bg-tertiary);
  border: 2px solid var(--color-border);
  transition: all var(--transition-base);
}

.timeline-item.done .marker-dot {
  background: var(--color-accent);
  border-color: var(--color-accent);
}
.timeline-item.active .marker-dot {
  background: var(--color-accent);
  border-color: var(--color-accent);
  box-shadow: 0 0 0 4px var(--color-accent-light);
  animation: pulse-dot 2s ease-in-out infinite;
}
.timeline-item.error .marker-dot {
  background: var(--color-danger);
  border-color: var(--color-danger);
}

.timeline-item.done .marker-icon { color: var(--color-accent); }
.timeline-item.error .marker-icon { color: var(--color-danger); }

.timeline-content {
  flex: 1;
  padding: 2px 0 var(--space-2) var(--space-3);
  min-width: 0;
}
.timeline-label {
  display: block;
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-text-tertiary);
  transition: color var(--transition-fast);
}
.timeline-item.done .timeline-label { color: var(--color-text-secondary); }
.timeline-item.active .timeline-label { color: var(--color-text-primary); font-weight: 600; }
.timeline-item.error .timeline-label { color: var(--color-danger); }

.timeline-detail {
  display: block;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.timeline-line {
  position: absolute;
  left: 11px;
  top: 24px;
  bottom: -24px;
  width: 2px;
  background: var(--color-border-light);
  transition: background var(--transition-fast);
}
.timeline-line.done { background: var(--color-accent); }
.timeline-line.active { background: linear-gradient(to bottom, var(--color-accent), var(--color-border-light)); }

.output-area {
  margin-top: var(--space-5);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.output-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-light);
}
.output-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}
.output-count {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
.output-body {
  padding: var(--space-3) var(--space-4);
  max-height: 280px;
  overflow-y: auto;
  background: var(--color-bg-tertiary);
}
.output-item {
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-3);
  border-bottom: 1px dashed var(--color-border-light);
}
.output-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.output-agent {
  display: inline-block;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-accent-text);
  margin-bottom: var(--space-1);
}
.output-text {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-mono);
}
</style>
