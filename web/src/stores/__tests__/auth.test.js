import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

const mockApiLogin = vi.fn()
const mockApiRegister = vi.fn()
const mockApiRefresh = vi.fn()

vi.mock('@/api/auth', () => ({
  login: (...args) => mockApiLogin(...args),
  register: (...args) => mockApiRegister(...args),
  refreshToken: (...args) => mockApiRefresh(...args),
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockApiLogin.mockReset()
    mockApiRegister.mockReset()
    mockApiRefresh.mockReset()
  })

  const mockSession = { token: 'abc', refreshToken: 'xyz', userId: '42' }

  it('starts unauthenticated when no token in localStorage', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.token).toBe('')
    expect(store.userId).toBe('')
  })

  it('restores session from localStorage', () => {
    localStorage.setItem('token', 'existing-token')
    localStorage.setItem('refreshToken', 'existing-refresh')
    localStorage.setItem('userId', '100')
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(true)
    expect(store.token).toBe('existing-token')
  })

  it('login saves session and returns data', async () => {
    mockApiLogin.mockResolvedValue(mockSession)
    const store = useAuthStore()
    const result = await store.login({ username: 'u', password: 'p' })
    expect(mockApiLogin).toHaveBeenCalledWith({ username: 'u', password: 'p' })
    expect(store.token).toBe('abc')
    expect(store.refreshTokenValue).toBe('xyz')
    expect(store.userId).toBe('42')
    expect(localStorage.getItem('token')).toBe('abc')
    expect(localStorage.getItem('refreshToken')).toBe('xyz')
    expect(localStorage.getItem('userId')).toBe('42')
    expect(result.token).toBe('abc')
  })

  it('register saves session and returns data', async () => {
    mockApiRegister.mockResolvedValue(mockSession)
    const store = useAuthStore()
    const result = await store.register({ username: 'new', password: 'pw' })
    expect(mockApiRegister).toHaveBeenCalledWith({ username: 'new', password: 'pw' })
    expect(store.token).toBe('abc')
    expect(result.token).toBe('abc')
  })

  it('logout clears all session data', () => {
    localStorage.setItem('token', 't')
    localStorage.setItem('refreshToken', 'rt')
    localStorage.setItem('userId', '1')
    const store = useAuthStore()
    store.logout()
    expect(store.token).toBe('')
    expect(store.refreshTokenValue).toBe('')
    expect(store.userId).toBe('')
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(localStorage.getItem('userId')).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('refresh calls API and saves new session', async () => {
    mockApiRefresh.mockResolvedValue({ token: 'new-token', refreshToken: 'new-refresh', userId: '42' })
    const store = useAuthStore()
    store.refreshTokenValue = 'old-refresh'
    await store.refresh()
    expect(mockApiRefresh).toHaveBeenCalledWith('old-refresh')
    expect(store.token).toBe('new-token')
    expect(localStorage.getItem('token')).toBe('new-token')
  })
})
