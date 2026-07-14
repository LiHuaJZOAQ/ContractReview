import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockPost = vi.fn()
vi.mock('@/api/interceptor', () => ({
  default: { post: mockPost }
}))

const { login, register, refreshToken } = await import('@/api/auth')

describe('auth API', () => {
  beforeEach(() => {
    mockPost.mockReset()
  })

  it('login calls POST /auth/login with credentials', async () => {
    mockPost.mockResolvedValue({ data: { data: { token: 'abc' } } })
    const result = await login({ username: 'user', password: 'pass' })
    expect(mockPost).toHaveBeenCalledWith('/auth/login', { username: 'user', password: 'pass' })
    expect(result.data.data.token).toBe('abc')
  })

  it('register calls POST /auth/register with data', async () => {
    mockPost.mockResolvedValue({ data: { data: { token: 'xyz' } } })
    const result = await register({ username: 'newuser', password: 'newpass' })
    expect(mockPost).toHaveBeenCalledWith('/auth/register', { username: 'newuser', password: 'newpass' })
    expect(result.data.data.token).toBe('xyz')
  })

  it('refreshToken calls POST /auth/refresh', async () => {
    mockPost.mockResolvedValue({ data: { data: { token: 'refreshed' } } })
    const result = await refreshToken('my-refresh-token')
    expect(mockPost).toHaveBeenCalledWith('/auth/refresh', { refreshToken: 'my-refresh-token' })
    expect(result.data.data.token).toBe('refreshed')
  })
})
