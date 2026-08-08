import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: {} }),
}))

const mockGetHistory = vi.fn()
const mockRetryTask = vi.fn()
vi.mock('@/api/contract', () => ({
  getHistory: (...args) => mockGetHistory(...args),
  retryTask: (...args) => mockRetryTask(...args),
}))

const History = (await import('@/views/History.vue')).default

describe('History view', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockPush.mockReset()
    mockGetHistory.mockReset()
    mockRetryTask.mockReset()
    mockGetHistory.mockResolvedValue({ tasks: [], total: 0 })
  })

  it('renders history page', () => {
    const wrapper = mount(History)
    expect(wrapper.text()).toContain('审查历史')
  })

  it('has 4 filter buttons', () => {
    const wrapper = mount(History)
    const buttons = wrapper.findAll('.segment-btn')
    expect(buttons.length).toBe(4)
    const labels = buttons.map(t => t.text())
    expect(labels).toContain('全部')
    expect(labels).toContain('进行中')
    expect(labels).toContain('已完成')
    expect(labels).toContain('失败')
  })

  it('fetchHistory on mount calls API', async () => {
    mockGetHistory.mockResolvedValue({ tasks: [{ taskId: 1, fileName: 'test.pdf', status: 'SUCCESS', progress: 100, createdAt: '2024-01-01' }], total: 1 })
    const wrapper = mount(History)
    await new Promise(r => setTimeout(r, 50))
    expect(mockGetHistory).toHaveBeenCalled()
  })

  it('handleRetry calls retryTask and refreshes', async () => {
    mockRetryTask.mockResolvedValue({})
    mockGetHistory.mockResolvedValue({ tasks: [], total: 0 })
    const wrapper = mount(History)
    await wrapper.vm.handleRetry({ taskId: 7 })
    expect(mockRetryTask).toHaveBeenCalledWith(7)
    expect(mockGetHistory).toHaveBeenCalled()
  })

  it('statusLabel maps all statuses', () => {
    const wrapper = mount(History)
    expect(wrapper.vm.statusLabel('PENDING')).toBe('待提交')
    expect(wrapper.vm.statusLabel('PARSING')).toBe('解析中')
    expect(wrapper.vm.statusLabel('RETRIEVING')).toBe('检索中')
    expect(wrapper.vm.statusLabel('REVIEWING')).toBe('审查中')
    expect(wrapper.vm.statusLabel('SUMMARIZING')).toBe('汇总中')
    expect(wrapper.vm.statusLabel('SUCCESS')).toBe('已完成')
    expect(wrapper.vm.statusLabel('FAILED')).toBe('失败')
    expect(wrapper.vm.statusLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('statusClass returns correct classes', () => {
    const wrapper = mount(History)
    expect(wrapper.vm.statusClass('SUCCESS')).toBe('success')
    expect(wrapper.vm.statusClass('FAILED')).toBe('danger')
    expect(wrapper.vm.statusClass('PENDING')).toBe('pending')
    expect(wrapper.vm.statusClass('PARSING')).toBe('processing')
  })

  it('isProcessing returns true for in-progress statuses', () => {
    const wrapper = mount(History)
    expect(wrapper.vm.isProcessing('PENDING')).toBe(true)
    expect(wrapper.vm.isProcessing('PARSING')).toBe(true)
    expect(wrapper.vm.isProcessing('RETRIEVING')).toBe(true)
    expect(wrapper.vm.isProcessing('REVIEWING')).toBe(true)
    expect(wrapper.vm.isProcessing('SUMMARIZING')).toBe(true)
    expect(wrapper.vm.isProcessing('SUCCESS')).toBe(false)
    expect(wrapper.vm.isProcessing('FAILED')).toBe(false)
  })

  it('STATUS_FILTER_MAP correctly maps PROCESSING to comma-separated statuses', () => {
    const wrapper = mount(History)
    const map = wrapper.vm.STATUS_FILTER_MAP
    expect(map.ALL).toBe('ALL')
    expect(map.PROCESSING).toBe('PENDING,PARSING,RETRIEVING,REVIEWING,SUMMARIZING')
    expect(map.SUCCESS).toBe('SUCCESS')
    expect(map.FAILED).toBe('FAILED')
  })
})
