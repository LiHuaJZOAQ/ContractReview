import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

function makeResponse(body, ok = true, status = 200) {
  return {
    ok,
    status,
    body: {
      getReader: () => ({
        read: body.read,
        cancel: vi.fn().mockResolvedValue(undefined)
      })
    }
  }
}

function createFetchMock(lines) {
  const read = vi.fn()
  const chunks = lines.length === 0
    ? [{ done: true, value: undefined }]
    : lines.map(line => ({ done: false, value: new TextEncoder().encode(line + '\n') })).concat([{ done: true, value: undefined }])
  chunks.forEach(c => read.mockResolvedValueOnce(c))
  return vi.fn().mockResolvedValue(makeResponse({ read }))
}

let mockFetch
const handlers = new Set()

beforeEach(() => {
  mockFetch = vi.fn().mockResolvedValue(makeResponse({ read: vi.fn().mockResolvedValue({ done: true }) }))
  global.fetch = mockFetch
  handlers.clear()
})

afterEach(() => {
  handlers.clear()
})

localStorage.setItem('token', 'test-token')

const SseProgress = (await import('@/components/SseProgress.vue')).default

describe('SseProgress', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    wrapper = mount(SseProgress, {
      props: { taskId: 1 }
    })
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('renders hidden initially', () => {
    expect(wrapper.find('.sse-panel').exists()).toBe(false)
  })

  it('opens SSE connection when open() is called', async () => {
    wrapper.vm.open()
    await nextTick()
    await nextTick()

    expect(wrapper.find('.sse-panel').exists()).toBe(true)
    expect(mockFetch).toHaveBeenCalledWith('/api/v1/contract/1/progress', expect.objectContaining({
      headers: { Authorization: 'Bearer test-token' }
    }))
  })

  it('shows 4 stages', async () => {
    wrapper.vm.open()
    await nextTick()
    await nextTick()

    const stageLabels = wrapper.findAll('.timeline-label').map(el => el.text())
    expect(stageLabels).toEqual(['解析文档', '检索法条', '审查条款', '汇总报告'])
  })

  it('progress message updates percentage and stage status', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'progress', status: 'parsing', progress: 5, message: '解析中' })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.vm.percentage).toBe(5)
    expect(wrapper.vm.stages[0].status).toBe('active')
  })

  it('retrieving stage is marked active when retrieving progress', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'progress', status: 'retrieving', progress: 20, message: '检索中' })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.vm.percentage).toBe(20)
    expect(wrapper.vm.stages[0].status).toBe('done')
    expect(wrapper.vm.stages[1].status).toBe('active')
  })

  it('marks previous stages as done and current as active', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'progress', status: 'summarizing', progress: 80 })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.vm.stages[0].status).toBe('done')
    expect(wrapper.vm.stages[1].status).toBe('done')
    expect(wrapper.vm.stages[2].status).toBe('done')
    expect(wrapper.vm.stages[3].status).toBe('active')
  })

  it('complete message sets 100% and emits complete', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'complete', status: 'completed', progress: 100 })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.vm.percentage).toBe(100)
    expect(wrapper.vm.stages.every(s => s.status === 'done')).toBe(true)
    expect(wrapper.emitted('complete')).toBeTruthy()
  })

  it('error message emits error', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'progress', status: 'reviewing', progress: 40 }),
      JSON.stringify({ type: 'error', message: 'LLM API error' })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.emitted('error')).toBeTruthy()
    expect(wrapper.emitted('error')[0]).toEqual(['LLM API error'])
  })

  it('llm_output message adds to outputs list', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'llm_output', agent: 'Agent-A', content: '分类结果' })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.vm.outputs.length).toBe(1)
    expect(wrapper.vm.outputs[0].agent).toBe('Agent-A')
    expect(wrapper.vm.outputs[0].content).toBe('分类结果')
  })

  it('401 emits error 登录已过期', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: false, status: 401 })

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    expect(wrapper.emitted('error')).toBeTruthy()
    expect(wrapper.emitted('error')[0]).toEqual(['登录已过期'])
  })

  it('close() aborts the stream', async () => {
    const abortSpy = vi.fn()
    global.fetch = vi.fn().mockImplementation((url, opts) => {
      opts.signal.addEventListener('abort', abortSpy)
      return Promise.resolve(makeResponse({ read: vi.fn().mockResolvedValue({ done: true }) }))
    })

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    wrapper.vm.close()

    expect(abortSpy).toHaveBeenCalled()
  })

  it('reset() restores initial state', async () => {
    mockFetch = createFetchMock([
      JSON.stringify({ type: 'progress', status: 'summarizing', progress: 80 })
    ])
    global.fetch = mockFetch

    wrapper.vm.open()
    await nextTick()
    await new Promise(r => setTimeout(r, 10))
    await nextTick()

    wrapper.vm.reset()
    expect(wrapper.vm.percentage).toBe(0)
    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.vm.stages.every(s => s.status === 'pending')).toBe(true)
    expect(wrapper.vm.outputs.length).toBe(0)
  })
})
