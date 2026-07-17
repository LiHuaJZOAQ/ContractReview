<template>
  <div class="upload-page">
    <div class="upload-card">
      <h2 class="card-title">上传合同文件</h2>

      <div
        class="upload-area"
        :class="{ 'has-file': selectedFile, dragover }"
        @dragover.prevent="dragover = true"
        @dragleave="dragover = false"
        @drop.prevent="handleDrop"
        @click="$refs.fileInput.click()"
      >
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.doc,.docx"
          class="file-input"
          @change="handleFileInput"
        />
        <div v-if="!selectedFile" class="upload-placeholder">
          <svg class="upload-icon" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M24 32V16M16 22l8-8 8 8"/>
            <path d="M40 32v4a4 4 0 01-4 4H12a4 4 0 01-4-4v-4"/>
          </svg>
          <p class="upload-text">拖拽文件到此处，或 <span class="upload-link">点击选择</span></p>
          <p class="upload-hint">支持 PDF、Word 格式，文件不超过 20MB</p>
        </div>
        <div v-else class="file-info">
          <svg class="file-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14,2 14,8 20,8"/>
          </svg>
          <div class="file-meta">
            <span class="file-name">{{ selectedFile.name }}</span>
            <span class="file-size">{{ formatSize(selectedFile.size) }}</span>
          </div>
          <button class="file-remove" @click.stop="clearFile">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
      </div>

      <div class="option-row">
        <label class="toggle-label">
          <span class="toggle-track" :class="{ checked: desensitize }" @click="desensitize = !desensitize">
            <span class="toggle-thumb" />
          </span>
          <span class="toggle-text">启用脱敏</span>
        </label>
      </div>

      <button
        v-if="selectedFile && !previewText"
        class="btn btn-primary btn-full"
        :disabled="uploading"
        @click="handleUpload"
      >
        <span v-if="uploading" class="spinner" />
        {{ uploading ? '上传中...' : '上传预览' }}
      </button>

      <div v-if="previewText" class="preview-section">
        <div class="section-label">文本预览</div>
        <div class="preview-block">
          <pre class="preview-text">{{ previewText }}</pre>
        </div>
        <div class="action-row">
          <button class="btn btn-secondary" @click="handleBack">返回</button>
          <button class="btn btn-primary" :disabled="submitting" @click="handleSubmit">
            <span v-if="submitting" class="spinner" />
            {{ submitting ? '提交中...' : '提交审查' }}
          </button>
        </div>
      </div>
    </div>

    <SseProgress ref="sseRef" :task-id="currentTaskId" @complete="onComplete" @error="onError" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadFile, submitTask } from '@/api/contract'
import SseProgress from '@/components/SseProgress.vue'

const router = useRouter()
const sseRef = ref(null)
const desensitize = ref(true)
const previewText = ref('')
const currentTaskId = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const submitting = ref(false)
const dragover = ref(false)

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

function handleDrop(e) {
  dragover.value = false
  const file = e.dataTransfer.files[0]
  if (file) setFile(file)
}

function handleFileInput(e) {
  const file = e.target.files[0]
  if (file) setFile(file)
}

function setFile(file) {
  selectedFile.value = file
  previewText.value = ''
  currentTaskId.value = null
}

function clearFile() {
  selectedFile.value = null
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
  max-width: 720px;
  margin: 0 auto;
}

.upload-card {
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  border: 1px solid var(--color-border-light);
  padding: var(--space-8);
}

.card-title {
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-6);
}

.upload-area {
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-12) var(--space-6);
  text-align: center;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.upload-area:hover,
.upload-area.dragover {
  border-color: var(--color-accent);
  background: var(--color-accent-light);
}
.upload-area.has-file {
  border-style: solid;
  border-color: var(--color-border);
  padding: var(--space-4) var(--space-5);
}

.file-input {
  display: none;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.upload-icon {
  width: 48px;
  height: 48px;
  color: var(--color-text-tertiary);
}

.upload-text {
  font-size: var(--text-base);
  color: var(--color-text-secondary);
  margin: 0;
}
.upload-link {
  color: var(--color-accent-text);
  font-weight: 500;
}
.upload-hint {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  margin: 0;
}

.file-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.file-icon {
  width: 24px;
  height: 24px;
  color: var(--color-accent);
  flex-shrink: 0;
}
.file-meta {
  flex: 1;
  text-align: left;
  min-width: 0;
}
.file-name {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}
.file-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  flex-shrink: 0;
  transition: all var(--transition-fast);
}
.file-remove:hover {
  background: var(--color-bg-hover);
  color: var(--color-danger);
}
.file-remove svg {
  width: 16px;
  height: 16px;
}

.option-row {
  margin-top: var(--space-5);
}

.toggle-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  cursor: pointer;
  user-select: none;
}
.toggle-track {
  position: relative;
  width: 40px;
  height: 22px;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-full);
  transition: background var(--transition-fast);
  border: 1px solid var(--color-border);
}
.toggle-track.checked {
  background: var(--color-accent);
  border-color: var(--color-accent);
}
.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  background: #fff;
  border-radius: 50%;
  transition: transform var(--transition-fast);
  box-shadow: var(--shadow-xs);
}
.toggle-track.checked .toggle-thumb {
  transform: translateX(18px);
}
.toggle-text {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  height: 40px;
  padding: 0 var(--space-5);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-family);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-primary {
  background: var(--color-accent);
  color: #fff;
  box-shadow: 0 1px 2px rgba(16, 185, 129, 0.2);
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-accent-hover);
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.3);
}
.btn-secondary {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}
.btn-secondary:hover:not(:disabled) {
  background: var(--color-bg-hover);
}
.btn-full {
  width: 100%;
  margin-top: var(--space-5);
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.preview-section {
  margin-top: var(--space-6);
}

.section-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--space-3);
}

.preview-block {
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  max-height: 320px;
  overflow-y: auto;
}

.preview-text {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.action-row {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-5);
}
.action-row .btn {
  flex: 1;
}
</style>
