<template>
  <div class="profile-page">
    <div class="page-header">
      <h2 class="page-title">个人中心</h2>
    </div>

    <div class="profile-grid">
      <div class="profile-card">
        <div class="card-header">
          <span class="card-title">基本信息</span>
        </div>
        <div class="card-body">
          <div v-if="loading" class="loading-state">
            <div v-for="i in 3" :key="i" class="skeleton" style="height: 40px; border-radius: var(--radius-md);" />
          </div>
          <template v-else>
            <div class="info-row">
              <span class="info-label">用户名</span>
              <div class="info-value-row">
                <input
                  v-if="editingUsername"
                  v-model="usernameForm"
                  class="info-input"
                  maxlength="50"
                  @keydown.enter="saveUsername"
                />
                <span v-else class="info-value">{{ profile.username }}</span>
                <button v-if="!editingUsername" class="edit-btn" @click="startEditUsername">编辑</button>
                <template v-else>
                  <button class="save-btn" @click="saveUsername">保存</button>
                  <button class="cancel-btn" @click="editingUsername = false">取消</button>
                </template>
              </div>
            </div>
            <div class="info-row">
              <span class="info-label">角色</span>
              <span class="info-value">
                <span class="role-badge" :class="'role-' + (profile.role || 'USER').toLowerCase()">
                  {{ profile.role === 'ADMIN' ? '管理员' : '普通用户' }}
                </span>
              </span>
            </div>
            <div class="info-row">
              <span class="info-label">积分</span>
              <span class="info-value quota-value">{{ profile.reviewQuota }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">注册时间</span>
              <span class="info-value">{{ profile.createdAt || '-' }}</span>
            </div>
          </template>
        </div>
      </div>

      <div class="profile-card">
        <div class="card-header">
          <span class="card-title">修改密码</span>
        </div>
        <div class="card-body">
          <div class="form-group">
            <label class="form-label">旧密码</label>
            <input v-model="passwordForm.oldPassword" type="password" class="form-input" placeholder="请输入旧密码" />
          </div>
          <div class="form-group">
            <label class="form-label">新密码</label>
            <input v-model="passwordForm.newPassword" type="password" class="form-input" placeholder="请输入新密码（6-128位）" />
          </div>
          <div class="form-group">
            <label class="form-label">确认密码</label>
            <input v-model="passwordForm.confirmPassword" type="password" class="form-input" placeholder="请再次输入新密码" />
          </div>
          <button class="btn btn-primary" :disabled="changingPassword" @click="handleChangePassword">
            {{ changingPassword ? '修改中...' : '修改密码' }}
          </button>
        </div>
      </div>

      <div class="profile-card full-width">
        <div class="card-header">
          <span class="card-title">自定义 API 配置</span>
          <span class="card-desc">配置后将使用您自己的 LLM API 进行审查</span>
        </div>
        <div class="card-body">
          <div class="form-row">
            <div class="form-group flex-1">
              <label class="form-label">API 地址</label>
              <input v-model="apiForm.apiUrl" class="form-input" placeholder="https://api.openai.com/v1" />
            </div>
            <div class="form-group flex-1">
              <label class="form-label">API Key</label>
              <input v-model="apiForm.apiKey" type="password" class="form-input" :placeholder="profile.hasCustomApiKey ? '已配置（留空不修改）' : 'sk-...'" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">模型名称</label>
            <input v-model="apiForm.model" class="form-input" placeholder="gpt-4o / big-pickle 等" />
          </div>
          <button class="btn btn-primary" :disabled="savingApi" @click="handleSaveApi">
            {{ savingApi ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile, changePassword, updateApiConfig } from '@/api/user'

const loading = ref(true)
const profile = ref({})
const editingUsername = ref(false)
const usernameForm = ref('')
const changingPassword = ref(false)
const savingApi = ref(false)

const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const apiForm = ref({ apiUrl: '', apiKey: '', model: '' })

async function fetchProfile() {
  loading.value = true
  try {
    const res = await getProfile()
    profile.value = res
    apiForm.value = {
      apiUrl: res.customApiUrl || '',
      apiKey: '',
      model: res.customModel || ''
    }
  } catch {
    ElMessage.error('获取个人信息失败')
  } finally {
    loading.value = false
  }
}

function startEditUsername() {
  usernameForm.value = profile.value.username
  editingUsername.value = true
}

async function saveUsername() {
  if (!usernameForm.value.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }
  try {
    await updateProfile({ username: usernameForm.value.trim() })
    profile.value.username = usernameForm.value.trim()
    localStorage.setItem('username', usernameForm.value.trim())
    editingUsername.value = false
    ElMessage.success('用户名已更新')
  } catch (e) {
    ElMessage.error(e?.message || '更新失败')
  }
}

async function handleChangePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  changingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功')
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    changingPassword.value = false
  }
}

async function handleSaveApi() {
  savingApi.value = true
  try {
    const data = {}
    if (apiForm.value.apiUrl) data.apiUrl = apiForm.value.apiUrl
    if (apiForm.value.apiKey) data.apiKey = apiForm.value.apiKey
    if (apiForm.value.model) data.model = apiForm.value.model
    await updateApiConfig(data)
    profile.value.customApiUrl = apiForm.value.apiUrl || profile.value.customApiUrl
    profile.value.hasCustomApiKey = apiForm.value.apiKey ? true : profile.value.hasCustomApiKey
    profile.value.customModel = apiForm.value.model || profile.value.customModel
    apiForm.value.apiKey = ''
    ElMessage.success('API 配置已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingApi.value = false
  }
}

onMounted(fetchProfile)
</script>

<style scoped>
.profile-page {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: var(--space-6);
}
.page-title {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
}

.profile-card {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.profile-card.full-width {
  grid-column: 1 / -1;
}

.card-header {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.card-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-primary);
}
.card-desc {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.card-body {
  padding: var(--space-5);
}

.info-row {
  display: flex;
  align-items: center;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-border-light);
}
.info-row:last-child {
  border-bottom: none;
}
.info-label {
  width: 100px;
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}
.info-value-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.info-value {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--color-text-primary);
}
.info-input {
  flex: 1;
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  outline: none;
}
.info-input:focus {
  border-color: var(--color-accent);
}

.role-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
}
.role-admin {
  background: var(--color-risk-high-bg);
  color: var(--color-risk-high);
}
.role-user {
  background: var(--color-accent-light);
  color: var(--color-accent-text);
}

.quota-value {
  font-weight: 600;
  color: var(--color-accent-text);
}

.edit-btn, .save-btn, .cancel-btn {
  border: none;
  background: none;
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  font-family: var(--font-ui);
}
.edit-btn { color: var(--color-accent-text); }
.edit-btn:hover { background: var(--color-accent-light); }
.save-btn { color: var(--color-accent-text); }
.save-btn:hover { background: var(--color-accent-light); }
.cancel-btn { color: var(--color-text-tertiary); }
.cancel-btn:hover { background: var(--color-bg-hover); }

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

.form-row {
  display: flex;
  gap: var(--space-4);
}
.flex-1 { flex: 1; }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

.loading-state {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

@media (max-width: 767px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
  .form-row {
    flex-direction: column;
  }
}
</style>
