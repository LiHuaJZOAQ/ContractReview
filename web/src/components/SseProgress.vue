<template>
  <el-dialog v-model="visible" title="审查进度" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
    <div style="text-align:center;padding:20px">
      <el-progress type="circle" :percentage="percentage" :status="status" />
      <p style="margin-top:16px">{{ text }}</p>
      <p style="font-size:12px;color:#999">{{ stageText }}</p>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'

const props = defineProps({
  taskId: { type: [Number, String], required: true }
})

const emit = defineEmits(['complete', 'error'])

const visible = ref(false)
const percentage = ref(0)
const status = ref('')
const text = ref('准备中...')
const stageText = ref('')

let eventSource = null

function open() {
  visible.value = true
  const token = localStorage.getItem('token')
  eventSource = new EventSource(`/api/v1/contract/${props.taskId}/progress?token=${token}`)

  eventSource.addEventListener('progress', e => {
    const data = JSON.parse(e.data)
    percentage.value = data.progress
    stageText.value = data.message || ''
  })

  eventSource.addEventListener('complete', e => {
    percentage.value = 100
    text.value = '审查完成'
    status.value = 'success'
    close()
    emit('complete')
  })

  eventSource.addEventListener('error', e => {
    let msg = '审查失败'
    try {
      const data = JSON.parse(e.data)
      msg = data.message || msg
    } catch {}
    status.value = 'exception'
    text.value = msg
    close()
    emit('error', msg)
  })

  eventSource.onerror = () => {
    close()
    emit('error', '连接断开')
  }
}

function close() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  setTimeout(() => { visible.value = false }, 2000)
}

onUnmounted(close)

defineExpose({ open })
</script>
