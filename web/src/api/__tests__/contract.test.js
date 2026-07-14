import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockPost = vi.fn()
const mockGet = vi.fn()
vi.mock('@/api/interceptor', () => ({
  default: { post: mockPost, get: mockGet }
}))

const { uploadFile, submitTask, getStatus, getReport, getHistory, getPreviewText, getProcessLogs, retryTask } = await import('@/api/contract')

describe('contract API', () => {
  beforeEach(() => {
    mockPost.mockReset()
    mockGet.mockReset()
  })

  describe('uploadFile', () => {
    it('sends POST /contract/upload with FormData', async () => {
      const file = new File(['content'], 'test.pdf', { type: 'application/pdf' })
      mockPost.mockResolvedValue({ data: { data: { taskId: 1, previewText: 'text' } } })
      const result = await uploadFile(file, true)
      expect(mockPost).toHaveBeenCalledWith('/contract/upload', expect.any(FormData), {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      const formData = mockPost.mock.calls[0][1]
      expect(formData.get('file')).toBe(file)
      expect(formData.get('desensitize')).toBe('true')
      expect(result.data.data.taskId).toBe(1)
    })

    it('sends desensitize=false when specified', async () => {
      const file = new File(['content'], 'test.pdf', { type: 'application/pdf' })
      mockPost.mockResolvedValue({ data: { data: {} } })
      await uploadFile(file, false)
      const formData = mockPost.mock.calls[0][1]
      expect(formData.get('desensitize')).toBe('false')
    })
  })

  describe('submitTask', () => {
    it('sends POST /contract/{taskId}/submit', async () => {
      mockPost.mockResolvedValue({ data: { data: null } })
      await submitTask(42)
      expect(mockPost).toHaveBeenCalledWith('/contract/42/submit')
    })
  })

  describe('getStatus', () => {
    it('sends GET /contract/{taskId}/status', async () => {
      mockGet.mockResolvedValue({ data: { data: { status: 'PENDING', progress: 0 } } })
      await getStatus(7)
      expect(mockGet).toHaveBeenCalledWith('/contract/7/status')
    })
  })

  describe('getReport', () => {
    it('sends GET /contract/{taskId}/report', async () => {
      mockGet.mockResolvedValue({ data: { data: { summary: 'test' } } })
      await getReport(99)
      expect(mockGet).toHaveBeenCalledWith('/contract/99/report')
    })
  })

  describe('getHistory', () => {
    it('sends GET /contract/history without status filter', async () => {
      mockGet.mockResolvedValue({ data: { data: { tasks: [], total: 0 } } })
      await getHistory(1, 10, 'ALL')
      expect(mockGet).toHaveBeenCalledWith('/contract/history', { params: { page: 1, size: 10 } })
    })

    it('sends GET /contract/history/{status} with status filter', async () => {
      mockGet.mockResolvedValue({ data: { data: { tasks: [], total: 0 } } })
      await getHistory(2, 20, 'SUCCESS')
      expect(mockGet).toHaveBeenCalledWith('/contract/history/SUCCESS', { params: { page: 2, size: 20 } })
    })

    it('sends comma-separated statuses for PROCESSING filter', async () => {
      mockGet.mockResolvedValue({ data: { data: { tasks: [], total: 0 } } })
      await getHistory(1, 10, 'PENDING,PARSING,RETRIEVING,REVIEWING,SUMMARIZING')
      expect(mockGet).toHaveBeenCalledWith('/contract/history/PENDING%2CPARSING%2CRETRIEVING%2CREVIEWING%2CSUMMARIZING', { params: { page: 1, size: 10 } })
    })

    it('encodes special characters in status', async () => {
      mockGet.mockResolvedValue({ data: { data: { tasks: [], total: 0 } } })
      await getHistory(1, 10, 'PENDING,PARSING')
      expect(mockGet.mock.calls[0][0]).toContain('/contract/history/PENDING')
    })
  })

  describe('getPreviewText', () => {
    it('sends GET /contract/{taskId}/text', async () => {
      mockGet.mockResolvedValue({ data: { data: 'preview' } })
      await getPreviewText(5)
      expect(mockGet).toHaveBeenCalledWith('/contract/5/text')
    })
  })

  describe('getProcessLogs', () => {
    it('sends GET /contract/{taskId}/logs', async () => {
      mockGet.mockResolvedValue({ data: { data: [] } })
      await getProcessLogs(3)
      expect(mockGet).toHaveBeenCalledWith('/contract/3/logs')
    })
  })

  describe('retryTask', () => {
    it('sends POST /contract/{taskId}/retry', async () => {
      mockPost.mockResolvedValue({ data: { data: null } })
      await retryTask(8)
      expect(mockPost).toHaveBeenCalledWith('/contract/8/retry')
    })
  })
})
