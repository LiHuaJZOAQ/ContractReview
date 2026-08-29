<template>
  <div class="law-page">
    <div class="page-header">
      <h2 class="page-title">法律法规库</h2>
      <button class="btn btn-primary" @click="showCreateDialog">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增法规
      </button>
    </div>

    <div class="filter-bar">
      <input v-model="searchKeyword" class="search-input" placeholder="搜索法规名称..." @input="debounceSearch" />
      <select v-model="filterCategory" class="category-select" @change="fetchLaws">
        <option value="">全部分类</option>
        <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
      </select>
    </div>

    <div class="law-list">
      <div v-if="loading" class="loading-state">
        <div v-for="i in 5" :key="i" class="skeleton" style="height: 80px; border-radius: var(--radius-md);" />
      </div>
      <div v-else-if="laws.length === 0" class="empty-state">
        <svg class="empty-icon" viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
          <polyline points="14,2 14,8 20,8" />
        </svg>
        <p>暂无法律法规</p>
      </div>
      <div v-else class="law-cards">
        <div v-for="law in laws" :key="law.id" class="law-card">
          <div class="law-card-header">
            <div class="law-info">
              <h3 class="law-title">{{ law.title }}</h3>
              <span v-if="law.category" class="law-category">{{ law.category }}</span>
            </div>
            <div class="law-actions">
              <span class="status-badge" :class="law.enabled ? 'status-on' : 'status-off'" @click="handleToggle(law)">
                {{ law.enabled ? '启用' : '禁用' }}
              </span>
              <button class="action-btn" title="编辑" @click="showEditDialog(law)">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              </button>
              <button class="action-btn" title="重新索引" @click="handleReindex(law.id)">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
              </button>
              <button class="action-btn action-danger" title="删除" @click="handleDelete(law.id, law.title)">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
              </button>
            </div>
          </div>
          <p class="law-preview">{{ law.content.substring(0, 200) }}{{ law.content.length > 200 ? '...' : '' }}</p>
          <div class="law-meta">
            <span>{{ law.createdAt }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showDialog" class="dialog-overlay" @click.self="showDialog = false">
      <div class="dialog">
        <div class="dialog-header">
          <span class="dialog-title">{{ editingLaw ? '编辑法规' : '新增法规' }}</span>
          <button class="dialog-close" @click="showDialog = false">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label">法规名称 *</label>
            <input v-model="form.title" class="form-input" placeholder="如：中华人民共和国民法典" />
          </div>
          <div class="form-group">
            <label class="form-label">分类</label>
            <input v-model="form.category" class="form-input" placeholder="如：民法、劳动法、知识产权" />
          </div>
          <div class="form-group">
            <label class="form-label">内容 *</label>
            <textarea v-model="form.content" class="form-textarea" rows="12" placeholder="粘贴法规全文内容..." />
            <div class="textarea-hint">{{ form.content.length }} 字</div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn btn-secondary" @click="showDialog = false">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="handleSave">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listLaws, createLaw, updateLaw, deleteLaw, toggleLaw, reindexLaw } from '@/api/law'

const laws = ref([])
const loading = ref(true)
const showDialog = ref(false)
const saving = ref(false)
const editingLaw = ref(null)
const searchKeyword = ref('')
const filterCategory = ref('')
const categories = ref([])

const form = ref({ title: '', category: '', content: '' })

let searchTimer = null
function debounceSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(fetchLaws, 300)
}

async function fetchLaws() {
  loading.value = true
  try {
    const res = await listLaws(filterCategory.value, searchKeyword.value)
    laws.value = res || []
    const cats = new Set()
    laws.value.forEach(l => { if (l.category) cats.add(l.category) })
    categories.value = [...cats]
  } catch {
    laws.value = []
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingLaw.value = null
  form.value = { title: '', category: '', content: '' }
  showDialog.value = true
}

function showEditDialog(law) {
  editingLaw.value = law
  form.value = { title: law.title, category: law.category || '', content: law.content }
  showDialog.value = true
}

async function handleSave() {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('请填写法规名称和内容')
    return
  }
  saving.value = true
  try {
    if (editingLaw.value) {
      await updateLaw(editingLaw.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await createLaw(form.value)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    await fetchLaws()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(id, title) {
  try {
    await ElMessageBox.confirm(`确定删除「${title}」？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteLaw(id)
    ElMessage.success('已删除')
    await fetchLaws()
  } catch {}
}

async function handleToggle(law) {
  try {
    await toggleLaw(law.id)
    law.enabled = !law.enabled
    ElMessage.success(law.enabled ? '已启用' : '已禁用')
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleReindex(id) {
  try {
    await reindexLaw(id)
    ElMessage.success('重新索引成功')
  } catch (e) {
    ElMessage.error(e?.message || '索引失败')
  }
}

onMounted(fetchLaws)
</script>

<style scoped>
.law-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}
.page-title {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.filter-bar {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}
.search-input {
  flex: 1;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  outline: none;
  font-family: var(--font-ui);
}
.search-input:focus {
  border-color: var(--color-accent);
}
.category-select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  outline: none;
  font-family: var(--font-ui);
}

.law-cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.law-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  padding: var(--space-4) var(--space-5);
  transition: border-color var(--transition-fast);
}
.law-card:hover {
  border-color: var(--color-border);
}

.law-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}
.law-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}
.law-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.law-category {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  background: var(--color-bg-tertiary);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.law-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}

.status-badge {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}
.status-on {
  background: var(--color-accent-light);
  color: var(--color-accent-text);
}
.status-on:hover { opacity: 0.8; }
.status-off {
  background: var(--color-bg-tertiary);
  color: var(--color-text-tertiary);
}
.status-off:hover { opacity: 0.8; }

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.action-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}
.action-btn.action-danger:hover {
  background: var(--color-risk-high-bg);
  color: var(--color-risk-high);
}

.law-preview {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  line-height: var(--leading-relaxed);
  margin: var(--space-2) 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.law-meta {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-16) var(--space-6);
  color: var(--color-text-tertiary);
}
.empty-icon {
  width: 48px;
  height: 48px;
  opacity: 0.4;
}

.loading-state {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* Dialog */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: var(--space-4);
}
.dialog {
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-xl);
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
}
.dialog-title {
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--color-text-primary);
}
.dialog-close {
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
}
.dialog-close:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}
.dialog-body {
  padding: var(--space-5);
  overflow-y: auto;
  flex: 1;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--color-border-light);
}

.form-group {
  margin-bottom: var(--space-4);
}
.form-label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: var(--space-2);
}
.form-input {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  outline: none;
  box-sizing: border-box;
  font-family: var(--font-ui);
}
.form-input:focus {
  border-color: var(--color-accent);
}
.form-textarea {
  width: 100%;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-family: var(--font-mono);
  line-height: var(--leading-relaxed);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  outline: none;
  resize: vertical;
  box-sizing: border-box;
}
.form-textarea:focus {
  border-color: var(--color-accent);
}
.textarea-hint {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
  text-align: right;
  margin-top: var(--space-1);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  height: 38px;
  padding: 0 var(--space-5);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-ui);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-primary {
  background: var(--color-accent);
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-accent-hover);
}
.btn-secondary {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}
.btn-secondary:hover:not(:disabled) {
  background: var(--color-bg-hover);
}

@media (max-width: 767px) {
  .filter-bar {
    flex-direction: column;
  }
  .law-card-header {
    flex-direction: column;
  }
}
</style>
