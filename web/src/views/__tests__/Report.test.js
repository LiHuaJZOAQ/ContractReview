import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: { taskId: '99' } }),
}))

const mockGetReport = vi.fn()
const mockGetPreviewText = vi.fn()
const mockGetProcessLogs = vi.fn()
vi.mock('@/api/contract', () => ({
  getReport: (...args) => mockGetReport(...args),
  getPreviewText: (...args) => mockGetPreviewText(...args),
  getProcessLogs: (...args) => mockGetProcessLogs(...args),
}))

const Report = (await import('@/views/Report.vue')).default

describe('Report view', () => {
  beforeEach(() => {
    mockPush.mockReset()
    mockGetReport.mockReset()
    mockGetPreviewText.mockReset()
    mockGetProcessLogs.mockReset()
    mockGetPreviewText.mockRejectedValue(new Error(''))
    mockGetProcessLogs.mockRejectedValue(new Error(''))
  })

  it('renders loading state initially', () => {
    mockGetReport.mockImplementation(() => new Promise(() => {}))
    const wrapper = mount(Report, {
      global: {
        mocks: { $route: { params: { taskId: '99' }, query: {} } }
      }
    })
    expect(wrapper.find('.report-page').exists()).toBe(true)
  })

  it('loads and displays report data', async () => {
    mockGetReport.mockResolvedValue({
      summary: '合同存在风险',
      riskCount: { high: 2, medium: 1, low: 0 },
      risks: [
        { clauseIndex: 1, clauseContent: '条款1', riskLevel: 'HIGH', riskType: '免责', description: '不合理免责', suggestion: '删除', relatedLaws: ['民法典第506条'] }
      ],
      createdAt: '2024-01-01T00:00:00'
    })
    mockGetPreviewText.mockResolvedValue('合同原文内容')
    mockGetProcessLogs.mockResolvedValue([{ agent: 'Agent-A', content: '分类完成', createdAt: '2024-01-01' }])

    const wrapper = mount(Report, {
      global: {
        mocks: { $route: { params: { taskId: '99' }, query: {} } }
      }
    })
    await new Promise(r => setTimeout(r, 100))

    expect(wrapper.vm.report).not.toBeNull()
    expect(wrapper.vm.report.summary).toBe('合同存在风险')
    expect(wrapper.vm.previewText).toBe('合同原文内容')
    expect(wrapper.vm.logs.length).toBe(1)
  })

  it('riskLabel maps levels correctly', () => {
    mockGetReport.mockResolvedValue({ summary: '', riskCount: {}, risks: [] })
    const wrapper = mount(Report, {
      global: {
        mocks: { $route: { params: { taskId: '99' }, query: {} } }
      }
    })
    expect(wrapper.vm.riskLabel('HIGH')).toBe('高危')
    expect(wrapper.vm.riskLabel('MEDIUM')).toBe('中危')
    expect(wrapper.vm.riskLabel('LOW')).toBe('低危')
    expect(wrapper.vm.riskLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('has 3 tab buttons', async () => {
    mockGetReport.mockResolvedValue({ summary: '', riskCount: {}, risks: [] })
    const wrapper = mount(Report, {
      global: {
        mocks: { $route: { params: { taskId: '99' }, query: {} } }
      }
    })
    await new Promise(r => setTimeout(r, 50))
    const tabs = wrapper.findAll('.tab-btn')
    expect(tabs.length).toBe(3)
    const labels = tabs.map(t => t.text())
    expect(labels).toContain('审查报告')
    expect(labels).toContain('合同原文')
    expect(labels).toContain('审查过程')
  })
})
