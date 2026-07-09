<template>
  <div class="upload-page">
    <el-card>
      <template #header>
        <span>上传合同文件</span>
      </template>

      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".pdf,.doc,.docx"
        :on-change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 PDF、Word 格式，文件不超过 20MB</div>
        </template>
      </el-upload>

      <el-switch
        v-model="desensitize"
        active-text="启用脱敏"
        inactive-text="关闭脱敏"
        style="margin: 16px 0"
      />

      <div v-if="previewText" class="preview-section">
        <el-divider />
        <h3>文本预览</h3>
        <el-input v-model="previewText" type="textarea" :rows="8" readonly />
        <el-button type="primary" :loading="submitting" style="margin-top:16px" @click="handleSubmit">提交审查</el-button>
      </div>
    </el-card>

    <el-dialog v-model="progressVisible" title="审查进度" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <div style="text-align:center;padding:20px">
        <el-progress type="circle" :percentage="progress" :status="progressStatus" />
        <p style="margin-top:16px">{{ progressText }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadFile, submitTask } from '@/api/contract'

const router = useRouter()
const uploadRef = ref(null)
const desensitize = ref(true)
const previewText = ref('')
const currentTaskId = ref(null)
const selectedFile = ref(null)
const submitting = ref(false)

const progressVisible = ref(false)
const progress = ref(0)
const progressStatus = ref('')
const progressText = ref('')

function handleFileChange(file) {
  selectedFile.value = file.raw
  previewText.value = ''
  currentTaskId.value = null
}

async function handleSubmit() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  submitting.value = true
  try {
    const res = await uploadFile(selectedFile.value, desensitize.value)
    previewText.value = res.previewText
    currentTaskId.value = res.taskId

    await submitTask(res.taskId)
    ElMessage.success('提交成功，开始审查')

    progressVisible.value = true
    startSSE(res.taskId)
  } catch {
    submitting.value = false
  } finally {
    submitting.value = false
  }
}

function startSSE(taskId) {
  const token = localStorage.getItem('token')
  const source = new EventSource(`/api/v1/contract/${taskId}/progress?token=${token}`)

  source.addEventListener('progress', e => {
    const data = JSON.parse(e.data)
    progress.value = data.progress
    progressText.value = data.message || '处理中...'
    progressStatus.value = ''
  })

  source.addEventListener('complete', e => {
    progress.value = 100
    progressText.value = '审查完成'
    progressStatus.value = 'success'
    source.close()
    setTimeout(() => {
      progressVisible.value = false
      router.push(`/report/${taskId}`)
    }, 1500)
  })

  source.addEventListener('error', e => {
    const data = JSON.parse(e.data)
    progressStatus.value = 'exception'
    progressText.value = data.message || '审查失败'
    source.close()
  })

  source.onerror = () => {
    source.close()
  }
}
</script>

<style scoped>
.upload-page {
  max-width: 800px;
  margin: 0 auto;
}
.preview-section {
  margin-top: 16px;
}
</style>
