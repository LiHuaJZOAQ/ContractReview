import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const { mockRouterPush } = vi.hoisted(() => ({
  mockRouterPush: vi.fn()
}))

vi.mock('@/router', () => ({ default: { push: mockRouterPush } }))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}))

import '@/api/interceptor'

const axios = (await import('axios')).default

describe('interceptor', () => {
  beforeEach(() => {
    localStorage.clear()
    mockRouterPush.mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('request interceptor adds Authorization header when token exists', () => {
    localStorage.setItem('token', 'test-token')
    const config = { headers: {} }
    axios.interceptors.request.handlers[0].fulfilled(config)
    expect(config.headers.Authorization).toBe('Bearer test-token')
  })

  it('request interceptor skips header when no token', () => {
    const config = { headers: {} }
    axios.interceptors.request.handlers[0].fulfilled(config)
    expect(config.headers.Authorization).toBeUndefined()
  })

  it('response interceptor extracts data.data on success', () => {
    const response = { data: { code: 0, data: { key: 'value' } } }
    const result = axios.interceptors.response.handlers[0].fulfilled(response)
    expect(result).toEqual({ key: 'value' })
  })

  it('response interceptor rejects on business error code', async () => {
    const response = { data: { code: 1003, message: '配额不足' } }
    try {
      await axios.interceptors.response.handlers[0].fulfilled(response)
      expect.fail('should have thrown')
    } catch (e) {
      expect(e.message).toBe('配额不足')
    }
  })
})
