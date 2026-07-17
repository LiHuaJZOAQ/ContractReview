import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

const EventSourceMock = vi.fn()
EventSourceMock.prototype.addEventListener = vi.fn()
EventSourceMock.prototype.close = vi.fn()

global.EventSource = EventSourceMock

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

    expect(wrapper.find('.sse-panel').exists()).toBe(true)
    expect(EventSource).toHaveBeenCalledWith('/api/v1/contract/1/progress?token=test-token')
  })

  it('shows 4 stages', async () => {
    wrapper.vm.open()
    await nextTick()

    const stageLabels = wrapper.findAll('.timeline-label').map(el => el.text())
    expect(stageLabels).toEqual(['解析文档', '检索法条', '审查条款', '汇总报告'])
  })

  it('progress event updates percentage and stage status', async () => {
    wrapper.vm.open()
    await nextTick()

    const progressCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'progress')[1]
    progressCb({ data: JSON.stringify({ status: 'PARSING', progress: 5, message: '解析中' }) })
    await nextTick()

    expect(wrapper.vm.percentage).toBe(5)
    expect(wrapper.vm.stages[0].status).toBe('active')
  })

  it('retrieving stage is marked active when retrieving progress', async () => {
    wrapper.vm.open()
    await nextTick()

    const progressCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'progress')[1]
    progressCb({ data: JSON.stringify({ status: 'RETRIEVING', progress: 20, message: '检索中' }) })
    await nextTick()

    expect(wrapper.vm.percentage).toBe(20)
    expect(wrapper.vm.stages[0].status).toBe('done')
    expect(wrapper.vm.stages[1].status).toBe('active')
  })

  it('marks previous stages as done and current as active', async () => {
    wrapper.vm.open()
    await nextTick()

    const progressCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'progress')[1]
    progressCb({ data: JSON.stringify({ status: 'SUMMARIZING', progress: 80 }) })
    await nextTick()

    expect(wrapper.vm.stages[0].status).toBe('done')
    expect(wrapper.vm.stages[1].status).toBe('done')
    expect(wrapper.vm.stages[2].status).toBe('done')
    expect(wrapper.vm.stages[3].status).toBe('active')
  })

  it('complete event sets 100% and emits complete', async () => {
    wrapper.vm.open()
    await nextTick()

    const completeCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'complete')[1]
    completeCb({ data: JSON.stringify({}) })
    await nextTick()

    expect(wrapper.vm.percentage).toBe(100)
    expect(wrapper.vm.stages.every(s => s.status === 'done')).toBe(true)
    expect(wrapper.emitted('complete')).toBeTruthy()
  })

  it('error event emits error', async () => {
    wrapper.vm.open()
    await nextTick()

    const progressCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'progress')[1]
    progressCb({ data: JSON.stringify({ status: 'REVIEWING', progress: 40 }) })
    await nextTick()

    const errorCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'error')[1]
    errorCb({ data: JSON.stringify({ message: 'LLM API error' }) })
    await nextTick()

    expect(wrapper.emitted('error')).toBeTruthy()
    expect(wrapper.emitted('error')[0]).toEqual(['LLM API error'])
  })

  it('llm_output event adds to outputs list', async () => {
    wrapper.vm.open()
    await nextTick()

    const llmCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'llm_output')[1]
    llmCb({ data: JSON.stringify({ agent: 'Agent-A', content: '分类结果' }) })
    await nextTick()

    expect(wrapper.vm.outputs.length).toBe(1)
    expect(wrapper.vm.outputs[0].agent).toBe('Agent-A')
    expect(wrapper.vm.outputs[0].content).toBe('分类结果')
  })

  it('close() cleans up EventSource and timer', async () => {
    wrapper.vm.open()
    await nextTick()

    wrapper.vm.close()
    expect(EventSourceMock.prototype.close).toHaveBeenCalled()
  })

  it('reset() restores initial state', async () => {
    wrapper.vm.open()
    await nextTick()

    const progressCb = EventSourceMock.prototype.addEventListener.mock.calls.find(c => c[0] === 'progress')[1]
    progressCb({ data: JSON.stringify({ status: 'SUMMARIZING', progress: 80 }) })
    await nextTick()

    wrapper.vm.reset()
    expect(wrapper.vm.percentage).toBe(0)
    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.vm.stages.every(s => s.status === 'pending')).toBe(true)
    expect(wrapper.vm.outputs.length).toBe(0)
  })
})
