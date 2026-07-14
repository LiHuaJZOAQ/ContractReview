import axios from './interceptor'

export function uploadFile(file, desensitize = true) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('desensitize', desensitize)
  return axios.post('/contract/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function submitTask(taskId) {
  return axios.post(`/contract/${taskId}/submit`)
}

export function getStatus(taskId) {
  return axios.get(`/contract/${taskId}/status`)
}

export function getReport(taskId) {
  return axios.get(`/contract/${taskId}/report`)
}

export function getHistory(page = 1, size = 10, status) {
  const params = { page, size }
  if (status && status !== 'ALL') params.status = status
  return axios.get('/contract/history', { params })
}

export function getPreviewText(taskId) {
  return axios.get(`/contract/${taskId}/text`)
}

export function getProcessLogs(taskId) {
  return axios.get(`/contract/${taskId}/logs`)
}

export function retryTask(taskId) {
  return axios.post(`/contract/${taskId}/retry`)
}
