import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: {} }),
}))

vi.mock('@element-plus/icons-vue', () => ({ UploadFilled: { template: '<span />' } }))

vi.mock('@/components/SseProgress.vue', () => ({
  default: {
    name: 'SseProgress',
    props: { taskId: [Number, String] },
    template: '<div class="sse-progress-stub" />',
    methods: { open() {}, close() {} },
  }
}))

const mockUploadFile = vi.fn()
const mockSubmitTask = vi.fn()
vi.mock('@/api/contract', () => ({
  uploadFile: (...args) => mockUploadFile(...args),
  submitTask: (...args) => mockSubmitTask(...args),
}))

const Upload = (await import('@/views/Upload.vue')).default

describe('Upload view', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockPush.mockReset()
    mockUploadFile.mockReset()
    mockSubmitTask.mockReset()
  })

  it('renders upload page', () => {
    const wrapper = mount(Upload)
    expect(wrapper.text()).toContain('上传合同文件')
  })

  it('desensitize switch defaults to true', () => {
    const wrapper = mount(Upload)
    expect(wrapper.vm.desensitize).toBe(true)
  })

  it('handleUpload calls uploadFile API', async () => {
    mockUploadFile.mockResolvedValue({ taskId: 42, previewText: '合同预览文本' })
    const wrapper = mount(Upload)
    wrapper.vm.selectedFile = { name: 'test.pdf' }
    await wrapper.vm.handleUpload()
    expect(mockUploadFile).toHaveBeenCalled()
    expect(wrapper.vm.previewText).toBe('合同预览文本')
    expect(wrapper.vm.currentTaskId).toBe(42)
  })

  it('handleUpload does nothing without selected file', async () => {
    const wrapper = mount(Upload)
    wrapper.vm.selectedFile = null
    await wrapper.vm.handleUpload()
    expect(mockUploadFile).not.toHaveBeenCalled()
  })

  it('handleSubmit calls submitTask and opens SSE', async () => {
    mockSubmitTask.mockResolvedValue({})
    const wrapper = mount(Upload)
    wrapper.vm.currentTaskId = 42
    const spy = vi.spyOn(wrapper.vm.sseRef, 'open')
    await wrapper.vm.handleSubmit()
    expect(mockSubmitTask).toHaveBeenCalledWith(42)
    expect(spy).toHaveBeenCalled()
  })

  it('handleBack resets preview state', () => {
    const wrapper = mount(Upload)
    wrapper.vm.previewText = 'text'
    wrapper.vm.currentTaskId = 42
    wrapper.vm.handleBack()
    expect(wrapper.vm.previewText).toBe('')
    expect(wrapper.vm.currentTaskId).toBeNull()
  })
})
