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

      <el-button
        v-if="selectedFile"
        type="primary"
        :loading="uploading"
        style="margin-top:16px"
        @click="handleUpload"
      >上传预览</el-button>

      <div v-if="previewText" class="preview-section">
        <el-divider />
        <h3>文本预览</h3>
        <el-input v-model="previewText" type="textarea" :rows="8" readonly />
        <div style="margin-top:16px; display:flex; gap:12px">
          <el-button @click="handleBack">返回</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交审查</el-button>
        </div>
      </div>
    </el-card>

    <SseProgress ref="sseRef" :task-id="currentTaskId" @complete="onComplete" @error="onError" />
    <p v-if="submitting" class="submit-hint">正在提交审查请求，请稍候...</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadFile, submitTask } from '@/api/contract'
import SseProgress from '@/components/SseProgress.vue'

const router = useRouter()
const uploadRef = ref(null)
const sseRef = ref(null)
const desensitize = ref(true)
const previewText = ref('')
const currentTaskId = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const submitting = ref(false)

function handleFileChange(file) {
  selectedFile.value = file.raw
  previewText.value = ''
  currentTaskId.value = null
}

async function handleUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res = await uploadFile(selectedFile.value, desensitize.value)
    previewText.value = res.previewText
    currentTaskId.value = res.taskId
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleSubmit() {
  if (!currentTaskId.value) return
  submitting.value = true
  try {
    await submitTask(currentTaskId.value)
    submitting.value = false
    sseRef.value.open()
  } catch (e) {
    submitting.value = false
    ElMessage.error(e?.response?.data?.message || '提交失败')
  }
}

function handleBack() {
  previewText.value = ''
  currentTaskId.value = null
}

function onComplete() {
  setTimeout(() => router.push(`/report/${currentTaskId.value}`), 1000)
}

function onError(msg) {
  ElMessage.error(msg)
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
.submit-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
}
</style>
