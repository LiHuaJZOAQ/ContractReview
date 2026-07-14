import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: {} }),
}))

vi.mock('@element-plus/icons-vue', () => ({ User: {}, Lock: {} }))

const mockLogin = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    login: mockLogin,
    isAuthenticated: false,
  }))
}))

const Login = (await import('@/views/Login.vue')).default

describe('Login view', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockPush.mockReset()
    mockLogin.mockReset()
  })

  it('renders login form', () => {
    const wrapper = mount(Login)
    expect(wrapper.find('.login-container').exists()).toBe(true)
    expect(wrapper.text()).toContain('智能合同风险审查系统')
    expect(wrapper.text()).toContain('登录')
    expect(wrapper.text()).toContain('注册账号')
  })

  it('has username and password fields', () => {
    const wrapper = mount(Login)
    const inputs = wrapper.findAllComponents({ name: 'ElInputStub' })
    expect(inputs.length).toBeGreaterThanOrEqual(2)
  })

  it('calls login on button click after validation', async () => {
    const wrapper = mount(Login, {
      global: {
        mocks: {
          $router: { push: mockPush }
        }
      }
    })
    wrapper.vm.form.username = 'testuser'
    wrapper.vm.form.password = 'testpass'
    wrapper.vm.formRef = {
      validate: () => Promise.resolve(true)
    }
    await wrapper.vm.handleLogin()
    expect(mockLogin).toHaveBeenCalledWith({ username: 'testuser', password: 'testpass' })
  })

  it('does not call login when form validation fails', async () => {
    const wrapper = mount(Login)
    wrapper.vm.formRef = {
      validate: () => Promise.reject(new Error('invalid'))
    }
    await wrapper.vm.handleLogin()
    expect(mockLogin).not.toHaveBeenCalled()
  })

  it('shows register link', () => {
    const wrapper = mount(Login)
    expect(wrapper.text()).toContain('注册账号')
  })
})
