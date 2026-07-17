<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <img src="@/assets/logo.svg" alt="Logo" />
      </div>
      <h1 class="auth-title">智能合同风险审查</h1>
      <p class="auth-subtitle">登录以继续使用</p>

      <el-form ref="formRef" :model="form" :rules="rules" class="auth-form" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="auth-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <p class="auth-link">
        还没有账号？<router-link to="/register">注册</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const formRef = ref(null)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login(form)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--color-bg-secondary);
  padding: var(--space-6);
}

.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--color-bg-primary);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  padding: var(--space-10) var(--space-10) var(--space-8);
}

.auth-logo {
  display: flex;
  justify-content: center;
  margin-bottom: var(--space-5);
}
.auth-logo img {
  width: 40px;
  height: 40px;
  color: var(--color-accent);
}

.auth-title {
  text-align: center;
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.auth-subtitle {
  text-align: center;
  font-size: var(--text-base);
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-8);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.auth-btn {
  width: 100%;
  margin-top: var(--space-2);
  height: 44px;
  font-size: var(--text-md);
}

.auth-link {
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
  margin: var(--space-6) 0 0;
}
.auth-link a {
  font-weight: 500;
}
</style>
