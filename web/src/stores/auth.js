import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, refreshToken as apiRefresh } from '@/api/auth'
import { getProfile } from '@/api/user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const refreshTokenValue = ref(localStorage.getItem('refreshToken') || '')
  const userId = ref(localStorage.getItem('userId') || '')
  const username = ref(localStorage.getItem('username') || '')
  const role = ref(localStorage.getItem('role') || 'USER')

  // 积分统一从 /user/profile 获取，不存储在登录响应中
  const reviewQuota = ref(parseInt(localStorage.getItem('reviewQuota'), 10) || 0)

  const isAuthenticated = computed(() => !!token.value)

  /**
   * 登录成功后调用：保存 Token + 拉取积分
   */
  async function login(credentials) {
    const res = await apiLogin(credentials)
    saveSession(res)
    await fetchProfile()
    return res
  }

  /**
   * 注册成功后调用：保存 Token + 拉取积分
   */
  async function register(data) {
    const res = await apiRegister(data)
    saveSession(res)
    await fetchProfile()
    return res
  }

  function saveSession(data) {
    token.value = data.token
    refreshTokenValue.value = data.refreshToken
    userId.value = data.userId
    username.value = data.username || ''
    role.value = data.role || 'USER'
    localStorage.setItem('token', data.token)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('username', data.username || '')
    localStorage.setItem('role', role.value)
  }

  function logout() {
    token.value = ''
    refreshTokenValue.value = ''
    userId.value = ''
    username.value = ''
    role.value = 'USER'
    reviewQuota.value = 0
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    localStorage.removeItem('reviewQuota')
  }

  /**
   * Token 刷新：仅更新 Token，积分不从此获取
   */
  async function refresh() {
    const res = await apiRefresh(refreshTokenValue.value)
    saveSession(res)
    return res
  }

  /**
   * 统一从 /user/profile 拉取积分（以及用户名等个人资料）
   * 建议在以下时机调用：
   *   - 登录/注册成功后
   *   - Token 刷新成功后（非必须，积分不常变）
   *   - 页面可见性恢复时（见 Layout.vue visibilitychange）
   */
  async function fetchProfile() {
    try {
      const res = await getProfile()
      const data = res.data?.data
      if (data) {
        reviewQuota.value = data.reviewQuota ?? 0
        localStorage.setItem('reviewQuota', String(reviewQuota.value))
        // 用户名可能已在别处修改，同步一下
        if (data.username) {
          username.value = data.username
          localStorage.setItem('username', data.username)
        }
      }
    } catch (e) {
      // 积分获取失败不影响主流程，保留上一次的缓存值
      console.warn('获取用户积分失败，使用缓存值', e)
    }
  }

  return {
    token, refreshTokenValue, userId, username, role,
    reviewQuota,
    isAuthenticated,
    login, register, logout, refresh, fetchProfile
  }
})
