import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, refreshToken as apiRefresh } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const refreshTokenValue = ref(localStorage.getItem('refreshToken') || '')
  const userId = ref(localStorage.getItem('userId') || '')

  const isAuthenticated = computed(() => !!token.value)

  async function login(credentials) {
    const res = await apiLogin(credentials)
    saveSession(res)
    return res
  }

  async function register(data) {
    const res = await apiRegister(data)
    saveSession(res)
    return res
  }

  function saveSession(data) {
    token.value = data.token
    refreshTokenValue.value = data.refreshToken
    userId.value = data.userId
    localStorage.setItem('token', data.token)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('userId', data.userId)
  }

  function logout() {
    token.value = ''
    refreshTokenValue.value = ''
    userId.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
  }

  async function refresh() {
    const res = await apiRefresh(refreshTokenValue.value)
    saveSession(res)
    return res
  }

  return { token, refreshTokenValue, userId, isAuthenticated, login, register, logout, refresh }
})
